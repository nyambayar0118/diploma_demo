package mn.diploma.routeplanner.group;

import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.location.LocationService;

/**
 * Гишүүний эхлэх цэг (зохиомж 3.2, Бүлгийн модуль): найзуудтайгаа хуваалцсан байршил, үгүй бол санаачлагчийн
 * газрын зураг дээр тэмдэглэсэн цэг, үгүй бол санаачлагчийн өөрийн эхлэх цэг.
 */
@Component
public class MemberStartResolver {

    public record Start(double lat, double lng, StartSource source) {
    }

    private final LocationService locations;

    public MemberStartResolver(LocationService locations) {
        this.locations = locations;
    }

    public Start resolve(long userId, Double pinnedLat, Double pinnedLng, double initiatorLat, double initiatorLng) {
        return locations.sharedLocation(userId)
                .map(l -> new Start(l.lat(), l.lng(), StartSource.SHARED_LOCATION))
                .orElseGet(() -> pinnedLat != null && pinnedLng != null
                        ? new Start(pinnedLat, pinnedLng, StartSource.PINNED)
                        : new Start(initiatorLat, initiatorLng, StartSource.SAME_AS_INITIATOR));
    }
}
