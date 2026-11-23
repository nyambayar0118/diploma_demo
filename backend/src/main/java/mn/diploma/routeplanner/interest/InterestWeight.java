package mn.diploma.routeplanner.interest;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Нэг хэрэглэгчийн нэг дэд ангилалд харгалзах сонирхлын жин, 0..1 (зохиомжийн шийдвэр: ангилал бүрд нэг мөр). */
@Entity
@Table(name = "interest_weights")
public class InterestWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private double weight;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected InterestWeight() {
    }

    public InterestWeight(Long userId, Long categoryId, double weight) {
        this.userId = userId;
        this.categoryId = categoryId;
        setWeight(weight);
    }

    public void setWeight(double weight) {
        this.weight = Math.clamp(weight, 0.0, 1.0);
        this.updatedAt = Instant.now();
    }

    public Long getCategoryId() { return categoryId; }
    public double getWeight() { return weight; }
}
