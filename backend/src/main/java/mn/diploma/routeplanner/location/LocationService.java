package mn.diploma.routeplanner.location;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.ConsentDataType;
import mn.diploma.routeplanner.auth.ConsentService;
import mn.diploma.routeplanner.auth.UserDeletedEvent;
import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.friend.FriendService;

/**
 * Location module (АШ21, ХШ07): the user's latest position, visible to accepted friends only while the user
 * shares it, has the LOCATION_SHARING consent and the position is fresh. No history is kept.
 */
@Service
public class LocationService {

    /** Older positions are not shown: sharing ends by itself when the app is closed. */
    static final Duration FRESH_FOR = Duration.ofMinutes(60);

    public record MyLocation(boolean shared, Double lat, Double lng, Instant updatedAt) {
    }

    public record FriendLocation(long userId, String username, String displayName, double lat, double lng,
                                 Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final ConsentService consents;
    private final FriendService friends;

    public LocationService(JdbcTemplate jdbc, ConsentService consents, FriendService friends) {
        this.jdbc = jdbc;
        this.consents = consents;
        this.friends = friends;
    }

    /** Updates the position and the sharing switch; lat/lng may be omitted to only change the switch. */
    @Transactional
    public MyLocation update(long userId, Double lat, Double lng, boolean shared) {
        if (shared && !consents.isGranted(userId, ConsentDataType.LOCATION_SHARING)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED",
                    "Байршил хуваалцах зөвшөөрөл (LOCATION_SHARING) өгөөгүй байна");
        }
        if (!shared) {
            // Stop sharing: the position is not needed any more, so it is deleted (ХШ07)
            jdbc.update("DELETE FROM user_locations WHERE user_id = ?", userId);
            return new MyLocation(false, null, null, null);
        }
        if (lat != null && lng != null) {
            jdbc.update("""
                    INSERT INTO user_locations (user_id, location, updated_at, is_shared_with_friends)
                    VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326), now(), TRUE)
                    ON CONFLICT (user_id) DO UPDATE
                    SET location = EXCLUDED.location, updated_at = now(), is_shared_with_friends = TRUE
                    """, userId, lng, lat);
        } else {
            jdbc.update("UPDATE user_locations SET is_shared_with_friends = TRUE WHERE user_id = ?", userId);
        }
        return mine(userId);
    }

    @Transactional(readOnly = true)
    public MyLocation mine(long userId) {
        return jdbc.query("""
                SELECT is_shared_with_friends, ST_Y(location) AS lat, ST_X(location) AS lng, updated_at
                FROM user_locations WHERE user_id = ?
                """, (rs, i) -> new MyLocation(rs.getBoolean(1), rs.getDouble(2), rs.getDouble(3),
                        rs.getTimestamp(4).toInstant()), userId)
                .stream().findFirst().orElse(new MyLocation(false, null, null, null));
    }

    @Transactional(readOnly = true)
    public List<FriendLocation> friendLocations(long userId) {
        List<Long> ids = friends.friendIds(userId);
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT u.id, u.username, u.display_name, ST_Y(l.location) AS lat, ST_X(l.location) AS lng, l.updated_at
                FROM user_locations l JOIN users u ON u.id = l.user_id
                WHERE l.user_id = ANY (?) AND l.is_shared_with_friends AND u.deleted_at IS NULL
                  AND l.updated_at > now() - make_interval(mins => ?)
                  AND EXISTS (SELECT 1 FROM privacy_consents c WHERE c.user_id = u.id
                              AND c.data_type = 'LOCATION_SHARING' AND c.revoked_at IS NULL)
                ORDER BY u.display_name
                """, (rs, i) -> new FriendLocation(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getTimestamp(6).toInstant()),
                ids.toArray(Long[]::new), (int) FRESH_FOR.toMinutes());
    }

    /** A user's shared, fresh position (used for group members' start points), if any. */
    @Transactional(readOnly = true)
    public java.util.Optional<FriendLocation> sharedLocation(long userId) {
        return jdbc.query("""
                SELECT u.id, u.username, u.display_name, ST_Y(l.location) AS lat, ST_X(l.location) AS lng, l.updated_at
                FROM user_locations l JOIN users u ON u.id = l.user_id
                WHERE l.user_id = ? AND l.is_shared_with_friends AND l.updated_at > now() - make_interval(mins => ?)
                  AND EXISTS (SELECT 1 FROM privacy_consents c WHERE c.user_id = u.id
                              AND c.data_type = 'LOCATION_SHARING' AND c.revoked_at IS NULL)
                """, (rs, i) -> new FriendLocation(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getTimestamp(6).toInstant()), userId, (int) FRESH_FOR.toMinutes())
                .stream().findFirst();
    }

    /** Retention: stale positions are deleted every 15 minutes. */
    @Scheduled(fixedDelay = 15 * 60 * 1000, initialDelay = 60 * 1000)
    public int deleteStale() {
        return jdbc.update("DELETE FROM user_locations WHERE updated_at < now() - make_interval(mins => ?)",
                (int) FRESH_FOR.toMinutes());
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("DELETE FROM user_locations WHERE user_id = ?", e.userId());
    }
}
