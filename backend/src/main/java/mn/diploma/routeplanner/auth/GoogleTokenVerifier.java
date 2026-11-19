package mn.diploma.routeplanner.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies the ID token that Google Identity Services gives the browser ("Sign in with Google", АШ01):
 * RS256 signature against Google's public keys, issuer, expiry and that it was issued for our client id.
 */
@Component
public class GoogleTokenVerifier {

    /** What we use from a verified Google ID token. */
    public record GoogleUser(String subject, String email, boolean emailVerified, String name) {
    }

    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder;

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    public boolean isEnabled() {
        return !clientId.isBlank();
    }

    public String clientId() {
        return clientId;
    }

    /** The user if the token is valid, empty otherwise. */
    public Optional<GoogleUser> verify(String idToken) {
        if (!isEnabled()) return Optional.empty();
        try {
            Jwt jwt = decoder().decode(idToken);
            return Optional.of(new GoogleUser(jwt.getSubject(), jwt.getClaimAsString("email"),
                    Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")), jwt.getClaimAsString("name")));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    private JwtDecoder decoder() {
        if (decoder == null) {
            NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
            OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong issuer", null));
            OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong audience", null));
            d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
            decoder = d;
        }
        return decoder;
    }
}
