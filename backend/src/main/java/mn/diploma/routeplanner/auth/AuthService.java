package mn.diploma.routeplanner.auth;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

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
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    /** Бүртгүүлэх эсвэл нэвтрэх үед ADMIN эрх авах имэйлүүд (ADMIN_EMAILS орчны хувьсагч, таслалаар тусгаарлана). */
    private final Set<String> adminEmails;

    public AuthService(UserRepository users, UserPreferencesRepository preferences,
                       PasswordEncoder passwordEncoder, JwtService jwtService, GoogleTokenVerifier google,
                       ApplicationEventPublisher events, JdbcTemplate jdbc,
                       @Value("${app.admin-emails:}") String adminEmails) {
        this.users = users;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.google = google;
        this.events = events;
        this.jdbc = jdbc;
        this.adminEmails = Arrays.stream(adminEmails.split(",")).map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty()).collect(Collectors.toSet());
    }

    private AuthResponse issue(User user) {
        if (user.getRole() != Role.ADMIN && adminEmails.contains(user.getEmail().toLowerCase(Locale.ROOT))) {
            user.setRole(Role.ADMIN);
            users.save(user);
        }
        return new AuthResponse(jwtService.issue(user), UserDto.of(user));
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
        return issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(req.email().trim())
                .filter(u -> u.getAuthProvider() == AuthProvider.EMAIL)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Имэйл эсвэл нууц үг буруу байна"));
        return issue(user);
    }

    /**
     * "Google-ээр нэвтрэх": Google хэрэглэгчтэй холбосон бүртгэлээр нэвтрүүлэх, анх удаа бол шинээр үүсгэнэ.
     * Ижил имэйлтэй, имэйл ба нууц үгээр үүсгэсэн бүртгэлийг санамсаргүйгээр нэгтгэхгүй.
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
        return issue(user);
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

    /** "bat.erdene@gmail.com" -> "bat_erdene", давхцвал "bat_erdene2", ... гэх мэтээр сул нэр олтол. */
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

    /** ХШ08: бүх модуль дахь бүртгэлийн хувийн мэдээллийг устгаж, нэргүй болгосон түүхийг үлдээнэ. */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = activeUser(userId);
        events.publishEvent(new UserDeletedEvent(userId));
        jdbc.update("DELETE FROM privacy_consents WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_preferences WHERE user_id = ?", userId);
        jdbc.update("UPDATE users SET home_location = NULL, reward_points = 0 WHERE id = ?", userId);
        user.anonymise(Instant.now());
        users.save(user);
    }

    /** Администратор: модераторын эрх олгох эсвэл хасах. */
    @Transactional
    public UserDto changeRole(Long userId, Role role) {
        User user = activeUser(userId);
        user.setRole(role);
        return UserDto.of(users.save(user));
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
