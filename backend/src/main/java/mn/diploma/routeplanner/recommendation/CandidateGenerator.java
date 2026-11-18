package mn.diploma.routeplanner.recommendation;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.place.OpeningHours;
import mn.diploma.routeplanner.routing.TravelTimeEstimator;

/**
 * Stage 1 of the recommender: rule-based candidate filtering (design 3.2, Recommendation module).
 * Keeps recommendable places within the search radius that the user has not hidden, fit the budget,
 * suit the time of day, are open long enough and can be reached in a reasonable share of the available time.
 */
@Component
public class CandidateGenerator {

    static final int MAX_CANDIDATES = 600;
    /** Minimum minutes a place must stay open after arrival. */
    static final int MIN_OPEN_MINUTES = 45;
    /** Weight assumed for categories without a stored interest weight. */
    static final double DEFAULT_WEIGHT = 0.2;
    /** Weight used for every category when the INTERESTS consent is missing. */
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
                ctx.lng(), ctx.lat(), ctx.radiusM(), ctx.userId(), ctx.budgetLevel().name(), MAX_CANDIDATES)
                .stream()
                .filter(row -> OpeningHours.possiblyOpenFor((String) row[1], ctx.at(), MIN_OPEN_MINUTES))
                .map(row -> (Candidate) row[0])
                .toList();

        // One-way travel should take at most a third of the available time (the rest is for the visit itself)
        int maxTravel = Math.max(15, ctx.availableMinutes() / 3);
        return rows.stream()
                .peek(c -> c.travelMinutes = travelTimes.minutesBetween(ctx.lat(), ctx.lng(), c.lat, c.lng,
                        ctx.transportMode()))
                .filter(c -> c.travelMinutes <= maxTravel)
                .toList();
    }
}
