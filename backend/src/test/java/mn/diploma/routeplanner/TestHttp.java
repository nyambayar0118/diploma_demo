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
    private final java.util.function.BiConsumer<String, String> roleSetter;

    TestHttp(int port) {
        this(port, (email, role) -> {
            throw new IllegalStateException("create TestHttp with a JdbcTemplate to set roles");
        });
    }

    TestHttp(int port, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this(port, (email, role) -> jdbc.update("UPDATE users SET role = ? WHERE email = ?", role, email));
    }

    private TestHttp(int port, java.util.function.BiConsumer<String, String> roleSetter) {
        this.port = port;
        this.roleSetter = roleSetter;
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

    /**
     * Registers a user and gives it a role straight in the database (tests only), then logs in again so the
     * token carries the role.
     */
    String tokenWithRole(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "r-" + suffix + "@example.com";
        send("POST", "/api/auth/register", """
                {"email":"%s","password":"secret123","displayName":"Role","username":"r_%s"}
                """.formatted(email, suffix), null);
        roleSetter.accept(email, role);
        HttpResponse<String> login = send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email), null);
        return extract(login.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");
    }

    static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        assertTrue(m.find(), "pattern " + regex + " not found in: " + text);
        return m.group(1);
    }
}
