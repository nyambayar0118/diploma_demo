package mn.diploma.routeplanner.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Monitoring module (СНШ05, СНШ07): component health checked every minute (kept 24 h for availability, СГШ06),
 * request metrics, and usage numbers of the trial for the admin dashboard.
 */
@Service
public class MonitoringService {

    public record ComponentStatus(String name, boolean up, Long latencyMs, String detail) {
    }

    public record HealthReport(boolean up, Instant checkedAt, List<ComponentStatus> components,
                               double availability24h, int checks24h, Instant startedAt) {
    }

    private static final int HISTORY = 24 * 60;

    private final JdbcTemplate jdbc;
    private final RestClient ml;
    private final RequestMetrics metrics;
    private final Deque<Boolean> history = new ArrayDeque<>();
    private volatile HealthReport last;

    public MonitoringService(JdbcTemplate jdbc, RequestMetrics metrics, @Value("${app.ml.base-url}") String mlBaseUrl) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(800));
        this.ml = RestClient.builder().baseUrl(mlBaseUrl).requestFactory(factory).build();
    }

    /** Checks every component now. */
    public HealthReport check() {
        ComponentStatus db = timed("database", () -> {
            String v = jdbc.queryForObject("SELECT 'PostGIS ' || postgis_lib_version()", String.class);
            jdbc.queryForObject("SELECT count(*) FROM places WHERE is_active", Long.class);
            return v;
        });
        ComponentStatus mlStatus = timed("ml", () -> {
            ml.get().uri("/health").retrieve().toBodilessEntity();
            return "FastAPI";
        });
        ComponentStatus api = new ComponentStatus("backend", true, 0L, "Spring Boot");
        List<ComponentStatus> all = List.of(api, db, mlStatus);
        // The ML service is optional (rule-based fallback), so only the database decides "up"
        boolean up = db.up();
        synchronized (history) {
            history.addLast(up);
            while (history.size() > HISTORY) history.pollFirst();
        }
        last = new HealthReport(up, Instant.now(), all, availability(), history.size(), metrics.startedAt());
        return last;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    void scheduledCheck() {
        check();
    }

    public HealthReport lastOrCheck() {
        HealthReport r = last;
        return r != null && r.checkedAt().isAfter(Instant.now().minusSeconds(90)) ? r : check();
    }

    private double availability() {
        synchronized (history) {
            if (history.isEmpty()) return 1.0;
            long ok = history.stream().filter(Boolean::booleanValue).count();
            return Math.round(1000.0 * ok / history.size()) / 1000.0;
        }
    }

    /** Trial usage for the dashboard: users, activity, recommendations, routes, visits, satisfaction. */
    public Map<String, Object> usage() {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("users", jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE deleted_at IS NULL) AS total,
                       count(*) FILTER (WHERE deleted_at IS NULL AND created_at > now() - INTERVAL '7 days') AS new_7d,
                       count(*) FILTER (WHERE deleted_at IS NULL AND auth_provider = 'GOOGLE') AS google
                FROM users
                """));
        u.put("activeUsers", jdbc.queryForMap("""
                SELECT count(DISTINCT user_id) FILTER (WHERE timestamp > now() - INTERVAL '1 day') AS day,
                       count(DISTINCT user_id) FILTER (WHERE timestamp > now() - INTERVAL '7 days') AS week
                FROM user_interactions
                """));
        u.put("interactions24h", jdbc.queryForList("""
                SELECT type, count(*) AS n FROM user_interactions WHERE timestamp > now() - INTERVAL '1 day'
                GROUP BY type ORDER BY n DESC
                """));
        u.put("interactionsTotal", jdbc.queryForList(
                "SELECT type, count(*) AS n FROM user_interactions GROUP BY type ORDER BY n DESC"));
        u.put("recommendations", jdbc.queryForMap("""
                SELECT count(*) AS batches, count(*) FILTER (WHERE requested_at > now() - INTERVAL '1 day') AS batches_24h,
                       count(*) FILTER (WHERE group_session_id IS NULL) AS training_batches
                FROM recommendation_batches
                """));
        u.put("routes", jdbc.queryForList("SELECT status, count(*) AS n FROM routes GROUP BY status ORDER BY status"));
        u.put("visits", jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE is_visited) AS confirmed, count(*) FILTER (WHERE skipped_at IS NOT NULL) AS skipped,
                       count(*) AS stops
                FROM route_stops s JOIN routes r ON r.id = s.route_id WHERE r.status <> 'CREATED'
                """));
        u.put("satisfaction", jdbc.queryForMap("""
                SELECT count(*) AS n, round(avg(relevance), 2) AS relevance, round(avg(time_suitability), 2) AS time_suitability,
                       round(avg(estimate_accuracy), 2) AS estimate_accuracy
                FROM satisfaction_feedbacks
                """));
        u.put("groups", jdbc.queryForList("SELECT status, count(*) AS n FROM group_sessions GROUP BY status ORDER BY status"));
        u.put("moderation", jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status = 'PENDING') AS pending, count(*) FILTER (WHERE status = 'APPROVED') AS approved,
                       count(*) FILTER (WHERE status = 'REJECTED') AS rejected,
                       (SELECT count(*) FROM reviews) AS reviews, (SELECT count(*) FROM reviews WHERE is_hidden) AS hidden_reviews
                FROM place_change_requests
                """));
        u.put("places", jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE is_active) AS active, count(*) FILTER (WHERE is_active AND source = 'USER') AS user_added
                FROM places
                """));
        return u;
    }

    private interface Check {
        String run() throws Exception;
    }

    private static ComponentStatus timed(String name, Check check) {
        long start = System.nanoTime();
        try {
            String detail = check.run();
            return new ComponentStatus(name, true, (System.nanoTime() - start) / 1_000_000, detail);
        } catch (Exception e) {
            return new ComponentStatus(name, false, (System.nanoTime() - start) / 1_000_000, e.getClass().getSimpleName());
        }
    }
}
