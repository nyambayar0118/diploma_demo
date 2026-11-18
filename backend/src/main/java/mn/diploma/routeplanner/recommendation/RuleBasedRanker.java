package mn.diploma.routeplanner.recommendation;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Baseline ranker: score = 0.55 * interest weight + 0.25 * closeness + 0.20 * time-of-day suitability,
 * where closeness = 1 - distance / search radius. Used until the ML model beats it on NDCG@10.
 */
@Component
public class RuleBasedRanker implements Ranker {

    static final double W_INTEREST = 0.55;
    static final double W_DISTANCE = 0.25;
    static final double W_TIME = 0.20;

    @Override
    public void score(List<Candidate> candidates, RecommendationContext ctx) {
        for (Candidate c : candidates) {
            double closeness = 1.0 - Math.min(1.0, c.distanceM / ctx.radiusM());
            c.score = W_INTEREST * c.interestWeight + W_DISTANCE * closeness + W_TIME * c.timePrior;
        }
    }

    @Override
    public String name() {
        return "RULE_BASED";
    }
}
