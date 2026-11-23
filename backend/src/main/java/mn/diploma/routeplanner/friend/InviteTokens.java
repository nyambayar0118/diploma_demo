package mn.diploma.routeplanner.friend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Гарын үсэгтэй, хугацаатай урилгын токен ("<purpose>.<id>.<expiry>.<signature>"); тул урилгын холбоосонд хүснэгт хэрэггүй.
 * Түлхүүрийг JWT нууц түлхүүрээс зорилгын угтвартайгаар гаргадаг тул урилгыг нэвтрэх токен болгон ашиглах боломжгүй.
 */
@Component
public class InviteTokens {

    private final byte[] key;

    public InviteTokens(@Value("${app.jwt.secret}") String secret) {
        this.key = ("invite:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String purpose, long id, Duration ttl) {
        long expires = Instant.now().plus(ttl).getEpochSecond();
        String payload = purpose + "." + id + "." + expires;
        return payload + "." + sign(payload);
    }

    /** Энэ зорилгын хүчинтэй, хугацаа нь дуусаагүй токен доторх id. */
    public Optional<Long> verify(String purpose, String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 4 || !parts[0].equals(purpose)) return Optional.empty();
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            if (Instant.now().getEpochSecond() > Long.parseLong(parts[2])) return Optional.empty();
            return Optional.of(Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Instant expiresAt(String token) {
        return Instant.ofEpochSecond(Long.parseLong(token.split("\\.")[2]));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
