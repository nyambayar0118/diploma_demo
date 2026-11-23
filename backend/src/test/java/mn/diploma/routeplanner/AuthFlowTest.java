package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Бүртгүүлэх, нэвтрэх, профайл, тохиргоо, зөвшөөрөл, бүртгэлийн асуулгыг бодит PostgreSQL + PostGIS дээр
 * (Flyway шилжилтүүдийн хамт) эхнээс нь дуустал шалгана. DB_URL/DB_USER/DB_PASSWORD хэрэгтэй;
 * CI-д тэдгээрийг postgis сервисийн контейнер олгоно.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void registerLoginOnboardingFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "test-" + suffix + "@example.com";
        String register = """
                {"email":"%s","password":"secret123","displayName":"Тест хэрэглэгч","username":"user_%s"}
                """.formatted(email, suffix);

        HttpResponse<String> reg = send("POST", "/api/auth/register", register, null);
        assertEquals(201, reg.statusCode(), reg.body());
        String token = extract(reg.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");

        assertEquals(409, send("POST", "/api/auth/register", register, null).statusCode());

        HttpResponse<String> badLogin = send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email), null);
        assertEquals(401, badLogin.statusCode());
        assertTrue(badLogin.body().contains("INVALID_CREDENTIALS"), badLogin.body());

        HttpResponse<String> login = send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email.toUpperCase()), null);
        assertEquals(200, login.statusCode(), login.body());

        assertEquals(401, send("GET", "/api/users/me", null, null).statusCode());
        HttpResponse<String> me = send("GET", "/api/users/me", null, token);
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.body().contains("\"role\":\"USER\""), me.body());

        assertEquals(200, send("GET", "/api/categories", null, null).statusCode());

        HttpResponse<String> prefs = send("GET", "/api/users/me/preferences", null, token);
        assertEquals(200, prefs.statusCode(), prefs.body());
        assertTrue(prefs.body().contains("\"searchRadiusM\":5000"), prefs.body());
        HttpResponse<String> badPrefs = send("PUT", "/api/users/me/preferences",
                "{\"transportModes\":[\"WALKING\"],\"budgetLevel\":\"LOW\",\"searchRadiusM\":10,"
                        + "\"timeBudgetMin\":120,\"stopCount\":2}", token);
        assertEquals(400, badPrefs.statusCode(), badPrefs.body());

        HttpResponse<String> notStarted = send("GET", "/api/onboarding", null, token);
        assertTrue(notStarted.body().contains("\"completed\":false"), notStarted.body());

        HttpResponse<String> tooFew = send("POST", "/api/onboarding",
                "{\"categoryCodes\":[\"cafe\"],\"consents\":[\"INTERESTS\"]}", token);
        assertEquals(400, tooFew.statusCode(), tooFew.body());

        HttpResponse<String> onboarding = send("POST", "/api/onboarding",
                "{\"categoryCodes\":[\"cafe\",\"cinema\",\"museum\"],\"consents\":[\"INTERESTS\",\"LOCATION\"]}", token);
        assertEquals(200, onboarding.statusCode(), onboarding.body());

        HttpResponse<String> interests = send("GET", "/api/users/me/interests", null, token);
        assertTrue(interests.body().contains("\"category\":\"cafe\""), interests.body());
        HttpResponse<String> consents = send("GET", "/api/consents", null, token);
        assertTrue(consents.body().contains("\"dataType\":\"LOCATION\""), consents.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), "pattern not found in: " + text);
        return m.group(1);
    }
}
