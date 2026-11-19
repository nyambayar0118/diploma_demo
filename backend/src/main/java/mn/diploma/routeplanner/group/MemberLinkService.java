package mn.diploma.routeplanner.group;

import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.routing.LatLng;
import mn.diploma.routeplanner.routing.MapsUrlBuilder;

/** Each member's own Google Maps link to the meeting place (АШ24). */
@Component
public class MemberLinkService {

    private final MapsUrlBuilder maps;

    public MemberLinkService(MapsUrlBuilder maps) {
        this.maps = maps;
    }

    public String linkFor(GroupRepository.Member m, LatLng meetingPlace) {
        if (!m.hasStart()) return maps.fromCurrentLocation(meetingPlace, m.transportMode);
        return maps.directions(new LatLng(m.startLat, m.startLng), java.util.List.of(meetingPlace), m.transportMode)
                .getFirst().url();
    }
}
