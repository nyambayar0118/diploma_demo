package mn.diploma.routeplanner.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    Optional<User> findByAuthProviderAndProviderUserIdAndDeletedAtIsNull(AuthProvider provider, String providerUserId);
}
