package mn.diploma.routeplanner.auth;

import org.springframework.data.jpa.repository.JpaRepository;

/** Хэрэглэгчийн санал болголтын анхдагч тохиргооны репозитори. */
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
}
