package mn.diploma.routeplanner.recommendation;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.AuthDtos.PreferencesDto;
import mn.diploma.routeplanner.auth.AuthService;
import mn.diploma.routeplanner.auth.ConsentDataType;
import mn.diploma.routeplanner.auth.ConsentService;
import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Public service of the Recommendation module (АШ09, ДШ04): filter -> rank -> explore -> log. */
@Service
public class RecommendationService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ulaanbaatar");

    public record Request(double lat, double lng, Integer availableMinutes, PriceLevel budgetLevel,
                          TransportMode transportMode, Integer k, LocalDateTime at) {
    }

    public record Item(long recommendationId, int rank, long placeId, String name, String nameLatin,
                       String category, String categoryName, String mainCategory, double lat, double lng,
                       long distanceM, int travelMinutes, double score, boolean exploration, String reason) {
    }

    public record Batch(long batchId, String ranker, LocalDateTime at, int availableMinutes,
                        TransportMode transportMode, List<Item> items) {
    }

    private final CandidateGenerator candidates;
    private final Ranker ranker;
    private final ExplorationPolicy exploration;
    private final AuthService auth;
    private final ConsentService consents;
    private final JdbcTemplate jdbc;

    public RecommendationService(CandidateGenerator candidates, Ranker ranker, ExplorationPolicy exploration,
                                 AuthService auth, ConsentService consents, JdbcTemplate jdbc) {
        this.candidates = candidates;
        this.ranker = ranker;
        this.exploration = exploration;
        this.auth = auth;
        this.consents = consents;
        this.jdbc = jdbc;
    }

    @Transactional
    public Batch recommend(Long userId, Request req) {
        RecommendationContext ctx = context(userId, req);
        List<Candidate> found = candidates.generate(ctx);
        ranker.score(found, ctx);
        List<Candidate> chosen = exploration.select(found, ctx.k());
        long batchId = logBatch(ctx, chosen);

        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM recommendations WHERE batch_id = ? ORDER BY rank", Long.class, batchId);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Candidate c = chosen.get(i);
            items.add(new Item(ids.get(i), i + 1, c.placeId, c.name, c.nameLatin, c.category, c.categoryName,
                    c.mainCategory, c.lat, c.lng, Math.round(c.distanceM), c.travelMinutes,
                    Math.round(c.score * 1000) / 1000.0, c.exploration, reason(c)));
        }
        return new Batch(batchId, ranker.name(), ctx.at(), ctx.availableMinutes(), ctx.transportMode(), items);
    }

    private RecommendationContext context(Long userId, Request req) {
        PreferencesDto prefs = auth.preferences(userId);
        TransportMode mode = req.transportMode() != null ? req.transportMode()
                : prefs.transportModes().stream().sorted().findFirst().orElse(TransportMode.WALKING);
        int minutes = req.availableMinutes() != null ? req.availableMinutes() : prefs.timeBudgetMin();
        PriceLevel budget = req.budgetLevel() != null ? req.budgetLevel() : prefs.budgetLevel();
        LocalDateTime at = req.at() != null ? req.at() : LocalDateTime.now(ZONE);
        int k = req.k() != null ? Math.clamp(req.k(), 1, 30) : 10;
        return new RecommendationContext(userId, req.lat(), req.lng(), at, minutes, budget, mode,
                prefs.searchRadiusM(), k,
                consents.isGranted(userId, ConsentDataType.INTERESTS),
                consents.isGranted(userId, ConsentDataType.LOCATION));
    }

    private long logBatch(RecommendationContext ctx, List<Candidate> chosen) {
        Long batchId = jdbc.queryForObject("""
                INSERT INTO recommendation_batches (user_id, user_location, available_minutes, budget_level,
                                                    transport_mode, ranker_name, model_version)
                VALUES (?, CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(?, ?), 4326) END, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, ctx.userId(), ctx.storeLocation(), ctx.lng(), ctx.lat(), ctx.availableMinutes(),
                ctx.budgetLevel().name(), ctx.transportMode().name(), ranker.name(), ranker.modelVersion());
        for (int i = 0; i < chosen.size(); i++) {
            Candidate c = chosen.get(i);
            jdbc.update("INSERT INTO recommendations (batch_id, place_id, score, rank, is_exploration) VALUES (?, ?, ?, ?, ?)",
                    batchId, c.placeId, c.score, i + 1, c.exploration);
        }
        return batchId;
    }

    private static String reason(Candidate c) {
        if (c.exploration) return "Шинэ зүйл туршаад үзээрэй";
        if (c.interestWeight >= ExplorationPolicy.LOW_INTEREST) return "Таны сонирхол: " + c.categoryName;
        if (c.timePrior >= 0.9) return "Энэ цагт тохиромжтой";
        return "Ойрхон";
    }
}
