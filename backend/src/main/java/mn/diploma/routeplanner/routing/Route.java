package mn.diploma.routeplanner.routing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import mn.diploma.routeplanner.common.TransportMode;

/** A route and its ordered stops (tables routes, route_stops); loaded and saved by {@link RouteRepository}. */
public class Route {

    Long id;
    final long ownerId;
    Long groupSessionId;
    final LatLng start;
    final TransportMode transportMode;
    final int timeBudgetMinutes;
    final LocalDateTime plannedStart;
    int estimatedDurationMinutes;
    RouteStatus status = RouteStatus.CREATED;
    Instant createdAt = Instant.now();
    Instant openedAt;
    Instant closedAt;
    List<RouteStop> stops = new ArrayList<>();

    Route(long ownerId, LatLng start, TransportMode transportMode, int timeBudgetMinutes, LocalDateTime plannedStart) {
        this.ownerId = ownerId;
        this.start = start;
        this.transportMode = transportMode;
        this.timeBudgetMinutes = timeBudgetMinutes;
        this.plannedStart = plannedStart;
    }

    /** Stops can be added, removed or replaced only before the route is opened in Google Maps. */
    boolean canModify() {
        return status == RouteStatus.CREATED;
    }

    List<RouteStop> remainingStops() {
        return stops.stream().filter(s -> !s.isAnswered()).toList();
    }

    public Long getId() { return id; }
    public RouteStatus getStatus() { return status; }
}
