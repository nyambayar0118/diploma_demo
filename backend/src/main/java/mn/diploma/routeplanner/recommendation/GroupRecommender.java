package mn.diploma.routeplanner.recommendation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;
import mn.diploma.routeplanner.place.OpeningHours;
import mn.diploma.routeplanner.routing.TravelTimeEstimator;

/**
 * Бүлгийн уулзах газар (АШ10). INTERESTS зөвшөөрөлтэй гишүүн бүр газарт өөрийн сонирхлын жингээр оноо өгч,
 * оноонуудыг нэгтгэнэ ({@link GroupAggregator}); гишүүдийн зорчих хугацааны зөрүүнд ногдуулах шударга байдлын
 * торгууль газрыг бүгдэд хүртээмжтэй байлгана. Багцыг бүлгийн id-тай бүртгэх бөгөөд ганц хэрэглэгчийн загварыг
 * сургахад ашиглахгүй.
 */
@Service
public class GroupRecommender {

    static final double W_INTEREST = 0.75;
    static final double W_TIME = 0.25;
    /** (хамгийн урт - хамгийн богино зорчих хугацаа) / зөвшөөрөх дээд хугацаанд ногдох торгууль. */
    static final double FAIRNESS_WEIGHT = 0.30;
    /** Дундаж зорчих хугацаа / зөвшөөрөх дээд хугацаанд ногдох торгууль (төвд ойр газрыг илүүд үзнэ). */
    static final double TRAVEL_WEIGHT = 0.15;
    static final int MAX_PER_CATEGORY = 2;

    /** Санал болгогчийн харах гишүүн; зочны хувьд userId нь null. */
    public record MemberInput(long memberId, Long userId, boolean usesInterests, double lat, double lng,
                              TransportMode mode) {
    }

    /** {@code again}: энэ бүлэгт өмнө санал болгосон газрууд хараахан хараагүй газруудын ард орно. */
    public record Request(long groupSessionId, long initiatorId, List<MemberInput> members, int availableMinutes,
                          PriceLevel budgetLevel, LocalDateTime at, int k, boolean again) {
    }

    public record Option(long recommendationId, long placeId, String name, String nameLatin, String category,
                         String categoryName, String mainCategory, double lat, double lng, double score,
                         Map<Long, Integer> travelMinutesByMember, int maxTravelMinutes) {
    }

    public record Result(long batchId, String aggregator, List<Option> options) {
    }

    private record Place(long id, String name, String nameLatin, long categoryId, String code, String categoryName,
                         String mainCode, double lat, double lng, double prior) {
    }

    private final JdbcTemplate jdbc;
    private final TravelTimeEstimator travelTimes;
    private final GroupAggregator aggregator;

    public GroupRecommender(JdbcTemplate jdbc, TravelTimeEstimator travelTimes,
                            @Value("${app.group.aggregator:LEAST_MISERY}") GroupAggregator.Strategy aggregator) {
        this.jdbc = jdbc;
        this.travelTimes = travelTimes;
        this.aggregator = aggregator;
    }

