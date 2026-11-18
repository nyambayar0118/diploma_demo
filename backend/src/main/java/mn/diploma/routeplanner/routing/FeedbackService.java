package mn.diploma.routeplanner.routing;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.routing.RoutingDtos.FeedbackRequest;

/** Satisfaction rating of a finished route (1-5 on three questions); used in the evaluation chapter. */
@Service
public class FeedbackService {

    private final RoutingService routing;
    private final JdbcTemplate jdbc;

    public FeedbackService(RoutingService routing, JdbcTemplate jdbc) {
        this.routing = routing;
        this.jdbc = jdbc;
    }

    @Transactional
    public void submit(long userId, long routeId, FeedbackRequest dto) {
        Route route = routing.owned(userId, routeId);
        if (route.status != RouteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "ROUTE_NOT_COMPLETED", "Маршрут дуусаагүй байна");
        }
        try {
            jdbc.update("""
                    INSERT INTO satisfaction_feedbacks (route_id, user_id, relevance, time_suitability, estimate_accuracy)
                    VALUES (?, ?, ?, ?, ?)
                    """, routeId, userId, dto.relevance(), dto.timeSuitability(), dto.estimateAccuracy());
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "FEEDBACK_EXISTS", "Үнэлгээ аль хэдийн өгсөн байна");
        }
    }
}
