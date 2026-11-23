package mn.diploma.routeplanner.recommendation;

import java.time.LocalDateTime;

import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Санал болголтын хүсэлтэд нөлөөлөх бүх зүйл (зохиомжийн RecommendationContext класс). */
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
        /** Жагсаалтыг хязгаарлах үндсэн эсвэл дэд ангиллын код; хоосон бол бүх ангилал. */
        java.util.List<String> categories) {
}
