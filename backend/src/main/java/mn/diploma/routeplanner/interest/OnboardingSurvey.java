package mn.diploma.routeplanner.interest;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Categories the user picked at registration; the starting point of the interest profile (cold start). */
@Entity
@Table(name = "onboarding_surveys")
public class OnboardingSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    @ElementCollection
    @CollectionTable(name = "survey_categories", joinColumns = @JoinColumn(name = "survey_id"))
    @Column(name = "category_id")
    private Set<Long> categoryIds = new HashSet<>();

    protected OnboardingSurvey() {
    }

    public OnboardingSurvey(Long userId, Set<Long> categoryIds) {
        this.userId = userId;
        this.categoryIds = new HashSet<>(categoryIds);
    }

    public void replaceCategories(Set<Long> ids) {
        categoryIds.clear();
        categoryIds.addAll(ids);
        completedAt = Instant.now();
    }

    public Set<Long> getCategoryIds() { return categoryIds; }
}
