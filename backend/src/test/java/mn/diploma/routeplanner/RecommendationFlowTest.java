package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Rule-based recommendations: filters (hidden, closed, time of day), ranking by interest, batch logging. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecommendationFlowTest {

    // An area far from the other tests' places, so they do not interfere
    static final double LAT = 47.80;
    static final double LNG = 107.10;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void recommendFiltersRanksAndLogs() throws Exception {
        TestHttp http = new TestHttp(port);
        long cafe = place("Рек кафе", "cafe", 0.002, "24/7");
        long cafe2 = place("Рек кафе хоёр", "cafe", 0.004, null);
        long nightclub = place("Рек клуб", "nightclub", 0.001, null);
        long museum = place("Рек музей", "museum", 0.003, "Mo-Su 09:00-10:00");
        long park = place("Рек цэцэрлэг", "park", 0.005, null);
        long farCafe = place("Хол кафе", "cafe", 0.2, null);

        String token = http.registerUser();
        assertEquals(200, http.send("POST", "/api/onboarding",
                "{\"categoryCodes\":[\"cafe\",\"cinema\",\"museum\"],"
                        + "\"consents\":[\"INTERESTS\",\"LOCATION\",\"INTERACTION_HISTORY\"]}", token).statusCode());
        assertEquals(200, http.send("PUT", "/api/places/" + cafe2 + "/mark", "{\"type\":\"HIDDEN\"}", token).statusCode());

        // Wednesday 15:00: nightclub unsuitable (prior 0), museum closed, cafe 2 hidden, far cafe outside 5 km
        String body = "{\"lat\":%s,\"lng\":%s,\"at\":\"2026-11-18T15:00:00\",\"k\":10}".formatted(LAT, LNG);
        HttpResponse<String> r = http.send("POST", "/api/recommendations", body, token);
        assertEquals(200, r.statusCode(), r.body());
        String json = r.body();
        assertTrue(json.contains("\"placeId\":" + cafe + ","), json);
        assertTrue(json.contains("\"placeId\":" + park + ","), json);
        for (long excluded : new long[] {cafe2, nightclub, museum, farCafe}) {
            assertFalse(json.contains("\"placeId\":" + excluded + ","), "should be excluded: " + excluded + " " + json);
        }
        // The cafe (interest 0.8) ranks above the park (0.2)
        assertTrue(json.indexOf("\"placeId\":" + cafe + ",") < json.indexOf("\"placeId\":" + park + ","), json);

        long batchId = Long.parseLong(TestHttp.extract(json, "\"batchId\":(\\d+)"));
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM recommendations WHERE batch_id = ?", Integer.class, batchId);
        assertTrue(rows != null && rows >= 2);
        assertEquals("RULE_BASED", jdbc.queryForObject(
                "SELECT ranker_name FROM recommendation_batches WHERE id = ?", String.class, batchId));

        // The client reports ACCEPT with the recommendation id (training label 1)
        long recId = Long.parseLong(TestHttp.extract(json, "\"recommendationId\":(\\d+)"));
        assertEquals(202, http.send("POST", "/api/interactions",
                "{\"placeId\":" + cafe + ",\"type\":\"ACCEPT\",\"recommendationId\":" + recId + "}", token).statusCode());

        assertEquals(401, http.send("POST", "/api/recommendations", body, null).statusCode());
    }

    private long place(String name, String category, double offset, String openingHours) {
        return jdbc.queryForObject("""
                INSERT INTO places (source, is_verified, name_cyrillic, category_id, location, opening_hours_raw)
                SELECT 'OSM', TRUE, ?, id, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?
                FROM place_categories WHERE code = ?
                RETURNING id
                """, Long.class, name, LNG + offset, LAT, openingHours, category);
    }
}
