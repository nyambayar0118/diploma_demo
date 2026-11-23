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
 * 3-р шат: эцсийн жагсаалтыг бүрдүүлнэ. Дэд ангилал бүрээс хамгийн ихдээ {@link #MAX_PER_CATEGORY} газар (олон
 * янз байдал), байрын 15% орчмыг хэрэглэгчийн сонирхол багатай ангилалд (жин 0.5-аас бага) өгнө. Ингэснээр систем
 * тэдгээрийн талаар суралцсаар, хэрэглэгч сонирхлын хөөсөнд түгжигдэхгүй (судалгааны санал болголт, exploration).
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

        // Судалгааны санал болголт: жагсаалтад ороогүй, сонирхол багатай ангилал бүрээс хамгийн өндөр оноотой нэг нэр дэвшигч
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

        // Судалгааны санал болголтыг бүгдийг төгсгөлд нь биш, жагсаалтын дундуур тараана
        List<Candidate> result = new ArrayList<>(main);
        for (int i = 0; i < explore.size(); i++) {
            int position = Math.min(result.size(), (i + 1) * (k / (explore.size() + 1)));
            result.add(position, explore.get(i));
        }
        // Судалгааны газар олдоогүй бол сул байрыг дараагийн шилдэг нэр дэвшигчээр дүүргэнэ
        for (Candidate c : ranked) {
            if (result.size() >= k) break;
            if (!result.contains(c)) result.add(c);
        }
        return result;
    }
}
