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

/** Мэдээллийн төрөл бүрд нэг зөвшөөрөл; цуцлахад мөрийг (revokedAt) түүх болгон үлдээнэ. */
@Entity
@Table(name = "privacy_consents")
public class PrivacyConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private ConsentDataType dataType;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PrivacyConsent() {
    }

    public static PrivacyConsent grant(Long userId, ConsentDataType type) {
        PrivacyConsent c = new PrivacyConsent();
        c.userId = userId;
        c.dataType = type;
        c.purpose = type.purpose();
        c.retentionDays = type.retentionDays();
        return c;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public ConsentDataType getDataType() { return dataType; }
    public Instant getGrantedAt() { return grantedAt; }
}
