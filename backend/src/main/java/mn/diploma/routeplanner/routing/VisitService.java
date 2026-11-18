package mn.diploma.routeplanner.routing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.interaction.InteractionService;
import mn.diploma.routeplanner.interaction.InteractionType;
import mn.diploma.routeplanner.recommendation.RecommendationService;
import mn.diploma.routeplanner.routing.RoutingDtos.PendingVisit;
import mn.diploma.routeplanner.routing.RoutingDtos.VisitResult;

/**
 * Visit confirmation (АШ23). Google Maps does the navigation, so the system cannot see whether the user
 * arrived: when the app is opened again it asks "Очсон уу?" about a stop whose estimated arrival has passed,
 * or about the stop the user is standing near. Only confirmed visits become VISIT labels.
 */
@Service
public class VisitService {

    static final int NEARBY_RADIUS_M = 150;

    private final RouteRepository routes;
    private final RoutingService routing;
    private final InteractionService interactions;

    public VisitService(RouteRepository routes, RoutingService routing, InteractionService interactions) {
        this.routes = routes;
        this.routing = routing;
        this.interactions = interactions;
    }

    /** The stop to ask about: a nearby one first, otherwise the earliest unanswered one already due. */
    @Transactional(readOnly = true)
    public Optional<PendingVisit> pendingQuestion(long userId, Double lat, Double lng, LocalDateTime now) {
        Optional<PendingVisit> nearby = Optional.empty();
        if (lat != null && lng != null) {
            nearby = checkNearby(userId, new LatLng(lat, lng));
        }
        if (nearby.isPresent()) return nearby;
        for (Route route : routes.findByOwnerAndStatus(userId, RouteStatus.OPENED_IN_MAPS)) {
            Optional<RouteStop> due = route.remainingStops().stream()
                    .filter(s -> !s.estimatedArrival.isAfter(now))
                    .min(Comparator.comparingInt(s -> s.stopOrder));
            if (due.isPresent()) return Optional.of(pending(route, due.get(), false));
        }
        return Optional.empty();
    }

    public Optional<PendingVisit> checkNearby(long userId, LatLng position) {
        for (Route route : routes.findByOwnerAndStatus(userId, RouteStatus.OPENED_IN_MAPS)) {
            for (RouteStop s : route.remainingStops()) {
                double d = TravelTimeEstimator.haversineMeters(position.lat(), position.lng(), s.place.lat(), s.place.lng());
                if (d <= NEARBY_RADIUS_M) return Optional.of(pending(route, s, true));
            }
        }
        return Optional.empty();
    }

    @Transactional
    public VisitResult confirmVisit(long userId, long stopId, Double lat, Double lng) {
        Route route = routeOfStop(userId, stopId);
        RouteStop stop = stop(route, stopId);
        if (!stop.isAnswered()) {
            stop.markVisited(Instant.now());
            routes.updateStop(stop);
            interactions.log(userId, stop.place.id(), InteractionType.VISIT, stop.recommendationId, route.id, lat, lng);
        }
        // Confirming the last stop (or answering every stop) finishes the route
        boolean lastStop = stop.stopOrder == route.stops.size();
        if ((lastStop && stop.visited) || route.remainingStops().isEmpty()) {
            routing.close(route, RouteStatus.COMPLETED);
        }
        return new VisitResult(stop.id, stop.visited, route.id, route.status);
    }

    /** "Үгүй, очоогүй": the stop is not asked about again and gives no VISIT label. */
    @Transactional
    public VisitResult skipQuestion(long userId, long stopId) {
        Route route = routeOfStop(userId, stopId);
        RouteStop stop = stop(route, stopId);
        if (!stop.isAnswered()) {
            stop.skippedAt = Instant.now();
            routes.updateStop(stop);
        }
        if (route.remainingStops().isEmpty()) {
            routing.close(route, RouteStatus.COMPLETED);
        }
        return new VisitResult(stop.id, stop.visited, route.id, route.status);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(RecommendationService.ZONE);
    }

    private Route routeOfStop(long userId, long stopId) {
        Route route = routes.findByStopId(stopId).filter(r -> r.ownerId == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STOP_NOT_FOUND", "Зогсоол олдсонгүй"));
        if (route.status != RouteStatus.OPENED_IN_MAPS) {
            throw new ApiException(HttpStatus.CONFLICT, "ROUTE_NOT_ACTIVE", "Маршрут идэвхтэй биш байна");
        }
        return route;
    }

    private static RouteStop stop(Route route, long stopId) {
        return route.stops.stream().filter(s -> s.id == stopId).findFirst().orElseThrow();
    }

    private static PendingVisit pending(Route route, RouteStop s, boolean nearby) {
        return new PendingVisit(s.id, route.id, s.stopOrder, route.stops.size(), s.place.id(), s.place.name(), nearby);
    }
}
