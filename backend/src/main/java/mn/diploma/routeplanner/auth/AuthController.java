package mn.diploma.routeplanner.auth;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.AuthDtos.AuthResponse;
import mn.diploma.routeplanner.auth.AuthDtos.ConsentDto;
import mn.diploma.routeplanner.auth.AuthDtos.LoginRequest;
import mn.diploma.routeplanner.auth.AuthDtos.PreferencesDto;
import mn.diploma.routeplanner.auth.AuthDtos.RegisterRequest;
import mn.diploma.routeplanner.auth.AuthDtos.UserDto;
import mn.diploma.routeplanner.common.ApiException;

/** Нэвтрэлтийн модулийн API (зохиомжийн "Хэрэглэгч ба нууцлалын модулиудын API" хүснэгт). */
@RestController
public class AuthController {

    private final AuthService auth;
    private final ConsentService consents;

    public AuthController(AuthService auth, ConsentService consents) {
        this.auth = auth;
        this.consents = consents;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(req));
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    /** Гуравдагч талын хаягаар нэвтрэх (АШ07). Зөвхөн GOOGLE хэрэгжсэн; FACEBOOK бол 400 буцаана. */
    @PostMapping("/api/auth/oauth")
    public AuthResponse oauth(@Valid @RequestBody AuthDtos.OAuthLoginRequest req) {
        if (req.provider() != AuthProvider.GOOGLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_NOT_SUPPORTED", req.provider() + " login is not available");
        }
        return auth.loginWithGoogle(req.token());
    }

    /** ХШ08: өөрийн бүртгэл болон хувийн мэдээллээ устгах. */
    @DeleteMapping("/api/users/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt) {
        auth.deleteAccount(JwtService.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/users/{id}/role")
    public UserDto changeRole(@PathVariable Long id, @Valid @RequestBody AuthDtos.RoleRequest req) {
        return auth.changeRole(id, req.role());
    }

    /** Нэвтрэх хуудсанд хэрэгтэй нийтийн тохиргоо (Google client id; Google-ээр нэвтрэх унтраастай бол null). */
    @GetMapping("/api/auth/config")
    public AuthDtos.AuthConfig config() {
        return auth.config();
    }

    @GetMapping("/api/users/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(JwtService.userId(jwt));
    }

    @GetMapping("/api/users/me/preferences")
    public PreferencesDto preferences(@AuthenticationPrincipal Jwt jwt) {
        return auth.preferences(JwtService.userId(jwt));
    }

    @PutMapping("/api/users/me/preferences")
    public PreferencesDto updatePreferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesDto dto) {
        return auth.updatePreferences(JwtService.userId(jwt), dto);
    }

    @GetMapping("/api/consents")
    public List<ConsentDto> consents(@AuthenticationPrincipal Jwt jwt) {
        return consents.list(JwtService.userId(jwt));
    }

    @PostMapping("/api/consents/{type}")
    public List<ConsentDto> grant(@AuthenticationPrincipal Jwt jwt, @PathVariable String type) {
        consents.grant(JwtService.userId(jwt), parse(type));
        return consents.list(JwtService.userId(jwt));
    }

    @DeleteMapping("/api/consents/{type}")
    public List<ConsentDto> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String type) {
        consents.revoke(JwtService.userId(jwt), parse(type));
        return consents.list(JwtService.userId(jwt));
    }

    private static ConsentDataType parse(String type) {
        try {
            return ConsentDataType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CONSENT_TYPE", "Unknown consent type: " + type);
        }
    }
}
