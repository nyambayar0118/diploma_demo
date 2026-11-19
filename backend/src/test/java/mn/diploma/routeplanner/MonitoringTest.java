package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Admin monitoring (СНШ05, СНШ07): component health, request metrics, usage numbers; admins only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoringTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void adminSeesHealthAndMetrics() throws Exception {
        TestHttp http = new TestHttp(port, jdbc);
        String admin = http.tokenWithRole("ADMIN");
        String user = http.registerUser();
        for (int i = 0; i < 3; i++) http.send("GET", "/api/places/search?q=kafe", null, null);

        assertEquals(403, http.send("GET", "/api/admin/metrics", null, user).statusCode());
        assertEquals(401, http.send("GET", "/api/admin/health", null, null).statusCode());

        HttpResponse<String> health = http.send("GET", "/api/admin/health", null, admin);
        assertEquals(200, health.statusCode(), health.body());
        assertTrue(health.body().contains("\"name\":\"database\"") && health.body().contains("\"up\":true"), health.body());

        HttpResponse<String> metrics = http.send("GET", "/api/admin/metrics?minutes=15", null, admin);
        assertEquals(200, metrics.statusCode(), metrics.body());
        String body = metrics.body();
        assertTrue(body.contains("GET /api/places/search"), body);
        assertTrue(body.contains("\"targetP95Ms\":1000"), body);
        assertTrue(body.contains("\"users\""), body);
        // The rejected call above is counted too (the filter runs before Spring Security)
        assertTrue(body.contains("(rejected)"), body);

        assertTrue(http.send("GET", "/api/admin/users?q=r-", null, admin).body().contains("ADMIN"));
    }
}
