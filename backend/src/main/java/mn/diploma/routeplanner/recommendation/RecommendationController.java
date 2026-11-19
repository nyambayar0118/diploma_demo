package mn.diploma.routeplanner.recommendation;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;
import mn.diploma.routeplanner.recommendation.RecommendationService.Batch;

/** Recommendation module API (design table "Санал болголтын модулийн API"). */
@RestController
public class RecommendationController {

    /** Missing fields fall back to the user's preferences; {@code at} is for planning a later time (АШ25). */
    public record RecommendationRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Min(30) @Max(720) Integer availableMinutes,
            PriceLevel budgetLevel,
            TransportMode transportMode,
            @Min(1) @Max(30) Integer k,
            LocalDateTime at,
            /** Main or subcategory codes ("food", "museum"); empty or missing = all. */
            @jakarta.validation.constraints.Size(max = 40) List<@jakarta.validation.constraints.Size(max = 40) String> categories) {
    }

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping("/api/recommendations")
    public Batch recommend(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecommendationRequest req) {
        return recommendations.recommend(JwtService.userId(jwt), new RecommendationService.Request(
                req.lat(), req.lng(), req.availableMinutes(), req.budgetLevel(), req.transportMode(), req.k(),
                req.at(), req.categories()));
    }
}
