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

/** Маршрутын модулийн API-ийн хүсэлт, хариуны бүтэц. */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record StopRequest(@NotNull Long placeId, Long recommendationId) {
    }

    /**
     * Зогсоол өгөөгүй бол сервер шилдэг санал болголтоос 2-3-ыг өөрөө сонгоно (автомат маршрут).
     * Тээвэр / төсөв өгөөгүй бол хэрэглэгчийн тохиргоог ашиглана; {@code startAt} нь дараа цагийг төлөвлөнө (орон нутгийн цаг).
     */
    public record CreateRouteRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            TransportMode transportMode,
            @Min(30) @Max(720) Integer timeBudgetMinutes,
            LocalDateTime startAt,
            @Size(max = RoutingService.MAX_STOPS) List<@Valid StopRequest> stops,
            /** Зөвхөн автомат маршрутад: сонгох газрыг эдгээр үндсэн эсвэл дэд ангиллаар хязгаарлана. */
            @Size(max = 40) List<@Size(max = 40) String> categories) {
    }

    public enum StopOperation { ADD, REMOVE, REPLACE, REORDER, OPTIMIZE }

    /**
     * ADD: placeId (+ recommendationId); REMOVE: stopId; REPLACE: stopId болон шинэ placeId;
     * REORDER: хэрэглэгчийн хүссэн дараалал дахь stopIds (өгснөөр нь хадгална); OPTIMIZE: хамгийн богино дараалал руу буцаана.
     */
    public record ModifyStopsRequest(@NotNull StopOperation op, Long stopId, Long placeId, Long recommendationId,
                                     @Size(max = RoutingService.MAX_STOPS) List<Long> stopIds) {
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
                            boolean feedbackGiven, Long groupSessionId, boolean shortestOrder) {
    }

    /** "Очсон уу?" асуулт: асуух зогсоол болон хэрэглэгч түүний ойролцоо байгаа эсэх. */
    public record PendingVisit(long stopId, long routeId, int stopOrder, int stopCount, long placeId, String name,
                               boolean nearby) {
    }

    public record VisitResult(long stopId, boolean visited, long routeId, RouteStatus routeStatus) {
    }
}
