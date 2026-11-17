package mn.diploma.routeplanner.monitoring;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * GET /api/health: status of the core server, the database (with PostGIS)
 * and the ML service. Used by the walking skeleton's map page; the full
 * admin endpoints (/api/admin/health, /api/admin/metrics) come later.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final RestClient mlClient;

    public HealthController(JdbcTemplate jdbc, @Value("${app.ml.base-url}") String mlBaseUrl) {
        this.jdbc = jdbc;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofMillis(500));
        this.mlClient = RestClient.builder().baseUrl(mlBaseUrl).requestFactory(factory).build();
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");

        try {
            String postgis = jdbc.queryForObject("SELECT postgis_lib_version()", String.class);
            result.put("database", "UP");
            result.put("postgis", postgis);
        } catch (Exception e) {
            result.put("database", "DOWN");
        }

        try {
            mlClient.get().uri("/health").retrieve().toBodilessEntity();
            result.put("ml", "UP");
        } catch (Exception e) {
            result.put("ml", "DOWN");
        }
        return result;
    }
}
