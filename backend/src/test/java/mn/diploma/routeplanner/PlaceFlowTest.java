package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Search, details, save / hide and the interest update triggered by interactions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceFlowTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void searchDetailsMarksAndInterestUpdate() throws Exception {
        TestHttp http = new TestHttp(port);
        long placeId = jdbc.queryForObject("""
                INSERT INTO places (source, is_verified, name_cyrillic, name_latin, category_id, location, osm_tags)
                SELECT 'OSM', TRUE, 'Тэнгэр кино театр', 'Tenger Cinema', id,
                       ST_SetSRID(ST_MakePoint(106.92, 47.92), 4326),
                       '{"name:mn-Mong": "ᠲᠩᠷᠢ", "website": "https://example.mn"}'::jsonb
                FROM place_categories WHERE code = 'cinema'
                RETURNING id
                """, Long.class);

        // Search: Cyrillic, transliterated Latin, typo
        for (String q : new String[] {"тэнгэр", "tenger", "tengr kino"}) {
            HttpResponse<String> r = http.send("GET", "/api/places/search?q=" + q.replace(" ", "%20"), null, null);
            assertEquals(200, r.statusCode(), r.body());
            assertTrue(r.body().contains("Тэнгэр кино театр"), q + " -> " + r.body());
        }

        HttpResponse<String> details = http.send("GET", "/api/places/" + placeId, null, null);
        assertEquals(200, details.statusCode(), details.body());
        assertTrue(details.body().contains("\"nameTraditional\" : \"ᠲᠩᠷᠢ\""), details.body());
        assertTrue(details.body().contains("https://example.mn"), details.body());
        assertEquals(404, http.send("GET", "/api/places/999999999", null, null).statusCode());

        String token = http.registerUser();
        assertEquals(200, http.send("POST", "/api/onboarding",
                "{\"categoryCodes\":[\"cafe\",\"museum\",\"park\"],\"consents\":[\"INTERESTS\",\"INTERACTION_HISTORY\"]}",
                token).statusCode());
        double before = cinemaWeight(http, token);

        assertEquals(401, http.send("PUT", "/api/places/" + placeId + "/mark", "{\"type\":\"SAVED\"}", null).statusCode());
        HttpResponse<String> saved = http.send("PUT", "/api/places/" + placeId + "/mark", "{\"type\":\"SAVED\"}", token);
        assertEquals(200, saved.statusCode(), saved.body());
        assertTrue(http.send("GET", "/api/places/" + placeId + "/mark", null, token).body().contains("SAVED"));
        assertTrue(http.send("GET", "/api/users/me/saved", null, token).body().contains("Тэнгэр кино театр"));

        // The weight update runs asynchronously after the SAVE interaction
        double after = before;
        for (int i = 0; i < 30 && after <= before; i++) {
            Thread.sleep(100);
            after = cinemaWeight(http, token);
        }
        assertTrue(after > before, "cinema weight should grow after SAVE: " + before + " -> " + after);

        assertEquals(202, http.send("POST", "/api/interactions",
                "{\"placeId\":" + placeId + ",\"type\":\"OPEN_IN_MAPS\"}", token).statusCode());
        assertEquals(400, http.send("POST", "/api/interactions",
                "{\"placeId\":" + placeId + ",\"type\":\"VISIT\"}", token).statusCode());

        assertEquals(204, http.send("DELETE", "/api/places/" + placeId + "/mark", null, token).statusCode());
        Integer logged = jdbc.queryForObject(
                "SELECT count(*) FROM user_interactions WHERE place_id = ? AND type IN ('SAVE', 'UNSAVE', 'OPEN_IN_MAPS')",
                Integer.class, placeId);
        assertEquals(3, logged);
    }

    private static double cinemaWeight(TestHttp http, String token) throws Exception {
        String body = http.send("GET", "/api/users/me/interests", null, token).body();
        return Double.parseDouble(TestHttp.extract(body,
                "\"category\":\"cinema\",\"mainCategory\":\"entertainment\",\"weight\":([0-9.]+)"));
    }
}
