package mn.diploma.routeplanner.group;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Бүлгийн модулийн API-ийн хүсэлт, хариуны бүтэц. */
public final class GroupDtos {

    private GroupDtos() {
    }

    /** Санаачлагчийн найздаа тэмдэглэсэн эхлэх цэг (найзын хуваалцсан байршлыг орлоно). */
    public record MemberStart(@NotNull Long userId,
                              @DecimalMin("-90") @DecimalMax("90") Double lat,
                              @DecimalMin("-180") @DecimalMax("180") Double lng,
                              TransportMode transportMode) {
    }

    public record CreateGroupRequest(
            @NotNull @Size(min = 1, max = 9) List<Long> friendIds,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            TransportMode transportMode,
            @Min(30) @Max(720) Integer timeBudgetMinutes,
            PriceLevel budgetLevel,
            List<@Valid MemberStart> starts) {
    }

    public enum MemberOperation { ADD, REMOVE, SET_START }

    /** ADD: userId (найз); REMOVE: memberId; SET_START: memberId, lat / lng болон / эсвэл transportMode. */
    public record ModifyMembersRequest(@NotNull MemberOperation op, Long userId, Long memberId,
                                       @DecimalMin("-90") @DecimalMax("90") Double lat,
                                       @DecimalMin("-180") @DecimalMax("180") Double lng,
                                       TransportMode transportMode) {
    }

    public record ShareRequest(@NotNull Long placeId) {
    }

    public record JoinRequest(@Size(max = 100) String guestName,
                              @DecimalMin("-90") @DecimalMax("90") Double lat,
                              @DecimalMin("-180") @DecimalMax("180") Double lng,
                              TransportMode transportMode) {
    }

    public record PlaceRef(long id, String name, String categoryName, String mainCategory, double lat, double lng) {
    }

    /** Эхлэх цэгийн солбилцлыг зөвхөн тухайн гишүүн өөрөө болон санаачлагч харна (зочных харагдахгүй). */
    public record MemberView(long id, MemberRole role, Long userId, String name, boolean guest, StartSource startSource,
                             Double startLat, Double startLng, TransportMode transportMode, boolean usesInterests,
                             Integer travelMinutes, boolean joinedViaLink, String mapsUrl, boolean me) {
    }

    public record GroupView(long id, GroupStatus status, int timeBudgetMinutes, PriceLevel budgetLevel,
                            Instant createdAt, PlaceRef meetingPlace, List<MemberView> members, boolean initiator,
                            String inviteToken, Instant inviteExpiresAt, Long myRouteId) {
    }

    public record OptionView(long recommendationId, PlaceRef place, double score, Map<Long, Integer> travelMinutesByMember,
                             int maxTravelMinutes) {
    }

    public record OptionsView(long batchId, String aggregator, List<OptionView> options) {
    }

    public record JoinInfo(GroupStatus status, String initiatorName, int memberCount, PlaceRef meetingPlace,
                           Instant expiresAt, boolean valid) {
    }

    public record JoinResult(long memberId, String name, PlaceRef meetingPlace, String mapsUrl, Integer travelMinutes,
                             Long routeId) {
    }
}
