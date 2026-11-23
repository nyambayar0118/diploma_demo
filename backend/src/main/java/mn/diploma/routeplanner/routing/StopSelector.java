package mn.diploma.routeplanner.routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.common.TransportMode;

/**
 * Эрэмбэлсэн санал болголтоос автомат маршрутын зогсоолыг сонгоно: эрэмбийн дарааллаар шунахайгаар, дэд ангилал
 * бүрээс нэг газар, оновчилсон маршрут (зорчих + байх хугацаа) цагийн хязгаарт багтаж байх хүртэл.
 */
@Component
public class StopSelector {

    /** Зогсоол болж болох эрэмбэлсэн санал болголт. */
    public record ScoredPlace(long placeId, Long recommendationId, String category, double lat, double lng) {
    }

    private final TravelTimeEstimator travelTimes;
    private final RouteOrderOptimizer optimizer;

    public StopSelector(TravelTimeEstimator travelTimes, RouteOrderOptimizer optimizer) {
        this.travelTimes = travelTimes;
        this.optimizer = optimizer;
    }

    public List<ScoredPlace> select(LatLng start, List<ScoredPlace> candidates, TransportMode mode,
                                    int budgetMinutes, int maxStops) {
        List<ScoredPlace> chosen = new ArrayList<>();
        Set<String> categories = new HashSet<>();
        for (ScoredPlace c : candidates) {
            if (chosen.size() >= maxStops) break;
            if (categories.contains(c.category())) continue;
            List<ScoredPlace> attempt = new ArrayList<>(chosen);
            attempt.add(c);
            if (durationMinutes(start, attempt, mode) <= budgetMinutes) {
                chosen = attempt;
                categories.add(c.category());
            }
        }
        if (chosen.isEmpty() && !candidates.isEmpty()) {
            chosen.add(candidates.getFirst()); // юу ч багтахгүй: шилдгийг нь хязгаар хэтэрсэн гэж тэмдэглээд санал болгоно
        }
        return chosen;
    }

    /** Хамгийн сайн дарааллаарх зорчих хугацаа дээр зогсоол бүрт байх хугацааг нэмнэ. */
    int durationMinutes(LatLng start, List<ScoredPlace> stops, TransportMode mode) {
        List<LatLng> points = new ArrayList<>();
        points.add(start);
        stops.forEach(s -> points.add(new LatLng(s.lat(), s.lng())));
        TravelTimeMatrix matrix = travelTimes.estimate(points, mode);
        int travel = RouteOrderOptimizer.travelMinutes(matrix, 0, optimizer.optimizeOrder(matrix, 0));
        return travel + stops.stream().mapToInt(s -> StayDurations.of(s.category())).sum();
    }
}
