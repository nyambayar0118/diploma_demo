package mn.diploma.routeplanner.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.AuthDtos.AuthResponse;
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

    public AuthService(UserRepository users, UserPreferencesRepository preferences,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
