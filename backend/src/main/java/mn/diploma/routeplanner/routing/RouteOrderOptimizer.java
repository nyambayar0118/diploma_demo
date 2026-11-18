package mn.diploma.routeplanner.routing;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Visiting order with the least total travel time (ДШ05, ХСШ05). A route has at most 4 stops,
 * so every order is checked (4! = 24); the route is open (it does not return to the start).
 */
@Component
public class RouteOrderOptimizer {

    static final int MAX_STOPS = 8; // guard: 8! = 40 320 orders is still instant

    /** Indices of the non-start points in the best visiting order. */
    public List<Integer> optimizeOrder(TravelTimeMatrix matrix, int startIndex) {
        List<Integer> stops = new ArrayList<>();
        for (int i = 0; i < matrix.size(); i++) {
            if (i != startIndex) stops.add(i);
        }
        if (stops.size() > MAX_STOPS) {
            throw new IllegalArgumentException("Too many stops: " + stops.size());
        }
        List<Integer> best = stops;
        int bestCost = Integer.MAX_VALUE;
        for (List<Integer> order : permutations(stops)) {
            int cost = travelMinutes(matrix, startIndex, order);
            if (cost < bestCost) {
                bestCost = cost;
                best = order;
            }
        }
        return best;
    }

    static int travelMinutes(TravelTimeMatrix matrix, int startIndex, List<Integer> order) {
        int total = 0;
        int prev = startIndex;
        for (int i : order) {
            total += matrix.between(prev, i);
            prev = i;
        }
        return total;
    }

    static List<List<Integer>> permutations(List<Integer> items) {
        if (items.size() <= 1) return List.of(List.copyOf(items));
        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<Integer> rest = new ArrayList<>(items);
            Integer head = rest.remove(i);
            for (List<Integer> tail : permutations(rest)) {
                List<Integer> p = new ArrayList<>(tail.size() + 1);
                p.add(head);
                p.addAll(tail);
                result.add(p);
            }
        }
        return result;
    }
}
