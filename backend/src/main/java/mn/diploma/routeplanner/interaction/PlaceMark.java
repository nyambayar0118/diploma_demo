package mn.diploma.routeplanner.interaction;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Current saved / hidden state of a place for a user (the history is in user_interactions). */
@Entity
@Table(name = "place_marks")
public class PlaceMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarkType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PlaceMark() {
    }

    public PlaceMark(Long userId, Long placeId, MarkType type) {
        this.userId = userId;
        this.placeId = placeId;
        this.type = type;
    }

    public void change(MarkType type) {
        this.type = type;
        this.createdAt = Instant.now();
    }

    public Long getPlaceId() { return placeId; }
    public MarkType getType() { return type; }
}
