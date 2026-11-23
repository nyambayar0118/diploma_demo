package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.json.JsonMapper;

/** Найз (АШ20) болон байршил хуваалцах (АШ21, ХШ07). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FriendFlowTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Test
    void friendsAndLocationSharing() throws Exception {
        TestHttp http = new TestHttp(port);
        String a = http.registerUser();
        String b = http.registerUser();
        String bName = username(http, b);

        // Хэрэглэгчийн нэрээр хүсэлт -> B ирсэн хүсэлт болгон харна -> зөвшөөрнө
        HttpResponse<String> req = http.send("POST", "/api/friends/requests", "{\"username\":\"" + bName + "\"}", a);
        assertEquals(201, req.statusCode(), req.body());
        assertTrue(req.body().contains("OUTGOING"));
        long id = JSON.readTree(req.body()).get("id").asLong();
        assertEquals(409, http.send("POST", "/api/friends/requests", "{\"username\":\"" + bName + "\"}", a).statusCode());
        assertTrue(http.send("GET", "/api/friends", null, b).body().contains("INCOMING"));
        assertEquals(404, http.send("POST", "/api/friends/requests/" + id + "/accept", null, a).statusCode());
        assertEquals(200, http.send("POST", "/api/friends/requests/" + id + "/accept", null, b).statusCode());
        assertTrue(http.send("GET", "/api/friends", null, a).body().contains("FRIEND"));
        assertEquals(404, http.send("POST", "/api/friends/requests", "{\"username\":\"no_such_user_x\"}", a).statusCode());

        // Байршил хуваалцахад LOCATION_SHARING зөвшөөрөл хэрэгтэй
        String share = "{\"lat\":47.91,\"lng\":106.91,\"shared\":true}";
        assertEquals(403, http.send("PUT", "/api/locations/me", share, a).statusCode());
        http.send("POST", "/api/consents/LOCATION_SHARING", null, a);
        assertEquals(200, http.send("PUT", "/api/locations/me", share, a).statusCode());
        assertTrue(http.send("GET", "/api/friends/locations", null, b).body().contains("47.91"));
        // Найз биш -> юу ч харагдахгүй
        String stranger = http.registerUser();
        assertEquals("[]", http.send("GET", "/api/friends/locations", null, stranger).body());
        // Хуваалцахаа зогсоовол -> алга болно; зөвшөөрлөө цуцлах нь мөн нууна
        http.send("PUT", "/api/locations/me", "{\"shared\":false}", a);
        assertEquals("[]", http.send("GET", "/api/friends/locations", null, b).body());
        http.send("PUT", "/api/locations/me", share, a);
        http.send("DELETE", "/api/consents/LOCATION_SHARING", null, a);
        assertEquals("[]", http.send("GET", "/api/friends/locations", null, b).body());

        // Урилгын холбоос: нээхэд хоёулаа шууд найз болно
        String token = TestHttp.extract(http.send("POST", "/api/friends/invite-link", null, a).body(),
                "\"token\":\"([^\"]+)\"");
        assertEquals(200, http.send("GET", "/api/friends/invites/" + token, null, null).statusCode());
        assertEquals(404, http.send("GET", "/api/friends/invites/" + token + "x", null, null).statusCode());
        assertEquals(200, http.send("POST", "/api/friends/invites/" + token + "/accept", null, stranger).statusCode());
        assertTrue(http.send("GET", "/api/friends", null, stranger).body().contains("FRIEND"));
        // Урилгын токен нь нэвтрэх токен биш
        assertEquals(401, http.send("GET", "/api/users/me", null, token).statusCode());

        // Татгалзаад дахин хүсэлт илгээх; найзаа хасах
        String c = http.registerUser();
        HttpResponse<String> cReq = http.send("POST", "/api/friends/requests", "{\"username\":\"" + bName + "\"}", c);
        long cId = JSON.readTree(cReq.body()).get("id").asLong();
        assertEquals(204, http.send("POST", "/api/friends/requests/" + cId + "/reject", null, b).statusCode());
        assertFalse(http.send("GET", "/api/friends", null, c).body().contains("OUTGOING"));
        assertEquals(201, http.send("POST", "/api/friends/requests", "{\"username\":\"" + bName + "\"}", c).statusCode());
        assertEquals(204, http.send("DELETE", "/api/friends/" + id, null, b).statusCode());
        assertFalse(http.send("GET", "/api/friends", null, a).body().contains(bName));
    }

    private static String username(TestHttp http, String token) throws Exception {
        return TestHttp.extract(http.send("GET", "/api/users/me", null, token).body(), "\"username\":\"([^\"]+)\"");
    }
}
