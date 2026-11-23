package mn.diploma.routeplanner.routing;

import mn.diploma.routeplanner.common.TransportMode;

/** Google Maps-ийн нэг чиглэлийн холбоос; нийтийн тээвэрт хэсэг тус бүрд нэг холбоос (legIndex 0, 1, ...). */
public record MapsLink(String url, TransportMode mode, int legIndex) {
}
