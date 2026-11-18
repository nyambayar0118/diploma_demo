package mn.diploma.routeplanner.routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.common.TransportMode;

/**
 * Picks the stops of an automatic route from ranked recommendations: greedily in rank order, at most one
 * place per subcategory, as long as the optimised route (travel + typical stay) still fits the time budget.
 */
@Component
public class StopSelector {

    /** A ranked recommendation offered as a possible stop. */
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
            chosen.add(candidates.getFirst()); // nothing fits: still offer the best one, flagged as over budget
        }
        return chosen;
    }

    /** Travel along the best order plus the typical stay at every stop. */
    int durationMinutes(LatLng start, List<ScoredPlace> stops, TransportMode mode) {
        List<LatLng> points = new ArrayList<>();
        points.add(start);
        stops.forEach(s -> points.add(new LatLng(s.lat(), s.lng())));
        TravelTimeMatrix matrix = travelTimes.estimate(points, mode);
        int travel = RouteOrderOptimizer.travelMinutes(matrix, 0, optimizer.optimizeOrder(matrix, 0));
        return travel + stops.stream().mapToInt(s -> StayDurations.of(s.category())).sum();
    }
}
