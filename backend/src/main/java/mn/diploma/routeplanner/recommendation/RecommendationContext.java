package mn.diploma.routeplanner.recommendation;

import java.time.LocalDateTime;

import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Everything a recommendation request depends on (design class RecommendationContext). */
public record RecommendationContext(
        Long userId,
        double lat,
        double lng,
        LocalDateTime at,
        int availableMinutes,
        PriceLevel budgetLevel,
        TransportMode transportMode,
        int radiusM,
        int k,
        boolean useInterests,
        boolean storeLocation,
        /** Main or subcategory codes to limit the list to; empty = all categories. */
        java.util.List<String> categories) {
}
