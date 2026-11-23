package mn.diploma.routeplanner.monitoring;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Хяналтын модулийн API (зөвхөн администратор, SecurityConfig-ийг үз): төлөв, хэмжүүр, хяналтын хуудасны өгөгдөл (СНШ05, СНШ07). */
@RestController
public class AdminController {

    private final MonitoringService monitoring;
    private final RequestMetrics metrics;
    private final JdbcTemplate jdbc;
    private final AlertService alerts;

    public AdminController(MonitoringService monitoring, RequestMetrics metrics, JdbcTemplate jdbc, AlertService alerts) {
        this.monitoring = monitoring;
        this.metrics = metrics;
        this.jdbc = jdbc;
        this.alerts = alerts;
    }

    @GetMapping("/api/admin/health")
    public MonitoringService.HealthReport health() {
        return monitoring.check();
    }

    /** Сүүлийн {@code minutes} (анхдагч 60) минутын хариу өгөх хугацаа / алдаа болон туршилтын хэрэглээний тоо. */
    @GetMapping("/api/admin/metrics")
    public Map<String, Object> metrics(@RequestParam(defaultValue = "60") int minutes) {
        int window = Math.clamp(minutes, 1, (int) RequestMetrics.WINDOW.toMinutes());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMinutes", window);
        result.put("emailAlerts", alerts.mailConfigured());
        result.put("endpoints", metrics.stats(Duration.ofMinutes(window)));
        result.put("usage", monitoring.usage());
        return result;
    }

    /** Эрхийн удирдлагад зориулсан хэрэглэгчийн хайлт (PUT /api/admin/users/{id}/role). */
    @GetMapping("/api/admin/users")
    public List<Map<String, Object>> users(@RequestParam(defaultValue = "") String q) {
        return jdbc.queryForList("""
                SELECT id, email, username, display_name, role, auth_provider, reward_points, created_at
                FROM users
                WHERE deleted_at IS NULL
                  AND (? = '' OR email ILIKE '%' || ? || '%' OR username ILIKE '%' || ? || '%' OR display_name ILIKE '%' || ? || '%')
                ORDER BY (role <> 'USER') DESC, created_at DESC
                LIMIT 50
                """, q, q, q, q);
    }
}
