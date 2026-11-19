package mn.diploma.routeplanner.contribution;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mn.diploma.routeplanner.auth.JwtService;
import mn.diploma.routeplanner.contribution.ContributionDtos.ChangeRequestDto;
import mn.diploma.routeplanner.contribution.ContributionDtos.ChangeRequestView;
import mn.diploma.routeplanner.contribution.ContributionDtos.ReviewDecision;
import mn.diploma.routeplanner.contribution.ContributionDtos.RewardsView;

/** Contribution module API (design table "Хувь нэмэр ба хяналтын модулийн API"). */
@RestController
public class ContributionController {

    private final ContributionService contributions;
    private final RewardService rewards;

    public ContributionController(ContributionService contributions, RewardService rewards) {
        this.contributions = contributions;
        this.rewards = rewards;
    }

    @PostMapping("/api/change-requests")
    public ResponseEntity<ChangeRequestView> submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contributions.submit(JwtService.userId(jwt), dto));
    }

    @GetMapping("/api/users/me/change-requests")
    public List<ChangeRequestView> mine(@AuthenticationPrincipal Jwt jwt) {
        return contributions.listByUser(JwtService.userId(jwt));
    }

    @GetMapping("/api/users/me/rewards")
    public RewardsView rewards(@AuthenticationPrincipal Jwt jwt) {
        return rewards.rewards(JwtService.userId(jwt));
    }

    @GetMapping("/api/moderation/requests")
    public List<ChangeRequestView> queue(@RequestParam(defaultValue = "PENDING") RequestStatus status) {
        return contributions.listByStatus(status);
    }

    @PostMapping("/api/moderation/requests/{id}/approve")
    public ChangeRequestView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                     @Valid @RequestBody(required = false) ReviewDecision decision) {
        return contributions.approve(JwtService.userId(jwt), id, decision == null ? null : decision.comment());
    }

    @PostMapping("/api/moderation/requests/{id}/reject")
    public ChangeRequestView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                    @Valid @RequestBody ReviewDecision decision) {
        return contributions.reject(JwtService.userId(jwt), id, decision.comment());
    }
}
