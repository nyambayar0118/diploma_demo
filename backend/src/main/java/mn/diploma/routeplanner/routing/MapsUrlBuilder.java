package mn.diploma.routeplanner.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.common.TransportMode;

/**
 * Дараалуулсан зогсоолуудын Google Maps URL (https://developers.google.com/maps/documentation/urls) (АШ22).
 * API түлхүүргүй, серверээс хүсэлт илгээхгүй: клиент холбоосыг Google Maps апп эсвэл вебээр нээнэ.
 * Гар утасны Maps хамгийн ихдээ 3 дамжих цэг дэмждэг (тул хамгийн ихдээ 4 зогсоол), нийтийн тээврийн чиглэлд
 * дамжих цэг дэмжигддэггүй тул нийтийн тээвэрт хэсэг бүрд тусдаа холбоос үүсгэнэ.
 */
@Component
public class MapsUrlBuilder {

    static final int MAX_WAYPOINTS = 3;
    private static final String BASE = "https://www.google.com/maps/dir/?api=1";

    public List<MapsLink> directions(LatLng origin, List<LatLng> stops, TransportMode mode) {
        if (stops.isEmpty()) return List.of();
        List<MapsLink> links = new ArrayList<>();
        if (mode == TransportMode.PUBLIC_TRANSPORT) {
            LatLng from = origin;
            for (int i = 0; i < stops.size(); i++) {
                links.add(new MapsLink(url(from, stops.get(i), List.of(), mode), mode, i));
                from = stops.get(i);
            }
            return links;
        }
        if (stops.size() - 1 > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("At most " + (MAX_WAYPOINTS + 1) + " stops");
        }
        links.add(new MapsLink(url(origin, stops.getLast(), stops.subList(0, stops.size() - 1), mode), mode, 0));
        return links;
    }

    /** Утас хаана байгаагаас эхлэх чиглэл (эхлэх цэг өгөхгүй: Google Maps одоогийн байршлыг ашиглана). */
    public String fromCurrentLocation(LatLng to, TransportMode mode) {
        return BASE + "&destination=" + coord(to) + "&travelmode=" + travelMode(mode);
    }

    private static String url(LatLng from, LatLng to, List<LatLng> waypoints, TransportMode mode) {
        StringBuilder sb = new StringBuilder(BASE)
                .append("&origin=").append(coord(from))
                .append("&destination=").append(coord(to))
                .append("&travelmode=").append(travelMode(mode));
        if (!waypoints.isEmpty()) {
            sb.append("&waypoints=");
            for (int i = 0; i < waypoints.size(); i++) {
                if (i > 0) sb.append("%7C"); // "|"
                sb.append(coord(waypoints.get(i)));
            }
        }
        return sb.toString();
    }

    private static String coord(LatLng p) {
        return String.format(Locale.ROOT, "%.6f,%.6f", p.lat(), p.lng());
    }

    static String travelMode(TransportMode mode) {
        return switch (mode) {
            case WALKING -> "walking";
            case DRIVING -> "driving";
            case BICYCLE -> "bicycling";
            case PUBLIC_TRANSPORT -> "transit";
        };
    }
}
