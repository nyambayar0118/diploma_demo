package mn.diploma.routeplanner.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, Long> {

    List<PrivacyConsent> findByUserIdAndRevokedAtIsNull(Long userId);

    Optional<PrivacyConsent> findByUserIdAndDataTypeAndRevokedAtIsNull(Long userId, ConsentDataType dataType);
}