    @Transactional
    public Result recommend(Request req) {
        List<MemberInput> members = req.members();
        double cLat = members.stream().mapToDouble(MemberInput::lat).average().orElseThrow();
        double cLng = members.stream().mapToDouble(MemberInput::lng).average().orElseThrow();
        double spread = members.stream()
                .mapToDouble(m -> TravelTimeEstimator.haversineMeters(cLat, cLng, m.lat(), m.lng())).max().orElse(0);
        double radius = Math.min(15_000, spread + 3_000);
        List<Long> userIds = members.stream().map(MemberInput::userId).filter(id -> id != null).toList();

        List<Object[]> rows = jdbc.query("""
                SELECT p.id, p.name_cyrillic, p.name_latin, c.id AS category_id, c.code, c.name_mn, m.code AS main_code,
                       ST_Y(p.location) AS lat, ST_X(p.location) AS lng, p.opening_hours_raw,
                       coalesce(t.suitability, 0.5) AS prior
                FROM places p
                JOIN place_categories c ON c.id = p.category_id
                JOIN place_categories m ON m.id = c.parent_id
                LEFT JOIN category_time_priors t ON t.category_id = c.id AND t.time_of_day = ? AND t.day_type = ?
                WHERE p.is_active AND c.is_recommendable
                  AND ST_DWithin(p.location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                  AND NOT EXISTS (SELECT 1 FROM place_marks pm
                                  WHERE pm.place_id = p.id AND pm.type = 'HIDDEN' AND pm.user_id = ANY (?))
                  AND (p.price_level IS NULL
                       OR array_position(ARRAY['FREE', 'LOW', 'MEDIUM', 'HIGH'], p.price_level::text)
                          <= array_position(ARRAY['FREE', 'LOW', 'MEDIUM', 'HIGH'], ?::text))
                  AND coalesce(t.suitability, 0.5) > 0
                ORDER BY p.location <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)
                LIMIT 800
                """, (rs, i) -> new Object[] {
                        new Place(rs.getLong("id"), rs.getString("name_cyrillic"), rs.getString("name_latin"),
                                rs.getLong("category_id"), rs.getString("code"), rs.getString("name_mn"),
                                rs.getString("main_code"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getDouble("prior")),
                        rs.getString("opening_hours_raw")},
                TimeOfDay.of(req.at()).name(), TimeOfDay.dayType(req.at()), cLng, cLat, radius,
                userIds.toArray(Long[]::new), req.budgetLevel().name(), cLng, cLat);

        Map<Long, Map<Long, Double>> weights = weights(members);
        int maxTravel = Math.max(20, req.availableMinutes() / 3);

        List<Option> scored = new ArrayList<>();
        for (Object[] row : rows) {
            Place p = (Place) row[0];
            if (!OpeningHours.possiblyOpenFor((String) row[1], req.at(), CandidateGenerator.MIN_OPEN_MINUTES)) continue;
            Map<Long, Integer> travel = new HashMap<>();
            for (MemberInput m : members) {
                travel.put(m.memberId(), travelTimes.minutesBetween(m.lat(), m.lng(), p.lat(), p.lng(), m.mode()));
            }
            int max = travel.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = travel.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            if (max > maxTravel) continue; // хэн нэгэн цагтаа хүрэхгүй
            double mean = travel.values().stream().mapToInt(Integer::intValue).average().orElse(0);

            List<Double> memberScores = new ArrayList<>();
            for (MemberInput m : members) {
                if (!m.usesInterests() || m.userId() == null) continue;
                double w = weights.getOrDefault(m.userId(), Map.of()).getOrDefault(p.categoryId(),
                        CandidateGenerator.DEFAULT_WEIGHT);
                memberScores.add(W_INTEREST * w + W_TIME * p.prior());
            }
            double group = memberScores.isEmpty()
                    ? W_INTEREST * CandidateGenerator.NEUTRAL_WEIGHT + W_TIME * p.prior()
                    : aggregator.aggregate(memberScores);
            double score = group - FAIRNESS_WEIGHT * (max - min) / (double) maxTravel
                    - TRAVEL_WEIGHT * mean / maxTravel;
            scored.add(new Option(0, p.id(), p.name(), p.nameLatin(), p.code(), p.categoryName(), p.mainCode(),
                    p.lat(), p.lng(), Math.round(score * 1000) / 1000.0, travel, max));
        }
        scored.sort(Comparator.comparingDouble(Option::score).reversed());

        // "Дахин": энэ бүлэгт өмнө санал болгосон газрыг зөвхөн шинэ газар дуусах үед ашиглана
        Set<Long> shown = req.again() ? shownBefore(req.groupSessionId()) : Set.of();
        List<Option> chosen = new ArrayList<>();
        Map<String, Integer> perCategory = new HashMap<>();
        pick(scored.stream().filter(o -> !shown.contains(o.placeId())).toList(), chosen, perCategory, req.k());
        if (chosen.size() < req.k()) {
            pick(scored.stream().filter(o -> shown.contains(o.placeId())).toList(), chosen, perCategory, req.k());
        }
        return log(req, chosen);
    }

    private static void pick(List<Option> ranked, List<Option> chosen, Map<String, Integer> perCategory, int k) {
        for (Option o : ranked) {
            if (chosen.size() >= k) return;
            if (perCategory.getOrDefault(o.category(), 0) >= MAX_PER_CATEGORY) continue;
            perCategory.merge(o.category(), 1, Integer::sum);
            chosen.add(o);
        }
    }

    private Set<Long> shownBefore(long groupSessionId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT r.place_id FROM recommendations r
                JOIN recommendation_batches b ON b.id = r.batch_id
                WHERE b.group_session_id = ?
                """, Long.class, groupSessionId));
    }

    private Map<Long, Map<Long, Double>> weights(List<MemberInput> members) {
        List<Long> ids = members.stream().filter(m -> m.usesInterests() && m.userId() != null)
                .map(MemberInput::userId).toList();
        Map<Long, Map<Long, Double>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.query("SELECT user_id, category_id, weight FROM interest_weights WHERE user_id = ANY (?)",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result
                        .computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getLong(2), rs.getDouble(3)),
                (Object) ids.toArray(Long[]::new));
        return result;
    }

    private Result log(Request req, List<Option> chosen) {
        Long batchId = jdbc.queryForObject("""
                INSERT INTO recommendation_batches (user_id, group_session_id, available_minutes, budget_level,
                                                    transport_mode, ranker_name)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, req.initiatorId(), req.groupSessionId(), req.availableMinutes(),
                req.budgetLevel().name(), req.members().getFirst().mode().name(), "GROUP_" + aggregator.name());
        List<Option> withIds = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Option o = chosen.get(i);
            Long recId = jdbc.queryForObject("""
                    INSERT INTO recommendations (batch_id, place_id, score, rank, is_exploration)
                    VALUES (?, ?, ?, ?, FALSE) RETURNING id
                    """, Long.class, batchId, o.placeId(), o.score(), i + 1);
            withIds.add(new Option(recId, o.placeId(), o.name(), o.nameLatin(), o.category(), o.categoryName(),
                    o.mainCategory(), o.lat(), o.lng(), o.score(), o.travelMinutesByMember(), o.maxTravelMinutes()));
        }
        return new Result(batchId, aggregator.name(), withIds);
    }
}
