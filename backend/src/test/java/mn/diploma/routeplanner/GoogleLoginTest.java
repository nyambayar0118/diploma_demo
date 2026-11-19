package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import mn.diploma.routeplanner.auth.GoogleTokenVerifier;
import mn.diploma.routeplanner.auth.GoogleTokenVerifier.GoogleUser;

/** Google login with the token verification mocked (CI cannot call Google). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoogleLoginTest {

    @Value("${local.server.port}")
    int port;

    @MockitoBean
    GoogleTokenVerifier verifier;

    @Test
    void createsThenLogsInGoogleUser() throws Exception {
        TestHttp http = new TestHttp(port);
        String sub = UUID.randomUUID().toString();
        String email = "g-" + sub.substring(0, 8) + "@gmail.com";
        when(verifier.isEnabled()).thenReturn(true);
        when(verifier.clientId()).thenReturn("test-client.apps.googleusercontent.com");
        when(verifier.verify(anyString())).thenReturn(Optional.empty());
        when(verifier.verify("good")).thenReturn(Optional.of(new GoogleUser(sub, email, true, "Бат Эрдэнэ")));

        assertTrue(http.send("GET", "/api/auth/config", null, null).body().contains("test-client"));

        HttpResponse<String> first = http.send("POST", "/api/auth/oauth", "{\"provider\":\"GOOGLE\",\"token\":\"good\"}", null);
        assertEquals(200, first.statusCode(), first.body());
        String id = TestHttp.extract(first.body(), "\"id\":(\\d+)");
        assertTrue(first.body().contains("Бат Эрдэнэ"), first.body());

        // Same Google account again -> same user
        HttpResponse<String> second = http.send("POST", "/api/auth/oauth", "{\"provider\":\"GOOGLE\",\"token\":\"good\"}", null);
        assertEquals(id, TestHttp.extract(second.body(), "\"id\":(\\d+)"));
        String token = TestHttp.extract(second.body(), "\"token\"\\s*:\\s*\"([^\"]+)\"");
        assertEquals(200, http.send("GET", "/api/users/me", null, token).statusCode());

        assertEquals(401, http.send("POST", "/api/auth/oauth", "{\"provider\":\"GOOGLE\",\"token\":\"forged\"}", null).statusCode());

        // An e-mail / password account is not taken over by a Google login with the same address
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String taken = "p-" + suffix + "@gmail.com";
        http.send("POST", "/api/auth/register", """
                {"email":"%s","password":"secret123","displayName":"Тест","username":"p_%s"}
                """.formatted(taken, suffix), null);
        when(verifier.verify("other")).thenReturn(Optional.of(new GoogleUser("x-" + suffix, taken, true, "X")));
        assertEquals(409, http.send("POST", "/api/auth/oauth", "{\"provider\":\"GOOGLE\",\"token\":\"other\"}", null).statusCode());
    }
}
