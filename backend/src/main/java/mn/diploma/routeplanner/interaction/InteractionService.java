package mn.diploma.routeplanner.interaction;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.ConsentDataType;
import mn.diploma.routeplanner.auth.ConsentService;

/**
 * Public service of the Interaction module: records what users do (АШ13, ДШ03).
 * The log row is written only with the INTERACTION_HISTORY consent (ХШ05); the event is published
 * in any case so the interest profile can still adapt (that needs the INTERESTS consent instead).
 */
@Service
public class InteractionService {

    private final JdbcTemplate jdbc;
    private final ConsentService consents;
    private final ApplicationEventPublisher events;

    public InteractionService(JdbcTemplate jdbc, ConsentService consents, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.consents = consents;
        this.events = events;
    }

    @Transactional
    public void log(Long userId, Long placeId, InteractionType type, Long recommendationId, Long routeId,
                    Double lat, Double lng) {
        if (consents.isGranted(userId, ConsentDataType.INTERACTION_HISTORY)) {
            boolean withLocation = lat != null && lng != null
                    && consents.isGranted(userId, ConsentDataType.LOCATION);
            jdbc.update("""
                    INSERT INTO user_interactions (user_id, place_id, type, recommendation_id, route_id, user_location)
                    VALUES (?, ?, ?, ?, ?, CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(?, ?), 4326) END)
                    """, userId, placeId, type.name(), recommendationId, routeId,
                    withLocation, withLocation ? lng : null, withLocation ? lat : null);
        }
        events.publishEvent(new InteractionLoggedEvent(userId, placeId, type));
    }

    public void log(Long userId, Long placeId, InteractionType type) {
        log(userId, placeId, type, null, null, null, null);
    }
}
