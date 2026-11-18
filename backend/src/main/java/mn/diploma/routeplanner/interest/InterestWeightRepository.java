package mn.diploma.routeplanner.interest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestWeightRepository extends JpaRepository<InterestWeight, Long> {

    List<InterestWeight> findByUserId(Long userId);

    java.util.Optional<InterestWeight> findByUserIdAndCategoryId(Long userId, Long categoryId);
}
