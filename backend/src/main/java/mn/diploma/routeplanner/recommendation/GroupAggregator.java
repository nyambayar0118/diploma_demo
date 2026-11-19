package mn.diploma.routeplanner.recommendation;

import java.util.List;

/**
 * Combines the members' scores of one place into the group score (research 2.x, Masthoff 2011).
 * Chosen with app.group.aggregator (default LEAST_MISERY: no member gets a place they dislike).
 */
public interface GroupAggregator {

    double aggregate(List<Double> memberScores);

    String name();

    enum Strategy implements GroupAggregator {
        /** S_avg(p) = mean over members of s_u(p): highest total satisfaction. */
        AVERAGE {
            @Override
            public double aggregate(List<Double> s) {
                return s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            }
        },
        /** S_lm(p) = min over members of s_u(p): avoids places one member would not like at all. */
        LEAST_MISERY {
            @Override
            public double aggregate(List<Double> s) {
                return s.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            }
        };
    }
}
