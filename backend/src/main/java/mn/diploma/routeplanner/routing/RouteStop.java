package mn.diploma.routeplanner.routing;

import java.time.Instant;
import java.time.LocalDateTime;

/** One place of a route, with its order and estimated arrival. */
public class RouteStop {

    /** Place data needed to plan and show the stop. */
    public record PlaceInfo(long id, String name, String nameLatin, String category, String categoryName,
                            String mainCategory, double lat, double lng, String openingHours) {
    }

    Long id;
    final PlaceInfo place;
    final Long recommendationId;
    int stopOrder;
    LocalDateTime estimatedArrival;
    final int stayMinutes;
    boolean visited;
    Instant visitedAt;
    Instant skippedAt;

    RouteStop(PlaceInfo place, Long recommendationId, int stayMinutes) {
        this.place = place;
        this.recommendationId = recommendationId;
        this.stayMinutes = stayMinutes;
    }

    void markVisited(Instant time) {
        visited = true;
        visitedAt = time;
    }

    /** The user answered "Очсон уу?" (yes or no). */
    boolean isAnswered() {
        return visited || skippedAt != null;
    }

    LatLng location() {
        return new LatLng(place.lat(), place.lng());
    }
}
