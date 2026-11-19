package mn.diploma.routeplanner.place;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.place.ReviewService.ReviewDto;

/** Reviews API (design table "Газрын модулийн API"); /api/moderation/** needs MODERATOR or ADMIN. */
@RestController
public class ReviewController {

    public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    }

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/places/{id}/reviews")
    public List<ReviewDto> list(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        Long viewer = jwt == null ? null : JwtService.userId(jwt);
        return reviews.list(id, viewer, isModerator(jwt));
    }

    @PostMapping("/api/places/{id}/reviews")
    public ReviewDto write(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ReviewRequest req) {
        return reviews.write(JwtService.userId(jwt), id, req.rating(), req.comment());
    }

    @DeleteMapping("/api/places/{id}/reviews/mine")
    public ResponseEntity<Void> deleteMine(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        reviews.deleteOwn(JwtService.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/moderation/reviews/{id}/hide")
    public ReviewDto hide(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return reviews.setHidden(JwtService.userId(jwt), id, true);
    }

    @PostMapping("/api/moderation/reviews/{id}/unhide")
    public ReviewDto unhide(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return reviews.setHidden(JwtService.userId(jwt), id, false);
    }

    static boolean isModerator(Jwt jwt) {
        if (jwt == null) return false;
        String role = jwt.getClaimAsString("role");
        return "MODERATOR".equals(role) || "ADMIN".equals(role);
    }
}
