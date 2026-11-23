package mn.diploma.routeplanner.interaction;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.common.ApiException;

/** Үйлдлийн модулийн API (зохиомжийн "Сонирхлын загварчлалын модулиудын API" хүснэгт). */
@RestController
public class InteractionController {

    public record MarkRequest(@NotNull MarkType type) {
    }

    public record InteractionRequest(@NotNull Long placeId, @NotNull InteractionType type,
                                     Long recommendationId, Long routeId, Double lat, Double lng) {
    }

    private final PlaceMarkService marks;
    private final InteractionService interactions;

    public InteractionController(PlaceMarkService marks, InteractionService interactions) {
        this.marks = marks;
        this.interactions = interactions;
    }

    /** Нэвтэрсэн хэрэглэгчийн хувьд газрын тэмдэглэгээ; зочин эсвэл тэмдэглээгүй газарт {"type": null}. */
    @GetMapping("/api/places/{id}/mark")
    public Map<String, Object> mark(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        Object type = jwt == null ? null : marks.markOf(JwtService.userId(jwt), id).map(Enum::name).orElse(null);
        return java.util.Collections.singletonMap("type", type);
    }

    @PutMapping("/api/places/{id}/mark")
    public Map<String, Object> setMark(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                       @Valid @RequestBody MarkRequest req) {
        return Map.of("type", marks.mark(JwtService.userId(jwt), id, req.type()).name());
    }

    @DeleteMapping("/api/places/{id}/mark")
    public ResponseEntity<Void> removeMark(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        marks.unmark(JwtService.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/api/users/me/saved", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saved(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(marks.savedPlacesAsJson(JwtService.userId(jwt)));
    }

    /** Хэрэглэгчийн санал болголтоос нуусан газрууд (дахин харуулах боломжтой). */
    @GetMapping(value = "/api/users/me/hidden", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hidden(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(marks.hiddenPlacesAsJson(JwtService.userId(jwt)));
    }

    /** Клиентээс мэдээлсэн үйлдэл (VIEW, OPEN_IN_MAPS, ACCEPT, REJECT); 202 буцаана (СГШ08). */
    @PostMapping("/api/interactions")
    public ResponseEntity<Void> log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InteractionRequest req) {
        if (!req.type().reportableByClient()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TYPE_NOT_ALLOWED",
                    req.type() + " is recorded by the server, not by the client");
        }
        interactions.log(JwtService.userId(jwt), req.placeId(), req.type(), req.recommendationId(), req.routeId(),
                req.lat(), req.lng());
        return ResponseEntity.accepted().build();
    }
}
