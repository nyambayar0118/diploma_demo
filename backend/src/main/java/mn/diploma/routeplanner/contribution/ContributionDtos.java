package mn.diploma.routeplanner.contribution;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import mn.diploma.routeplanner.common.PriceLevel;

/** Request and response bodies of the Contribution module API. */
public final class ContributionDtos {

    private ContributionDtos() {
    }

    public enum LinkType { WEBSITE, FACEBOOK, INSTAGRAM, REVIEW_ARTICLE, OTHER }

    /**
     * Proposed values; only the fields that change are given. CREATE_PLACE needs name, category and position;
     * ADD_LINK needs url and linkType; permanentlyClosed = true reports a place that no longer exists.
     */
    public record ProposedData(
            @Size(max = 255) String nameCyrillic,
            @Size(max = 255) String nameLatin,
            @Size(max = 40) String categoryCode,
            @DecimalMin("41.5") @DecimalMax("52.2") Double lat,      // Mongolia's bounding box
            @DecimalMin("87.7") @DecimalMax("119.95") Double lng,
            @Size(max = 100) String phone,
            @Size(max = 500) String openingHours,
            @Size(max = 255) String street,
            PriceLevel priceLevel,
            Boolean permanentlyClosed,
            @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "http(s):// хаяг байх ёстой") String url,
            LinkType linkType,
            @Size(max = 200) String linkTitle) {
    }

    public record ChangeRequestDto(@NotNull RequestType type, Long targetPlaceId, @NotNull @Valid ProposedData data) {
    }

    public record ReviewDecision(@Size(max = 500) String comment) {
    }

    public record PlaceSnapshot(long id, String name, String nameLatin, String category, String categoryName,
                                Double lat, Double lng, String phone, String openingHours, String street,
                                String priceLevel, boolean active) {
    }

    public record ChangeRequestView(long id, RequestType type, RequestStatus status, ProposedData data,
                                    PlaceSnapshot target, Long submitterId, String submitterName,
                                    String moderatorComment, Instant submittedAt, Instant reviewedAt,
                                    Integer pointsGranted) {
    }

    public record RewardView(long id, int points, String reason, Instant grantedAt, Long requestId) {
    }

    public record RewardsView(int total, java.util.List<RewardView> items) {
    }
}
