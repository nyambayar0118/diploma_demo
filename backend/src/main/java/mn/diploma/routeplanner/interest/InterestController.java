package mn.diploma.routeplanner.interest;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.ConsentDataType;
import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.interest.InterestProfileService.OnboardingStatus;
import mn.diploma.routeplanner.interest.InterestProfileService.WeightDto;

/** Interest Profile module API (design table "Сонирхлын загварчлалын модулиудын API"). */
@RestController
public class InterestController {

    public record OnboardingRequest(@NotNull Set<String> categoryCodes, @NotNull Set<ConsentDataType> consents) {
    }

    private final InterestProfileService interests;

    public InterestController(InterestProfileService interests) {
        this.interests = interests;
    }

    @GetMapping("/api/onboarding")
    public OnboardingStatus status(@AuthenticationPrincipal Jwt jwt) {
        return interests.status(JwtService.userId(jwt));
    }

    @PostMapping("/api/onboarding")
    public OnboardingStatus complete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OnboardingRequest req) {
        return interests.completeOnboarding(JwtService.userId(jwt), req.categoryCodes(), req.consents());
    }

    @GetMapping("/api/users/me/interests")
    public List<WeightDto> weights(@AuthenticationPrincipal Jwt jwt) {
        return interests.weights(JwtService.userId(jwt));
    }
}
