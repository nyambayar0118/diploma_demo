package mn.diploma.routeplanner.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.AuthDtos.AuthResponse;
import mn.diploma.routeplanner.auth.GoogleTokenVerifier.GoogleUser;
import mn.diploma.routeplanner.auth.AuthDtos.LoginRequest;
import mn.diploma.routeplanner.auth.AuthDtos.PreferencesDto;
import mn.diploma.routeplanner.auth.AuthDtos.RegisterRequest;
import mn.diploma.routeplanner.auth.AuthDtos.UserDto;
import mn.diploma.routeplanner.common.ApiException;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier google;

    public AuthService(UserRepository users, UserPreferencesRepository preferences,
                       PasswordEncoder passwordEncoder, JwtService jwtService, GoogleTokenVerifier google) {
        this.users = users;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.google = google;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String username = req.username().trim();
        if (users.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Энэ имэйлээр бүртгэл үүссэн байна");
        }
        if (users.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Энэ хэрэглэгчийн нэр аль хэдийн байна");
        }
        User user = users.save(User.withEmail(email, passwordEncoder.encode(req.password()),
                req.displayName().trim(), username));
        preferences.save(UserPreferences.defaultsFor(user.getId()));
        return new AuthResponse(jwtService.issue(user), UserDto.of(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(req.email().trim())
                .filter(u -> u.getAuthProvider() == AuthProvider.EMAIL)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Имэйл эсвэл нууц үг буруу байна"));
        return new AuthResponse(jwtService.issue(user), UserDto.of(user));
    }

    /**
     * "Sign in with Google": logs in the account linked to the Google user, or creates one on first use.
     * An existing e-mail / password account with the same address is not taken over silently.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String credential) {
        if (!google.isEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GOOGLE_DISABLED", "Google-ээр нэвтрэх тохируулагдаагүй байна");
        }
        GoogleUser g = google.verify(credential)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                        "Google-ийн баталгаажуулалт амжилтгүй боллоо"));
        if (g.email() == null || !g.emailVerified()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED", "Google бүртгэлийн имэйл баталгаажаагүй байна");
        }
        User user = users.findByAuthProviderAndProviderUserIdAndDeletedAtIsNull(AuthProvider.GOOGLE, g.subject())
                .orElseGet(() -> createGoogleUser(g));
        return new AuthResponse(jwtService.issue(user), UserDto.of(user));
    }

    private User createGoogleUser(GoogleUser g) {
        String email = g.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_REGISTERED_WITH_PASSWORD",
                    "Энэ имэйлээр нууц үгтэй бүртгэл байна. Имэйл, нууц үгээрээ нэвтэрнэ үү.");
        }
        String name = g.name() == null || g.name().isBlank() ? email.substring(0, email.indexOf('@')) : g.name().trim();
        User user = users.save(User.withGoogle(email, g.subject(), name.length() > 100 ? name.substring(0, 100) : name,
                uniqueUsername(email)));
        preferences.save(UserPreferences.defaultsFor(user.getId()));
        return user;
    }

    /** "bat.erdene@gmail.com" -> "bat_erdene", then "bat_erdene2", ... until free. */
    private String uniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() < 3) base = base + "_user";
        if (base.length() > 25) base = base.substring(0, 25);
        String candidate = base;
        for (int i = 2; users.existsByUsernameIgnoreCaseAndDeletedAtIsNull(candidate); i++) {
            candidate = base + i;
        }
        return candidate;
    }

    public AuthDtos.AuthConfig config() {
        return new AuthDtos.AuthConfig(google.isEnabled() ? google.clientId() : null);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.of(activeUser(userId));
    }

    @Transactional(readOnly = true)
    public PreferencesDto preferences(Long userId) {
        return PreferencesDto.of(preferencesOf(userId));
    }

    @Transactional
    public PreferencesDto updatePreferences(Long userId, PreferencesDto dto) {
        UserPreferences p = preferencesOf(userId);
        p.update(dto.transportModes(), dto.budgetLevel(), dto.searchRadiusM(), dto.timeBudgetMin(), dto.stopCount());
        return PreferencesDto.of(p);
    }

    private User activeUser(Long userId) {
        return users.findById(userId).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Хэрэглэгч олдсонгүй"));
    }

    private UserPreferences preferencesOf(Long userId) {
        activeUser(userId);
        return preferences.findById(userId).orElseGet(() -> preferences.save(UserPreferences.defaultsFor(userId)));
    }
}
