package mn.diploma.routeplanner.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Stage 3: builds the final list. At most {@link #MAX_PER_CATEGORY} places per subcategory (variety), and about
 * 15% of the slots go to categories the user has shown little interest in (weight below 0.5), so the system keeps
 * learning about them and the user is not locked into a filter bubble.
 */
@Component
public class ExplorationPolicy {

    static final double EXPLORATION_SHARE = 0.15;
    static final double LOW_INTEREST = 0.5;
    static final int MAX_PER_CATEGORY = 3;

    public List<Candidate> select(List<Candidate> scored, int k) {
        List<Candidate> ranked = new ArrayList<>(scored);
        ranked.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());

        int explorationSlots = k >= 5 ? Math.max(1, (int) Math.round(k * EXPLORATION_SHARE)) : 0;
        List<Candidate> main = new ArrayList<>();
        Map<String, Integer> perCategory = new HashMap<>();
        for (Candidate c : ranked) {
            if (main.size() >= k - explorationSlots) break;
            if (perCategory.merge(c.category, 1, Integer::sum) <= MAX_PER_CATEGORY) {
                main.add(c);
            }
        }

        // Exploration: best-placed candidates from low-interest categories not in the list yet, one per category
        Set<String> usedCategories = new HashSet<>();
        main.forEach(c -> usedCategories.add(c.category));
        List<Candidate> explore = new ArrayList<>();
        for (Candidate c : ranked) {
            if (explore.size() >= explorationSlots) break;
            if (c.interestWeight < LOW_INTEREST && !main.contains(c) && usedCategories.add(c.category)) {
                c.exploration = true;
                explore.add(c);
            }
        }

        // Spread exploration items through the list instead of putting them all at the end
        List<Candidate> result = new ArrayList<>(main);
        for (int i = 0; i < explore.size(); i++) {
            int position = Math.min(result.size(), (i + 1) * (k / (explore.size() + 1)));
            result.add(position, explore.get(i));
        }
        // If exploration found nothing, fill the free slots with the next best candidates
        for (Candidate c : ranked) {
            if (result.size() >= k) break;
            if (!result.contains(c)) result.add(c);
        }
        return result;
    }
}
