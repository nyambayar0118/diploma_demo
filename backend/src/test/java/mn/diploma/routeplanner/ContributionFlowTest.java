package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Хувь нэмэр (АШ03-АШ06, АШ18): илгээх -> модератор батлах / татгалзах -> газар өөрчлөгдөх, оноо. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContributionFlowTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contributionLifecycle() throws Exception {
        TestHttp http = new TestHttp(port, jdbc);
        String user = http.registerUser();
        String moderator = http.tokenWithRole("MODERATOR");

        // Шинэ газар
        assertEquals(400, http.send("POST", "/api/change-requests",
                "{\"type\":\"CREATE_PLACE\",\"data\":{\"categoryCode\":\"cafe\",\"lat\":47.55,\"lng\":107.7}}", user).statusCode());
        JsonNode created = json(http.send("POST", "/api/change-requests", """
                {"type":"CREATE_PLACE","data":{"nameCyrillic":"Хувь нэмэр кафе","categoryCode":"cafe",
                 "lat":47.55,"lng":107.7,"openingHours":"Mo-Su 09:00-21:00"}}
                """, user), 201);
        assertTrue(created.get("status").toString().contains("PENDING"));
        long createId = created.get("id").asLong();

        assertEquals(403, http.send("GET", "/api/moderation/requests", null, user).statusCode());
        assertTrue(json(http.send("GET", "/api/moderation/requests?status=PENDING", null, moderator), 200).toString()
                .contains("Хувь нэмэр кафе"));
        JsonNode approved = json(http.send("POST", "/api/moderation/requests/" + createId + "/approve", null, moderator), 200);
        long placeId = approved.get("target").get("id").asLong();
        assertEquals("USER", jdbc.queryForObject("SELECT source FROM places WHERE id = ?", String.class, placeId));
        assertEquals(409, http.send("POST", "/api/moderation/requests/" + createId + "/approve", null, moderator).statusCode());

        // Засвар: талбар OSM-ийг дахин импортлоход хамгаалагдана (СНШ10)
        long editId = json(http.send("POST", "/api/change-requests", """
                {"type":"UPDATE_PLACE","targetPlaceId":%d,"data":{"nameCyrillic":"Зассан кафе","phone":"99112233"}}
                """.formatted(placeId), user), 201).get("id").asLong();
        json(http.send("POST", "/api/moderation/requests/" + editId + "/approve", "{\"comment\":\"Баярлалаа\"}", moderator), 200);
        assertEquals("Зассан кафе", jdbc.queryForObject("SELECT name_cyrillic FROM places WHERE id = ?", String.class, placeId));
        assertTrue(jdbc.queryForObject("SELECT 'name_cyrillic' = ANY (user_edited_fields) FROM places WHERE id = ?",
                Boolean.class, placeId));

        // Холбоос
        long linkId = json(http.send("POST", "/api/change-requests", """
                {"type":"ADD_LINK","targetPlaceId":%d,"data":{"url":"https://facebook.com/test-cafe","linkType":"FACEBOOK"}}
                """.formatted(placeId), user), 201).get("id").asLong();
        assertEquals(400, http.send("POST", "/api/change-requests", """
                {"type":"ADD_LINK","targetPlaceId":%d,"data":{"url":"javascript:alert(1)","linkType":"OTHER"}}
                """.formatted(placeId), user).statusCode());
        json(http.send("POST", "/api/moderation/requests/" + linkId + "/approve", null, moderator), 200);
        assertTrue(http.send("GET", "/api/places/" + placeId, null, null).body().contains("facebook.com/test-cafe"));

        // Татгалзахад шалтгаан заавал, илгээгч түүнийг харна
        long badId = json(http.send("POST", "/api/change-requests", """
                {"type":"UPDATE_PLACE","targetPlaceId":%d,"data":{"permanentlyClosed":true}}
                """.formatted(placeId), user), 201).get("id").asLong();
        assertEquals(400, http.send("POST", "/api/moderation/requests/" + badId + "/reject", "{}", moderator).statusCode());
        json(http.send("POST", "/api/moderation/requests/" + badId + "/reject", "{\"comment\":\"Нээлттэй байна\"}", moderator), 200);
        assertTrue(json(http.send("GET", "/api/users/me/change-requests", null, user), 200).toString().contains("Нээлттэй байна"));

        // Оноо: 20 (шинэ) + 10 (засвар) + 5 (холбоос)
        assertEquals(35, json(http.send("GET", "/api/users/me/rewards", null, user), 200).get("total").asInt());

        // Модератор өөрийн хүсэлтийг батлах боломжгүй
        long own = json(http.send("POST", "/api/change-requests", """
                {"type":"UPDATE_PLACE","targetPlaceId":%d,"data":{"street":"Энхтайваны өргөн чөлөө"}}
                """.formatted(placeId), moderator), 201).get("id").asLong();
        assertEquals(403, http.send("POST", "/api/moderation/requests/" + own + "/approve", null, moderator).statusCode());
    }

    private static JsonNode json(HttpResponse<String> r, int expected) {
        assertEquals(expected, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }
}
