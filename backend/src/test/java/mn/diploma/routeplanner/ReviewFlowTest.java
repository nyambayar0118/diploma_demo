package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reviews (АШ16): write / edit, average rating; moderators hide (АШ17). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReviewFlowTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void reviewsAndModeration() throws Exception {
        TestHttp http = new TestHttp(port, jdbc);
        long place = jdbc.queryForObject("""
                INSERT INTO places (source, name_cyrillic, category_id, location)
                SELECT 'OSM', 'Сэтгэгдэл кафе', id, ST_SetSRID(ST_MakePoint(107.6, 47.6), 4326) FROM place_categories
                WHERE code = 'cafe' RETURNING id
                """, Long.class);
        String path = "/api/places/" + place + "/reviews";
        String alice = http.registerUser();
        String bob = http.registerUser();

        assertEquals(200, http.send("POST", path, "{\"rating\":4,\"comment\":\"Сайхан\"}", alice).statusCode());
        HttpResponse<String> bobs = http.send("POST", path, "{\"rating\":2,\"comment\":\"Муухай үг\"}", bob);
        long bobReview = Long.parseLong(TestHttp.extract(bobs.body(), "\"id\":(\\d+)"));
        assertAverage(place, "3.00", 2);

        // Writing again edits the same review
        assertEquals(200, http.send("POST", path, "{\"rating\":5}", alice).statusCode());
        assertAverage(place, "3.50", 2);
        assertEquals(400, http.send("POST", path, "{\"rating\":6}", alice).statusCode());
        assertEquals(401, http.send("POST", path, "{\"rating\":3}", null).statusCode());

        // Hiding needs the moderator role
        String hide = "/api/moderation/reviews/" + bobReview + "/hide";
        assertEquals(403, http.send("POST", hide, null, alice).statusCode());
        String moderator = http.tokenWithRole("MODERATOR");
        assertEquals(200, http.send("POST", hide, null, moderator).statusCode());
        assertAverage(place, "5.00", 1);

        String publicList = http.send("GET", path, null, null).body();
        assertFalse(publicList.contains("Муухай"), publicList);
        assertTrue(publicList.contains("Сайхан") || publicList.contains("\"rating\":5"), publicList);
        assertTrue(http.send("GET", path, null, moderator).body().contains("Муухай"));

        assertEquals(204, http.send("DELETE", path + "/mine", null, alice).statusCode());
        assertAverage(place, "0.00", 0);
    }

    private void assertAverage(long place, String avg, int count) {
        assertEquals(new BigDecimal(avg), jdbc.queryForObject("SELECT average_rating FROM places WHERE id = ?",
                BigDecimal.class, place));
        assertEquals(count, jdbc.queryForObject("SELECT review_count FROM places WHERE id = ?", Integer.class, place));
    }
}
