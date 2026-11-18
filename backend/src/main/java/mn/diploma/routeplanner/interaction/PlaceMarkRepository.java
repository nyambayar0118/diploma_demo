package mn.diploma.routeplanner.interaction;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceMarkRepository extends JpaRepository<PlaceMark, Long> {

    Optional<PlaceMark> findByUserIdAndPlaceId(Long userId, Long placeId);

    List<PlaceMark> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, MarkType type);
}
