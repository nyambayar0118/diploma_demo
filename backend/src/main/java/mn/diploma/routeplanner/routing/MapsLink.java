package mn.diploma.routeplanner.routing;

import mn.diploma.routeplanner.common.TransportMode;

/** One Google Maps directions link; public transport gets one link per leg (legIndex 0, 1, ...). */
public record MapsLink(String url, TransportMode mode, int legIndex) {
}
