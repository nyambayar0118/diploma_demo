package mn.diploma.routeplanner.group;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Component;

/** Хуваалцсан бүлгийн төлөвлөгөөний санамсаргүй, хугацаатай урилгын токен (АШ24). */
@Component
public class GroupInviteService {

    static final Duration INVITE_TTL = Duration.ofHours(24);
    private final SecureRandom random = new SecureRandom();

    public String createToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Instant expiry() {
        return Instant.now().plus(INVITE_TTL);
    }
}
