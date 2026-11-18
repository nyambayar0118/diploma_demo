package mn.diploma.routeplanner.routing;

import java.util.Map;

import org.springframework.stereotype.Service;

import mn.diploma.routeplanner.common.TransportMode;

/**
 * Approximate travel time without calling any external API (ХСШ05, ХСШ06):
 *   t = c * d_haversine / v_mode + overhead_mode,  c = 1.3 (circuity factor, Ballou et al. 2002).
 * The factor and speeds are calibrated later against Google Maps for sample point pairs.
 */
@Service
public class TravelTimeEstimator {

    static final double DETOUR_FACTOR = 1.3;
    private static final double EARTH_RADIUS_M = 6_371_000;

    /** Average door-to-door speed in km/h (public transport includes waiting and transfers). */
    private static final Map<TransportMode, Double> SPEED_KMH = Map.of(
            TransportMode.WALKING, 4.5,
            TransportMode.BICYCLE, 12.0,
            TransportMode.PUBLIC_TRANSPORT, 13.0,
            TransportMode.DRIVING, 22.0);

    /** Fixed minutes per trip: waiting for a bus, parking the car. */
    private static final Map<TransportMode, Integer> OVERHEAD_MIN = Map.of(
            TransportMode.WALKING, 0,
            TransportMode.BICYCLE, 1,
            TransportMode.PUBLIC_TRANSPORT, 8,
            TransportMode.DRIVING, 4);

    public int minutesBetween(double fromLat, double fromLng, double toLat, double toLng, TransportMode mode) {
        double km = DETOUR_FACTOR * haversineMeters(fromLat, fromLng, toLat, toLng) / 1000.0;
        double minutes = km / SPEED_KMH.get(mode) * 60.0 + OVERHEAD_MIN.get(mode);
        return (int) Math.ceil(minutes);
    }

    /** Travel minutes between every pair of points. */
    public TravelTimeMatrix estimate(java.util.List<LatLng> points, TransportMode mode) {
        int n = points.size();
        int[][] m = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    LatLng a = points.get(i);
                    LatLng b = points.get(j);
                    m[i][j] = minutesBetween(a.lat(), a.lng(), b.lat(), b.lng(), mode);
                }
            }
        }
        return new TravelTimeMatrix(m);
    }

    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
