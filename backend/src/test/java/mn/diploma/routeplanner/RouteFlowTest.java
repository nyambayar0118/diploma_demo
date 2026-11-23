package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import mn.diploma.routeplanner.routing.RouteExpiryJob;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Маршрут: дараалуулах, Maps холбоос, өөрчлөх, нээх -> зочлох -> дуусгах -> үнэлгээ, хугацаа дуусах. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteFlowTest {

    // Бусад тестийн газраас хол газар
    static final double LAT = 47.70;
    static final double LNG = 107.30;

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RouteExpiryJob expiryJob;

    @Test
    void routeLifecycle() throws Exception {
        TestHttp http = new TestHttp(port);
        // Эхлэх цэгийн зүүн талд нэг шугам дээрх гурван газар
        long near = place("Маршрут кафе", "cafe", 0.003);
        long middle = place("Маршрут цэцэрлэг", "park", 0.006);
        long far = place("Маршрут галерей", "gallery", 0.009);
        long extra = place("Маршрут номын дэлгүүр", "bookshop", 0.004);

        String token = http.registerUser();
        assertEquals(200, http.send("POST", "/api/onboarding",
                "{\"categoryCodes\":[\"cafe\",\"park\",\"gallery\"],"
                        + "\"consents\":[\"INTERESTS\",\"LOCATION\",\"INTERACTION_HISTORY\"]}", token).statusCode());

        // Зогсоолд санал болголтын id-г хадгалдаг тул VISIT-ийг эрэмбэлсэн жагсаалттай холбож болно
        JsonNode recs = json(http.send("POST", "/api/recommendations",
                "{\"lat\":%s,\"lng\":%s,\"k\":10}".formatted(LAT, LNG), token), 200);
        Long nearRec = null;
        for (JsonNode item : recs.get("items")) {
            if (item.get("placeId").asLong() == near) nearRec = item.get("recommendationId").asLong();
        }

        // Буруу дарааллаар хүссэн; оновчлогч ойр -> дунд -> хол дарааллаар зочилно
        String create = """
                {"lat":%s,"lng":%s,"transportMode":"WALKING","timeBudgetMinutes":240,
                 "stops":[{"placeId":%d},{"placeId":%d,"recommendationId":%s},{"placeId":%d}]}
                """.formatted(LAT, LNG, far, near, nearRec, middle);
        JsonNode route = json(http.send("POST", "/api/routes", create, token), 201);
        long routeId = route.get("id").asLong();
        assertEquals(List.of(near, middle, far), placeIds(route));
        assertTrue(route.get("status").toString().contains("CREATED"));
        assertEquals(1, route.get("mapsLinks").size());
        String url = route.get("mapsLinks").get(0).get("url").toString();
        assertTrue(url.contains("travelmode=walking") && url.contains("waypoints="), url);
        assertTrue(route.get("estimatedDurationMinutes").asInt() > 0);

        // Өөрчлөх: дунд зогсоолыг хасаж, өөр газар нэмэх; давхардлыг татгалзана
        long middleStop = stopId(route, middle);
        route = json(http.send("PATCH", "/api/routes/" + routeId + "/stops",
                "{\"op\":\"REMOVE\",\"stopId\":" + middleStop + "}", token), 200);
        assertEquals(List.of(near, far), placeIds(route));
        route = json(http.send("PATCH", "/api/routes/" + routeId + "/stops",
                "{\"op\":\"ADD\",\"placeId\":" + extra + "}", token), 200);
        assertEquals(List.of(near, extra, far), placeIds(route));
        assertEquals(400, http.send("PATCH", "/api/routes/" + routeId + "/stops",
                "{\"op\":\"ADD\",\"placeId\":" + near + "}", token).statusCode());

        // Гараар тогтоосон дарааллыг хэвээр хадгална (ирэх цаг түүнийг дагана); OPTIMIZE хамгийн богино дараалал руу буцаана
        String reorder = "{\"op\":\"REORDER\",\"stopIds\":[%d,%d,%d]}"
                .formatted(stopId(route, far), stopId(route, near), stopId(route, extra));
        route = json(http.send("PATCH", "/api/routes/" + routeId + "/stops", reorder, token), 200);
        assertEquals(List.of(far, near, extra), placeIds(route));
        assertTrue(route.get("stops").get(0).get("estimatedArrival").asString()
                .compareTo(route.get("stops").get(1).get("estimatedArrival").asString()) < 0);
        assertEquals(400, http.send("PATCH", "/api/routes/" + routeId + "/stops",
                "{\"op\":\"REORDER\",\"stopIds\":[%d]}".formatted(stopId(route, far)), token).statusCode());
        // Зогсоол хасахад бусдын гар дараалал хадгалагдана
        long extraStop = stopId(route, extra);
        route = json(http.send("PATCH", "/api/routes/" + routeId + "/stops",
                "{\"op\":\"REMOVE\",\"stopId\":" + extraStop + "}", token), 200);
        assertEquals(List.of(far, near), placeIds(route));
        route = json(http.send("PATCH", "/api/routes/" + routeId + "/stops", "{\"op\":\"ADD\",\"placeId\":" + extra + "}",
                token), 200);
        assertEquals(List.of(far, near, extra), placeIds(route));
        route = json(http.send("PATCH", "/api/routes/" + routeId + "/stops", "{\"op\":\"OPTIMIZE\"}", token), 200);
        assertEquals(List.of(near, extra, far), placeIds(route));

        // Сонгосон ангиллаар хязгаарласан санал болголт
        JsonNode onlyCafes = json(http.send("POST", "/api/recommendations",
                "{\"lat\":%s,\"lng\":%s,\"k\":10,\"categories\":[\"cafe\"]}".formatted(LAT, LNG), token), 200);
        for (JsonNode item : onlyCafes.get("items")) {
            assertEquals("cafe", item.get("category").asString(), onlyCafes.toString());
        }
        assertEquals("{cafe}", jdbc.queryForObject("SELECT category_filter::text FROM recommendation_batches WHERE id = ?",
                String.class, onlyCafes.get("batchId").asLong()));

        // Бусад хэрэглэгч харахгүй
        assertEquals(404, http.send("GET", "/api/routes/" + routeId, null, http.registerUser()).statusCode());

        // Google Maps-д нээх: маршрут түгжигдэж, зогсоол бүрд OPEN_IN_MAPS бүртгэгдэнэ
        route = json(http.send("POST", "/api/routes/" + routeId + "/open", null, token), 200);
        assertTrue(route.get("status").toString().contains("OPENED_IN_MAPS"));
        assertEquals(409, http.send("PATCH", "/api/routes/" + routeId + "/stops",
                "{\"op\":\"REMOVE\",\"stopId\":" + stopId(route, far) + "}", token).statusCode());
        assertEquals(3, count("SELECT count(*) FROM user_interactions WHERE route_id = ? AND type = 'OPEN_IN_MAPS'", routeId));

        // Эхний зогсоолын дэргэд байх үед: апп түүний талаар "Очсон уу?" гэж асууна
        JsonNode pending = json(http.send("GET", "/api/visits/pending?lat=%s&lng=%s".formatted(LAT, LNG + 0.003),
                null, token), 200);
        assertTrue(pending.get("nearby").asBoolean());
        assertEquals(near, pending.get("placeId").asLong());
        long nearStop = pending.get("stopId").asLong();

        JsonNode visit = json(http.send("POST", "/api/visits/" + nearStop + "/confirm",
                "{\"lat\":%s,\"lng\":%s}".formatted(LAT, LNG + 0.003), token), 200);
        assertTrue(visit.get("visited").asBoolean());
        assertEquals(1, count("SELECT count(*) FROM user_interactions WHERE route_id = ? AND type = 'VISIT' AND place_id = ?",
                routeId, near));
        if (nearRec != null) {
            assertEquals(nearRec, jdbc.queryForObject("SELECT recommendation_id FROM user_interactions "
                    + "WHERE route_id = ? AND type = 'VISIT'", Long.class, routeId));
        }

        // Бусад хоёрт "очоогүй": бүх зогсоолд хариулсан тул маршрут дуусна
        http.send("POST", "/api/visits/" + stopId(route, extra) + "/skip", null, token);
        visit = json(http.send("POST", "/api/visits/" + stopId(route, far) + "/skip", null, token), 200);
        assertTrue(visit.get("routeStatus").toString().contains("COMPLETED"), visit.toString());
        assertEquals(204, http.send("GET", "/api/visits/pending", null, token).statusCode());

        // Сэтгэл ханамжийн үнэлгээ, нэг удаа
        String fb = "{\"relevance\":5,\"timeSuitability\":4,\"estimateAccuracy\":3}";
        assertEquals(201, http.send("POST", "/api/routes/" + routeId + "/feedback", fb, token).statusCode());
        assertEquals(409, http.send("POST", "/api/routes/" + routeId + "/feedback", fb, token).statusCode());
        assertTrue(json(http.send("GET", "/api/routes/" + routeId, null, token), 200).get("feedbackGiven").asBoolean());

        // Нийтийн тээвэр: Google Maps-д дамжих цэг дэмжигддэггүй тул хэсэг бүрд нэг холбоос
        JsonNode transit = json(http.send("POST", "/api/routes", """
                {"lat":%s,"lng":%s,"transportMode":"PUBLIC_TRANSPORT","stops":[{"placeId":%d},{"placeId":%d}]}
                """.formatted(LAT, LNG, near, far), token), 201);
        assertEquals(2, transit.get("mapsLinks").size());
        assertEquals(204, http.send("DELETE", "/api/routes/" + transit.get("id").asLong(), null, token).statusCode());

        // Автомат маршрут: сервер зогсоолыг өөрийн санал болголтоос сонгоно
        JsonNode auto = json(http.send("POST", "/api/routes",
                "{\"lat\":%s,\"lng\":%s,\"timeBudgetMinutes\":240}".formatted(LAT, LNG), token), 201);
        assertFalse(auto.get("stops").isEmpty());
        assertTrue(auto.get("stops").size() <= 3);

        // Өнгөрсөн өдөрт төлөвлөсөн маршрутыг хугацааны ажил хаана (огт нээгээгүй -> CANCELLED)
        JsonNode old = json(http.send("POST", "/api/routes", """
                {"lat":%s,"lng":%s,"startAt":"2020-01-01T10:00:00","stops":[{"placeId":%d}]}
                """.formatted(LAT, LNG, near), token), 201);
        assertTrue(expiryJob.closeExpiredRoutes() >= 1);
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM routes WHERE id = ?", String.class,
                old.get("id").asLong()));

        assertEquals(401, http.send("POST", "/api/routes", create, null).statusCode());
    }

    private static JsonNode json(HttpResponse<String> r, int expectedStatus) {
        assertEquals(expectedStatus, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }

    private static List<Long> placeIds(JsonNode route) {
        List<Long> ids = new ArrayList<>();
        route.get("stops").forEach(s -> ids.add(s.get("placeId").asLong()));
        return ids;
    }

    private static long stopId(JsonNode route, long placeId) {
        for (JsonNode s : route.get("stops")) {
            if (s.get("placeId").asLong() == placeId) return s.get("stopId").asLong();
        }
        throw new AssertionError("no stop for place " + placeId + ": " + route);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private long place(String name, String category, double offset) {
        return jdbc.queryForObject("""
                INSERT INTO places (source, is_verified, name_cyrillic, category_id, location)
                SELECT 'OSM', TRUE, ?, id, ST_SetSRID(ST_MakePoint(?, ?), 4326)
                FROM place_categories WHERE code = ?
                RETURNING id
                """, Long.class, name, LNG + offset, LAT, category);
    }
}
