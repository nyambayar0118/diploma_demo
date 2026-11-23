package mn.diploma.routeplanner.routing;

import java.time.Instant;
import java.time.LocalDateTime;

/** Маршрутын нэг газар, дараалал болон ирэх цагийн тооцооны хамт. */
public class RouteStop {

    /** Зогсоолыг төлөвлөх, харуулахад хэрэгтэй газрын мэдээлэл. */
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

    /** Хэрэглэгч "Очсон уу?" асуултад хариулсан (тийм эсвэл үгүй). */
    boolean isAnswered() {
        return visited || skippedAt != null;
    }

    LatLng location() {
        return new LatLng(place.lat(), place.lng());
    }
}
