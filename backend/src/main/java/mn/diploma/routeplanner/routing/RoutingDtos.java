package mn.diploma.routeplanner.routing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import mn.diploma.routeplanner.common.TransportMode;
import mn.diploma.routeplanner.place.OpeningHours;

/** Request and response bodies of the Routing module API. */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record StopRequest(@NotNull Long placeId, Long recommendationId) {
    }

    /**
     * Without stops the server picks 2-3 of the top recommendations itself (automatic route).
     * Missing mode / budget fall back to the user's preferences; {@code startAt} plans a later time (local time).
     */
    public record CreateRouteRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            TransportMode transportMode,
            @Min(30) @Max(720) Integer timeBudgetMinutes,
            LocalDateTime startAt,
            @Size(max = RoutingService.MAX_STOPS) List<@Valid StopRequest> stops) {
    }

    public enum StopOperation { ADD, REMOVE, REPLACE }

    /** ADD: placeId (+ recommendationId); REMOVE: stopId; REPLACE: stopId and the new placeId. */
    public record ModifyStopsRequest(@NotNull StopOperation op, Long stopId, Long placeId, Long recommendationId) {
    }

    public record FeedbackRequest(@NotNull @Min(1) @Max(5) Integer relevance,
                                  @NotNull @Min(1) @Max(5) Integer timeSuitability,
                                  @NotNull @Min(1) @Max(5) Integer estimateAccuracy) {
    }

    public record PositionRequest(@DecimalMin("-90") @DecimalMax("90") Double lat,
                                  @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record StopView(long stopId, int order, long placeId, String name, String nameLatin, String category,
                           String categoryName, String mainCategory, double lat, double lng, int travelMinutes,
                           LocalDateTime estimatedArrival, int stayMinutes, OpeningHours.Status openAtArrival,
                           boolean visited, boolean skipped) {
    }

    public record RouteView(long id, RouteStatus status, TransportMode transportMode, double startLat, double startLng,
                            LocalDateTime plannedStart, int timeBudgetMinutes, int estimatedDurationMinutes,
                            boolean overBudget, Instant createdAt, List<StopView> stops, List<MapsLink> mapsLinks,
                            boolean feedbackGiven, Long groupSessionId) {
    }

    /** The "Очсон уу?" question: the stop to ask about and whether the user is standing near it. */
    public record PendingVisit(long stopId, long routeId, int stopOrder, int stopCount, long placeId, String name,
                               boolean nearby) {
    }

    public record VisitResult(long stopId, boolean visited, long routeId, RouteStatus routeStatus) {
    }
}
