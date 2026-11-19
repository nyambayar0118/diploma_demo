package mn.diploma.routeplanner.contribution;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.UserDeletedEvent;
import mn.diploma.routeplanner.contribution.ContributionDtos.RewardView;
import mn.diploma.routeplanner.contribution.ContributionDtos.RewardsView;

/** Reward points for approved contributions (АШ05). users.reward_points holds the running total. */
@Service
public class RewardService {

    static final int POINTS_PER_CREATE = 20;
    static final int POINTS_PER_UPDATE = 10;
    static final int POINTS_PER_LINK = 5;

    private final JdbcTemplate jdbc;

    public RewardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static int pointsFor(RequestType type) {
        return switch (type) {
            case CREATE_PLACE -> POINTS_PER_CREATE;
            case UPDATE_PLACE -> POINTS_PER_UPDATE;
            case ADD_LINK -> POINTS_PER_LINK;
        };
    }

    @Transactional
    public int grantFor(long userId, long requestId, RequestType type, String placeName) {
        int points = pointsFor(type);
        String reason = switch (type) {
            case CREATE_PLACE -> "Шинэ газар нэмсэн: ";
            case UPDATE_PLACE -> "Газрын мэдээлэл зассан: ";
            case ADD_LINK -> "Холбоос нэмсэн: ";
        } + placeName;
        jdbc.update("INSERT INTO rewards (user_id, request_id, points, reason) VALUES (?, ?, ?, left(?, 200))",
                userId, requestId, points, reason);
        jdbc.update("UPDATE users SET reward_points = reward_points + ? WHERE id = ?", points, userId);
        return points;
    }

    @Transactional(readOnly = true)
    public RewardsView rewards(long userId) {
        List<RewardView> items = jdbc.query("""
                SELECT id, points, reason, granted_at, request_id FROM rewards WHERE user_id = ? ORDER BY granted_at DESC
                """, (rs, i) -> new RewardView(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                        rs.getObject(5, Long.class)), userId);
        Integer total = jdbc.queryForObject("SELECT reward_points FROM users WHERE id = ?", Integer.class, userId);
        return new RewardsView(total == null ? 0 : total, items);
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("DELETE FROM rewards WHERE user_id = ?", e.userId());
        jdbc.update("UPDATE place_change_requests SET submitter_id = NULL WHERE submitter_id = ?", e.userId());
        jdbc.update("UPDATE external_links SET submitted_by = NULL WHERE submitted_by = ?", e.userId());
    }
}
