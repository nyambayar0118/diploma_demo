package mn.diploma.routeplanner.interest;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.auth.UserDeletedEvent;

/** ХШ08: the interest profile and survey answers are deleted. */
@Component
class InterestDataCleanup {

    private final JdbcTemplate jdbc;

    InterestDataCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("DELETE FROM interest_weights WHERE user_id = ?", e.userId());
        jdbc.update("DELETE FROM onboarding_surveys WHERE user_id = ?", e.userId());
    }
}
