package mn.diploma.routeplanner.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.AuthDtos.ConsentDto;

/** Нэвтрэлтийн модулийн нийтийн сервис: бусад модуль хувийн мэдээлэл ашиглахаасаа өмнө зөвшөөрлийг эндээс шалгана (ХШ05). */
@Service
public class ConsentService {

    private final PrivacyConsentRepository consents;

    public ConsentService(PrivacyConsentRepository consents) {
        this.consents = consents;
    }

    @Transactional(readOnly = true)
    public boolean isGranted(Long userId, ConsentDataType type) {
        return consents.findByUserIdAndDataTypeAndRevokedAtIsNull(userId, type).isPresent();
    }

    @Transactional(readOnly = true)
    public List<ConsentDto> list(Long userId) {
        Set<ConsentDataType> granted = consents.findByUserIdAndRevokedAtIsNull(userId).stream()
                .map(PrivacyConsent::getDataType).collect(Collectors.toSet());
        return Arrays.stream(ConsentDataType.values())
                .map(t -> new ConsentDto(t.name(), t.purpose(), t.retentionDays(), granted.contains(t)))
                .toList();
    }

    @Transactional
    public void grant(Long userId, ConsentDataType type) {
        if (!isGranted(userId, type)) {
            consents.save(PrivacyConsent.grant(userId, type));
        }
    }

    @Transactional
    public void revoke(Long userId, ConsentDataType type) {
        consents.findByUserIdAndDataTypeAndRevokedAtIsNull(userId, type).ifPresent(PrivacyConsent::revoke);
    }
}
