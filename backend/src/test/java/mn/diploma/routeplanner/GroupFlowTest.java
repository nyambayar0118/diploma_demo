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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Бүлгийн төлөвлөгөө (АШ10, АШ24): зөвхөн найз, шударга уулзах газар, хуваалцах, урилгын холбоосоор зочин нэгдэх. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GroupFlowTest {

    static final double LAT = 47.65;
    static final double LNG = 107.40;
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void groupPlanLifecycle() throws Exception {
        TestHttp http = new TestHttp(port);
        long middleCafe = place("Дунд кафе", "cafe", 0.0);
        long cafeNearA = place("А-гийн кафе", "cafe", -0.014);

        String a = http.registerUser();
        String b = http.registerUser();
        String c = http.registerUser();
        long bId = me(http, b).get("id").asLong();
        long cId = me(http, c).get("id").asLong();
        for (String t : new String[] {a, b}) {
            http.send("POST", "/api/onboarding", "{\"categoryCodes\":[\"cafe\",\"museum\",\"park\"],"
                    + "\"consents\":[\"INTERESTS\",\"INTERACTION_HISTORY\"]}", t);
        }
        // A, B найз болно; B байршлаа хуваалцана
        String invite = json(http.send("POST", "/api/friends/invite-link", null, a), 200).get("token").asString();
        json(http.send("POST", "/api/friends/invites/" + invite + "/accept", null, b), 200);
        http.send("POST", "/api/consents/LOCATION_SHARING", null, b);
        json(http.send("PUT", "/api/locations/me",
                "{\"lat\":%s,\"lng\":%s,\"shared\":true}".formatted(LAT, LNG + 0.01), b), 200);

        // Зөвхөн найзыг нэмж болно
        String create = "{\"friendIds\":[%d],\"lat\":%s,\"lng\":%s,\"transportMode\":\"WALKING\",\"timeBudgetMinutes\":180}";
        assertEquals(400, http.send("POST", "/api/groups", create.formatted(cId, LAT, LNG - 0.01), a).statusCode());
        JsonNode group = json(http.send("POST", "/api/groups", create.formatted(bId, LAT, LNG - 0.01), a), 201);
        long groupId = group.get("id").asLong();
        assertEquals(2, group.get("members").size());
        assertTrue(group.get("members").get(1).get("startSource").toString().contains("SHARED_LOCATION"), group.toString());

        // Хувилбар: тэдний дундах кафе A-гийн хажуудахаас өндөр оноо авна (шударга байдлын торгууль)
        JsonNode options = json(http.send("GET", "/api/groups/" + groupId + "/options", null, a), 200);
        assertEquals(middleCafe, options.get("options").get(0).get("place").get("id").asLong(), options.toString());
        boolean nearAListed = false;
        for (JsonNode o : options.get("options")) nearAListed |= o.get("place").get("id").asLong() == cafeNearA;
        assertTrue(nearAListed, options.toString());
        assertEquals("GROUP_LEAST_MISERY", jdbc.queryForObject(
                "SELECT ranker_name FROM recommendation_batches WHERE group_session_id = ? LIMIT 1", String.class, groupId));
        assertEquals(404, http.send("GET", "/api/groups/" + groupId + "/options", null, b).statusCode());

        // Дахин асуух: өмнө санал болгоогүй газар эхэнд орно; өмнөх газрууд зөвхөн үлдсэн байрыг дүүргэнэ
        java.util.Set<Long> firstIds = new java.util.HashSet<>();
        for (JsonNode o : options.get("options")) firstIds.add(o.get("place").get("id").asLong());
        place("Шинэ кафе", "cafe", 0.002);
        JsonNode again = json(http.send("GET", "/api/groups/" + groupId + "/options?again=true", null, a), 200);
        long firstAgain = again.get("options").get(0).get("place").get("id").asLong();
        assertFalse(firstIds.contains(firstAgain), again.toString());

        // Гишүүд төлөвлөгөөг харна; бусад нь харахгүй
        assertEquals(200, http.send("GET", "/api/groups/" + groupId, null, b).statusCode());
        assertEquals(404, http.send("GET", "/api/groups/" + groupId, null, c).statusCode());

        // Хуваалцах: урилгын холбоос + бүртгэлтэй гишүүн бүрд маршрут
        group = json(http.send("POST", "/api/groups/" + groupId + "/share", "{\"placeId\":" + middleCafe + "}", a), 200);
        assertTrue(group.get("status").toString().contains("SHARED"));
        String token = group.get("inviteToken").asString();
        assertFalse(group.get("myRouteId").isNull());
        assertTrue(json(http.send("GET", "/api/groups/" + groupId, null, b), 200).get("members").toString().contains("maps"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM routes WHERE group_session_id = ? AND owner_id = ?",
                Integer.class, groupId, bId));
        assertEquals(409, http.send("PATCH", "/api/groups/" + groupId + "/members",
                "{\"op\":\"ADD\",\"userId\":" + cId + "}", a).statusCode());

        // Бүртгэлгүй зочин холбоосоор нэгдэж, өөрийн Maps холбоосыг авна
        JsonNode info = json(http.send("GET", "/api/groups/join/" + token, null, null), 200);
        assertTrue(info.get("valid").asBoolean());
        assertEquals(400, http.send("POST", "/api/groups/join/" + token, "{}", null).statusCode());
        JsonNode guest = json(http.send("POST", "/api/groups/join/" + token,
                "{\"guestName\":\"Зочин\",\"lat\":%s,\"lng\":%s}".formatted(LAT + 0.005, LNG), null), 200);
        String url = guest.get("mapsUrl").asString();
        assertTrue(url.contains("origin=") && url.contains("destination="), url);
        assertTrue(guest.get("travelMinutes").asInt() > 0);
        // Найз биш нэвтэрсэн хэрэглэгч мөн холбоосоор нэгдэж болно (маршрут авна)
        JsonNode joined = json(http.send("POST", "/api/groups/join/" + token,
                "{\"lat\":%s,\"lng\":%s}".formatted(LAT, LNG + 0.003), c), 200);
        assertFalse(joined.get("routeId").isNull());

        // Цуцлах: холбоос ажиллахаа больж, гишүүдийн маршрут цуцлагдана
        assertEquals(204, http.send("DELETE", "/api/groups/" + groupId, null, a).statusCode());
        assertEquals(404, http.send("GET", "/api/groups/join/" + token, null, null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM routes WHERE group_session_id = ? "
                + "AND status IN ('CREATED', 'OPENED_IN_MAPS')", Integer.class, groupId));
    }

    private static JsonNode me(TestHttp http, String token) throws Exception {
        return json(http.send("GET", "/api/users/me", null, token), 200);
    }

    private static JsonNode json(HttpResponse<String> r, int expected) {
        assertEquals(expected, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }

    private long place(String name, String category, double offset) {
        return jdbc.queryForObject("""
                INSERT INTO places (source, is_verified, name_cyrillic, category_id, location)
                SELECT 'OSM', TRUE, ?, id, ST_SetSRID(ST_MakePoint(?, ?), 4326) FROM place_categories WHERE code = ?
                RETURNING id
                """, Long.class, name, LNG + offset, LAT, category);
    }
}
