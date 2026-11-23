package mn.diploma.routeplanner.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
    }

    public static User withEmail(String email, String passwordHash, String displayName, String username) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        u.displayName = displayName;
        u.username = username;
        u.authProvider = AuthProvider.EMAIL;
        return u;
    }

    public static User withGoogle(String email, String subject, String displayName, String username) {
        User u = new User();
        u.email = email;
        u.displayName = displayName;
        u.username = username;
        u.authProvider = AuthProvider.GOOGLE;
        u.providerUserId = subject;
        return u;
    }

    /** ХШ08: хувийн мэдээллийг устгана; нэргүй түүхийн гадаад түлхүүрүүд хадгалагдахын тулд мөр үлдэнэ. */
    void anonymise(Instant now) {
        email = "deleted-" + id + "@deleted.invalid";
        passwordHash = null;
        displayName = "Устгасан хэрэглэгч";
        username = "deleted_" + id;
        providerUserId = null;
        role = Role.USER;
        deletedAt = now;
    }

    void setRole(Role role) {
        this.role = role;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getUsername() { return username; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public Role getRole() { return role; }
    public int getRewardPoints() { return rewardPoints; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deletedAt != null; }
}
