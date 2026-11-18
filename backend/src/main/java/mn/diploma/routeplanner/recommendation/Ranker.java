package mn.diploma.routeplanner.recommendation;

import java.util.List;

/**
 * Stage 2 of the recommender. Implementations: {@link RuleBasedRanker} now; the LightGBM-based MlRanker in the
 * ML milestone, with fallback to the rule-based ranker when the ML service does not answer in time (МГШ06).
 */
public interface Ranker {

    /** Sets {@code score} on every candidate (higher = better). */
    void score(List<Candidate> candidates, RecommendationContext ctx);

    String name();

    /** Model version for the batch log; null for rule-based ranking. */
    default String modelVersion() {
        return null;
    }
}
