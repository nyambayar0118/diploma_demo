package mn.diploma.routeplanner.routing;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.routing.RoutingDtos.CreateRouteRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.FeedbackRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.ModifyStopsRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.PendingVisit;
import mn.diploma.routeplanner.routing.RoutingDtos.PositionRequest;
import mn.diploma.routeplanner.routing.RoutingDtos.RouteView;
import mn.diploma.routeplanner.routing.RoutingDtos.VisitResult;

/** Routing module API (design table "Маршрутын модулийн API"). */
@RestController
public class RouteController {

    private final RoutingService routing;
    private final VisitService visits;
    private final FeedbackService feedback;

    public RouteController(RoutingService routing, VisitService visits, FeedbackService feedback) {
        this.routing = routing;
        this.visits = visits;
        this.feedback = feedback;
    }

    @PostMapping("/api/routes")
    public ResponseEntity<RouteView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRouteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routing.createRoute(JwtService.userId(jwt), req));
    }

    /** The user's latest routes, newest first. */
    @GetMapping("/api/routes")
    public List<RouteView> list(@AuthenticationPrincipal Jwt jwt) {
        return routing.list(JwtService.userId(jwt));
    }

    @GetMapping("/api/routes/{id}")
    public RouteView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return routing.get(JwtService.userId(jwt), id);
    }

    @PatchMapping("/api/routes/{id}/stops")
    public RouteView modifyStops(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                 @Valid @RequestBody ModifyStopsRequest req) {
        return routing.modifyStops(JwtService.userId(jwt), id, req);
    }

    @PostMapping("/api/routes/{id}/open")
    public RouteView open(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return routing.openInMaps(JwtService.userId(jwt), id);
    }

    @PostMapping("/api/routes/{id}/complete")
    public RouteView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return routing.complete(JwtService.userId(jwt), id);
    }

    @DeleteMapping("/api/routes/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        routing.cancel(JwtService.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/routes/{id}/feedback")
    public ResponseEntity<Void> feedback(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                         @Valid @RequestBody FeedbackRequest req) {
        feedback.submit(JwtService.userId(jwt), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 200 with the question, or 204 when there is nothing to ask. */
    @GetMapping("/api/visits/pending")
    public ResponseEntity<PendingVisit> pending(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) Double lat,
                                                @RequestParam(required = false) Double lng) {
        return visits.pendingQuestion(JwtService.userId(jwt), lat, lng, VisitService.now())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/visits/{stopId}/confirm")
    public VisitResult confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long stopId,
                               @Valid @RequestBody(required = false) PositionRequest position) {
        Double lat = position == null ? null : position.lat();
        Double lng = position == null ? null : position.lng();
        return visits.confirmVisit(JwtService.userId(jwt), stopId, lat, lng);
    }

    @PostMapping("/api/visits/{stopId}/skip")
    public VisitResult skip(@AuthenticationPrincipal Jwt jwt, @PathVariable long stopId) {
        return visits.skipQuestion(JwtService.userId(jwt), stopId);
    }
}
