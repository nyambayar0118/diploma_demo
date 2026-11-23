package mn.diploma.routeplanner.recommendation;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.auth.UserDeletedEvent;

/** ХШ08: санал болголтын жагсаалтууд нэргүй сургалтын өгөгдөл болж үлдэнэ. */
@Component
class RecommendationDataCleanup {

    private final JdbcTemplate jdbc;

    RecommendationDataCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("UPDATE recommendation_batches SET user_id = NULL, user_location = NULL WHERE user_id = ?", e.userId());
    }
}
