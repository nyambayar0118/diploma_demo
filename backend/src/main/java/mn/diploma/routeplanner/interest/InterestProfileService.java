package mn.diploma.routeplanner.interest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.ConsentDataType;
import mn.diploma.routeplanner.auth.ConsentService;
import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.place.CategoryLookup;
import mn.diploma.routeplanner.place.CategoryLookup.Subcategory;

/**
 * Interest Profile module: creates the profile from the onboarding survey (ДШ02, cold start).
 * Later milestones update the weights from user interactions (ДШ03).
 */
@Service
public class InterestProfileService {

    /** Starting weights: picked categories, other categories of the same main category, everything else. */
    static final double WEIGHT_SELECTED = 0.8;
    static final double WEIGHT_SAME_MAIN = 0.5;
    static final double WEIGHT_OTHER = 0.2;
    static final int MIN_SELECTED = 3;

    public record OnboardingStatus(boolean completed, List<String> categoryCodes) {
    }

    public record WeightDto(String category, String mainCategory, double weight) {
    }

    private final OnboardingSurveyRepository surveys;
    private final InterestWeightRepository weights;
    private final CategoryLookup categories;
    private final ConsentService consents;

    public InterestProfileService(OnboardingSurveyRepository surveys, InterestWeightRepository weights,
                                  CategoryLookup categories, ConsentService consents) {
        this.surveys = surveys;
        this.weights = weights;
        this.categories = categories;
        this.consents = consents;
    }

    @Transactional(readOnly = true)
    public OnboardingStatus status(Long userId) {
        Map<Long, String> codeById = categories.recommendableSubcategories().stream()
                .collect(Collectors.toMap(Subcategory::id, Subcategory::code));
        return surveys.findByUserId(userId)
                .map(s -> new OnboardingStatus(true, s.getCategoryIds().stream()
                        .map(codeById::get).filter(c -> c != null).sorted().toList()))
                .orElse(new OnboardingStatus(false, List.of()));
    }

    @Transactional
    public OnboardingStatus completeOnboarding(Long userId, Set<String> categoryCodes, Set<ConsentDataType> granted) {
        if (!granted.contains(ConsentDataType.INTERESTS)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERESTS_CONSENT_REQUIRED",
                    "Сонирхлоо ашиглуулах зөвшөөрөлгүйгээр санал болголт гаргах боломжгүй");
        }
        List<Subcategory> all = categories.recommendableSubcategories();
        Map<String, Subcategory> byCode = all.stream().collect(Collectors.toMap(Subcategory::code, Function.identity()));
        Set<String> unknown = new HashSet<>(categoryCodes);
        unknown.removeAll(byCode.keySet());
        if (!unknown.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY", "Unknown categories: " + unknown);
        }
        if (categoryCodes.size() < MIN_SELECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_FEW_CATEGORIES",
                    "Дор хаяж " + MIN_SELECTED + " ангилал сонгоно уу");
        }

        granted.forEach(type -> consents.grant(userId, type));

        Set<Long> selectedIds = categoryCodes.stream().map(c -> byCode.get(c).id()).collect(Collectors.toSet());
        surveys.findByUserId(userId).ifPresentOrElse(
                s -> s.replaceCategories(selectedIds),
                () -> surveys.save(new OnboardingSurvey(userId, selectedIds)));

        Set<Long> selectedMains = categoryCodes.stream().map(c -> byCode.get(c).mainId()).collect(Collectors.toSet());
        Map<Long, InterestWeight> existing = new HashMap<>();
        weights.findByUserId(userId).forEach(w -> existing.put(w.getCategoryId(), w));
        for (Subcategory sub : all) {
            double w = selectedIds.contains(sub.id()) ? WEIGHT_SELECTED
                    : selectedMains.contains(sub.mainId()) ? WEIGHT_SAME_MAIN
                    : WEIGHT_OTHER;
            InterestWeight row = existing.get(sub.id());
            if (row == null) {
                weights.save(new InterestWeight(userId, sub.id(), w));
            } else {
                row.setWeight(w);
            }
        }
        return new OnboardingStatus(true, categoryCodes.stream().sorted().toList());
    }

    @Transactional(readOnly = true)
    public List<WeightDto> weights(Long userId) {
        Map<Long, Subcategory> byId = categories.recommendableSubcategories().stream()
                .collect(Collectors.toMap(Subcategory::id, Function.identity()));
        return weights.findByUserId(userId).stream()
                .filter(w -> byId.containsKey(w.getCategoryId()))
                .map(w -> {
                    Subcategory s = byId.get(w.getCategoryId());
                    return new WeightDto(s.code(), s.mainCode(), w.getWeight());
                })
                .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
                .toList();
    }
}
