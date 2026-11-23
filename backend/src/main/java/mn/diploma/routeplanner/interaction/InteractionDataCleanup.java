package mn.diploma.routeplanner.interaction;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.auth.UserDeletedEvent;

/** ХШ08: хадгалсан / нуусан газруудыг устгана; үйлдлийн бүртгэлийг хэрэглэгч, байршилгүйгээр үлдээнэ. */
@Component
class InteractionDataCleanup {

    private final JdbcTemplate jdbc;

    InteractionDataCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("DELETE FROM place_marks WHERE user_id = ?", e.userId());
        jdbc.update("UPDATE user_interactions SET user_id = NULL, user_location = NULL WHERE user_id = ?", e.userId());
    }
}
