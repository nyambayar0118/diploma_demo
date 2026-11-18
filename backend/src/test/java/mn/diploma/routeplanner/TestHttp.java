package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny HTTP helper for the end-to-end tests (no extra test libraries needed). */
final class TestHttp {

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    TestHttp(int port) {
        this.port = port;
    }

    HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Registers a fresh user and returns its token. */
    String registerUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> reg = send("POST", "/api/auth/register", """
                {"email":"u-%s@example.com","password":"secret123","displayName":"Тест","username":"u_%s"}
                """.formatted(suffix, suffix), null);
        assertTrue(reg.statusCode() == 201, reg.body());
        return extract(reg.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");
    }

    static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), "pattern " + regex + " not found in: " + text);
        return m.group(1);
    }
}
