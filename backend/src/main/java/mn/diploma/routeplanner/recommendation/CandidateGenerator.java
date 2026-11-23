package mn.diploma.routeplanner.recommendation;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.place.OpeningHours;
import mn.diploma.routeplanner.routing.TravelTimeEstimator;

/**
 * Санал болголтын 1-р шат: нэр дэвшигч газрыг дүрмээр шүүх (зохиомж 3.2, Санал болголтын модуль).
 * Хайлтын радиус доторх, хэрэглэгчийн нуугаагүй, төсөвт багтах, цагийн тохирцтой, хангалттай удаан нээлттэй,
 * боломжит хугацааны зохих хэсэгт хүрч болох санал болгож болох газруудыг үлдээнэ.
 */
@Component
public class CandidateGenerator {

    static final int MAX_CANDIDATES = 600;
    /** Очсоноос хойш газар нээлттэй байх хамгийн бага хугацаа, минутаар. */
    static final int MIN_OPEN_MINUTES = 45;
    /** Сонирхлын жин хадгалагдаагүй ангилалд өгөх жин. */
    static final double DEFAULT_WEIGHT = 0.2;
    /** INTERESTS зөвшөөрөл байхгүй үед бүх ангилалд өгөх жин. */
    static final double NEUTRAL_WEIGHT = 0.5;

    private static final String SQL = """
            SELECT p.id, p.name_cyrillic, p.name_latin, c.id AS category_id, c.code, c.name_mn, m.code AS main_code,
                   ST_Y(p.location) AS lat, ST_X(p.location) AS lng,
                   ST_Distance(p.location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) AS distance_m,
                   p.opening_hours_raw,
                   coalesce(w.weight, ?) AS weight,
                   coalesce(t.suitability, 0.5) AS prior
            FROM places p
            JOIN place_categories c ON c.id = p.category_id
            JOIN place_categories m ON m.id = c.parent_id
            LEFT JOIN interest_weights w ON w.user_id = ? AND w.category_id = c.id
            LEFT JOIN category_time_priors t ON t.category_id = c.id AND t.time_of_day = ? AND t.day_type = ?
            WHERE p.is_active
              AND c.is_recommendable
              AND ST_DWithin(p.location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
              AND NOT EXISTS (SELECT 1 FROM place_marks pm
                              WHERE pm.user_id = ? AND pm.place_id = p.id AND pm.type = 'HIDDEN')
              AND (p.price_level IS NULL
                   OR array_position(ARRAY['FREE', 'LOW', 'MEDIUM', 'HIGH'], p.price_level::text)
                      <= array_position(ARRAY['FREE', 'LOW', 'MEDIUM', 'HIGH'], ?::text))
              AND coalesce(t.suitability, 0.5) > 0
              AND (cardinality(?::text[]) = 0 OR m.code = ANY (?::text[]) OR c.code = ANY (?::text[]))
            ORDER BY distance_m
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final TravelTimeEstimator travelTimes;

    public CandidateGenerator(JdbcTemplate jdbc, TravelTimeEstimator travelTimes) {
        this.jdbc = jdbc;
        this.travelTimes = travelTimes;
    }

    public List<Candidate> generate(RecommendationContext ctx) {
        double defaultWeight = ctx.useInterests() ? DEFAULT_WEIGHT : NEUTRAL_WEIGHT;
        long weightsUserId = ctx.useInterests() ? ctx.userId() : -1L;
        String[] categories = ctx.categories().toArray(String[]::new);
        List<Candidate> rows = jdbc.query(SQL, (rs, i) -> {
                    Candidate c = new Candidate(rs.getLong("id"), rs.getString("name_cyrillic"),
                            rs.getString("name_latin"), rs.getLong("category_id"), rs.getString("code"),
                            rs.getString("name_mn"), rs.getString("main_code"), rs.getDouble("lat"),
                            rs.getDouble("lng"), rs.getDouble("distance_m"), rs.getDouble("weight"),
                            rs.getDouble("prior"));
                    return new Object[] {c, rs.getString("opening_hours_raw")};
                },
                ctx.lng(), ctx.lat(), defaultWeight, weightsUserId,
                TimeOfDay.of(ctx.at()).name(), TimeOfDay.dayType(ctx.at()),
                ctx.lng(), ctx.lat(), ctx.radiusM(), ctx.userId(), ctx.budgetLevel().name(),
                categories, categories, categories, MAX_CANDIDATES)
                .stream()
                .filter(row -> OpeningHours.possiblyOpenFor((String) row[1], ctx.at(), MIN_OPEN_MINUTES))
                .map(row -> (Candidate) row[0])
                .toList();

        // Нэг талын зорчих хугацаа боломжит хугацааны гуравны нэгээс хэтрэхгүй (үлдсэн нь зочлолд)
        int maxTravel = Math.max(15, ctx.availableMinutes() / 3);
        return rows.stream()
                .peek(c -> c.travelMinutes = travelTimes.minutesBetween(ctx.lat(), ctx.lng(), c.lat, c.lng,
                        ctx.transportMode()))
                .filter(c -> c.travelMinutes <= maxTravel)
                .toList();
    }
}
