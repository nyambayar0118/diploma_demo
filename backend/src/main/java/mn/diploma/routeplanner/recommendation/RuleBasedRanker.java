package mn.diploma.routeplanner.recommendation;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Суурь загвар болох дүрэмд суурилсан эрэмбэлэгч: оноо = 0.55 * сонирхлын жин + 0.25 * ойр байдал + 0.20 * цагийн тохирц,
 * энд ойр байдал = 1 - зай / хайлтын радиус. Машин сургалтын загвар NDCG@10-аар давтал үүнийг ашиглана.
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
