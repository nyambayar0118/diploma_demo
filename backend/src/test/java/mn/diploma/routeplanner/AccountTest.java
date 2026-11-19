package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Account deletion (ХШ08) and role management by an admin. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.admin-emails=admin-test@example.com")
class AccountTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void deleteAccountRemovesPersonalData() throws Exception {
        TestHttp http = new TestHttp(port);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "del-" + suffix + "@example.com";
        HttpResponse<String> reg = http.send("POST", "/api/auth/register", """
                {"email":"%s","password":"secret123","displayName":"Устгах","username":"del_%s"}
                """.formatted(email, suffix), null);
        String token = TestHttp.extract(reg.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");
        long userId = Long.parseLong(TestHttp.extract(reg.body(), "\"id\":(\\d+)"));
        http.send("POST", "/api/onboarding", "{\"categoryCodes\":[\"cafe\",\"park\",\"museum\"],"
                + "\"consents\":[\"INTERESTS\",\"INTERACTION_HISTORY\"]}", token);
        long place = jdbc.queryForObject("""
                INSERT INTO places (source, name_cyrillic, category_id, location)
                SELECT 'OSM', 'Устгах тест', id, ST_SetSRID(ST_MakePoint(107.5, 47.5), 4326) FROM place_categories
                WHERE code = 'cafe' RETURNING id
                """, Long.class);
        http.send("PUT", "/api/places/" + place + "/mark", "{\"type\":\"SAVED\"}", token);

        assertEquals(204, http.send("DELETE", "/api/users/me", null, token).statusCode());

        assertEquals(401, http.send("GET", "/api/users/me", null, token).statusCode());
        assertEquals(401, http.send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email), null).statusCode());
        assertTrue(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId).startsWith("deleted-"));
        for (String table : new String[] {"interest_weights", "place_marks", "privacy_consents", "onboarding_surveys"}) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId),
                    table);
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_interactions WHERE user_id = ?", Integer.class, userId));

        // The e-mail can be used again for a new account
        assertEquals(201, http.send("POST", "/api/auth/register", """
                {"email":"%s","password":"secret123","displayName":"Шинэ","username":"new_%s"}
                """.formatted(email, suffix), null).statusCode());
    }

    @Test
    void adminGivesModeratorRole() throws Exception {
        TestHttp http = new TestHttp(port);
        jdbc.update("UPDATE users SET deleted_at = now(), email = 'old-' || id || '@x.invalid' "
                + "WHERE lower(email) = 'admin-test@example.com'");
        HttpResponse<String> admin = http.send("POST", "/api/auth/register", """
                {"email":"admin-test@example.com","password":"secret123","displayName":"Админ","username":"admin_%s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8)), null);
        assertTrue(admin.body().contains("\"role\":\"ADMIN\""), admin.body());
        String adminToken = TestHttp.extract(admin.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");

        String userToken = http.registerUser();
        String userId = TestHttp.extract(http.send("GET", "/api/users/me", null, userToken).body(), "\"id\":(\\d+)");
        assertEquals(403, http.send("PUT", "/api/admin/users/" + userId + "/role", "{\"role\":\"MODERATOR\"}", userToken)
                .statusCode());
        HttpResponse<String> r = http.send("PUT", "/api/admin/users/" + userId + "/role", "{\"role\":\"MODERATOR\"}", adminToken);
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("MODERATOR"));
    }
}
