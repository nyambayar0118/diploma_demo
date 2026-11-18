package mn.diploma.routeplanner.interest;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingSurveyRepository extends JpaRepository<OnboardingSurvey, Long> {

    Optional<OnboardingSurvey> findByUserId(Long userId);
}
