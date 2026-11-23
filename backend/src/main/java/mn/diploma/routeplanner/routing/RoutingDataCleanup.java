package mn.diploma.routeplanner.routing;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import mn.diploma.routeplanner.auth.UserDeletedEvent;

/**
 * ХШ08: маршрутыг хэрэглэгчээс салгаж, хувийн мэдээлэл болох эхлэх цэгийг устгана;
 * зогсоолууд болон сэтгэл ханамжийн үнэлгээ нэргүй үнэлгээний өгөгдөл болж үлдэнэ.
 */
@Component
class RoutingDataCleanup {

    private final JdbcTemplate jdbc;

    RoutingDataCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("UPDATE satisfaction_feedbacks SET user_id = NULL WHERE user_id = ?", e.userId());
        jdbc.update("""
                UPDATE routes SET owner_id = NULL, start_location = NULL,
                       status = CASE WHEN status IN ('CREATED', 'OPENED_IN_MAPS') THEN 'CANCELLED' ELSE status END
                WHERE owner_id = ?
                """, e.userId());
    }
}
