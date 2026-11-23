package mn.diploma.routeplanner.auth;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Нэвтрэлтийн модулийн API-ийн хүсэлт, хариуны бүтэц. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.]{3,30}",
                    message = "3-30 latin letters, digits, '_' or '.'") String username) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserDto(Long id, String email, String username, String displayName, String role,
                          int rewardPoints) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getUsername(), u.getDisplayName(), u.getRole().name(),
                    u.getRewardPoints());
        }
    }

    public record AuthResponse(String token, UserDto user) {
    }

    /** {@code token}: GOOGLE-ийн хувьд Google Identity Services-ээс авсан ID токен ("credential"). */
    public record OAuthLoginRequest(@NotNull AuthProvider provider, @NotBlank String token) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    /** Клиентэд өгөх нийтийн тохиргоо (Google client id нь нууц биш). */
    public record AuthConfig(String googleClientId) {
    }

    public record PreferencesDto(
            @NotEmpty Set<TransportMode> transportModes,
            @NotNull PriceLevel budgetLevel,
            @Min(500) @Max(50000) int searchRadiusM,
            @Min(30) @Max(720) int timeBudgetMin,
            @Min(1) @Max(4) int stopCount) {
        static PreferencesDto of(UserPreferences p) {
            return new PreferencesDto(p.getTransportModes(), p.getBudgetLevel(), p.getSearchRadiusM(),
                    p.getTimeBudgetMin(), p.getStopCount());
        }
    }

    public record ConsentDto(String dataType, String purpose, int retentionDays, boolean granted) {
    }
}
