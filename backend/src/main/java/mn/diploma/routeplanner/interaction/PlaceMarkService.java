package mn.diploma.routeplanner.interaction;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.place.PlaceQueryRepository;

/** Газар хадгалах / нуух (АШ13). Нуусан газрыг дахин санал болгохгүй. */
@Service
public class PlaceMarkService {

    private final PlaceMarkRepository marks;
    private final InteractionService interactions;
    private final PlaceQueryRepository places;

    public PlaceMarkService(PlaceMarkRepository marks, InteractionService interactions, PlaceQueryRepository places) {
        this.marks = marks;
        this.interactions = interactions;
        this.places = places;
    }

    @Transactional(readOnly = true)
    public Optional<MarkType> markOf(Long userId, Long placeId) {
        return marks.findByUserIdAndPlaceId(userId, placeId).map(PlaceMark::getType);
    }

    @Transactional
    public MarkType mark(Long userId, Long placeId, MarkType type) {
        if (!places.exists(placeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Газар олдсонгүй");
        }
        Optional<PlaceMark> existing = marks.findByUserIdAndPlaceId(userId, placeId);
        if (existing.isPresent() && existing.get().getType() == type) {
            return type;
        }
        existing.ifPresentOrElse(m -> m.change(type), () -> marks.save(new PlaceMark(userId, placeId, type)));
        interactions.log(userId, placeId, type == MarkType.SAVED ? InteractionType.SAVE : InteractionType.HIDE);
        return type;
    }

    @Transactional
    public void unmark(Long userId, Long placeId) {
        marks.findByUserIdAndPlaceId(userId, placeId).ifPresent(m -> {
            marks.delete(m);
            interactions.log(userId, placeId,
                    m.getType() == MarkType.SAVED ? InteractionType.UNSAVE : InteractionType.UNHIDE);
        });
    }

    @Transactional(readOnly = true)
    public List<Long> placeIds(Long userId, MarkType type) {
        return marks.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type).stream().map(PlaceMark::getPlaceId).toList();
    }

    @Transactional(readOnly = true)
    public String savedPlacesAsJson(Long userId) {
        return places.summariesAsJson(placeIds(userId, MarkType.SAVED));
    }

    @Transactional(readOnly = true)
    public String hiddenPlacesAsJson(Long userId) {
        return places.summariesAsJson(placeIds(userId, MarkType.HIDDEN));
    }
}
