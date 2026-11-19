package mn.diploma.routeplanner.routing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import mn.diploma.routeplanner.common.TransportMode;
import mn.diploma.routeplanner.routing.RouteStop.PlaceInfo;

/** JDBC persistence of routes (PostGIS start point, so plain SQL instead of JPA). */
@Repository
public class RouteRepository {

    private static final String PLACE_COLUMNS = """
            p.id AS place_id, p.name_cyrillic, p.name_latin, c.code, c.name_mn, m.code AS main_code,
            ST_Y(p.location) AS place_lat, ST_X(p.location) AS place_lng, p.opening_hours_raw
            """;

    private static final String ROUTE_SELECT = """
            SELECT r.id, r.owner_id, ST_Y(r.start_location) AS start_lat, ST_X(r.start_location) AS start_lng,
                   r.transport_mode, r.time_budget_min, r.estimated_duration_min, r.planned_start, r.status,
                   r.created_at, r.opened_at, r.closed_at, r.group_session_id
            FROM routes r
            """;

    private final JdbcTemplate jdbc;

    public RouteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Active places by id (inactive or unknown ids are missing from the result). */
    public Map<Long, PlaceInfo> activePlaces(Collection<Long> ids) {
        Map<Long, PlaceInfo> result = new LinkedHashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.query("SELECT " + PLACE_COLUMNS + """
                FROM places p
                JOIN place_categories c ON c.id = p.category_id
                LEFT JOIN place_categories m ON m.id = c.parent_id
                WHERE p.is_active AND p.id = ANY (?)
                """, rs -> {
            PlaceInfo p = place(rs);
            result.put(p.id(), p);
        }, (Object) ids.toArray(Long[]::new));
        return result;
    }

    /** The recommendation id if it belongs to this user and place, otherwise null (never trust the client). */
    public Long ownRecommendation(long userId, Long recommendationId, long placeId) {
        if (recommendationId == null) return null;
        return jdbc.query("""
                SELECT r.id FROM recommendations r JOIN recommendation_batches b ON b.id = r.batch_id
                WHERE r.id = ? AND r.place_id = ? AND b.user_id = ?
                """, (rs, i) -> rs.getLong(1), recommendationId, placeId, userId).stream().findFirst().orElse(null);
    }

    public void insert(Route r) {
        r.id = jdbc.queryForObject("""
                INSERT INTO routes (owner_id, start_location, transport_mode, time_budget_min, estimated_duration_min,
                                    planned_start, status, created_at, group_session_id)
                VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, r.ownerId, r.start.lng(), r.start.lat(), r.transportMode.name(), r.timeBudgetMinutes,
                r.estimatedDurationMinutes, Timestamp.valueOf(r.plannedStart), r.status.name(),
                Timestamp.from(r.createdAt), r.groupSessionId);
        insertStops(r);
    }

    public void update(Route r) {
        jdbc.update("""
                UPDATE routes SET estimated_duration_min = ?, status = ?, opened_at = ?, closed_at = ? WHERE id = ?
                """, r.estimatedDurationMinutes, r.status.name(), ts(r.openedAt), ts(r.closedAt), r.id);
    }

    /** Replaces all stops (only done while the route is CREATED, so no visit answers are lost). */
    public void replaceStops(Route r) {
        jdbc.update("DELETE FROM route_stops WHERE route_id = ?", r.id);
        insertStops(r);
    }

    public void updateStop(RouteStop s) {
        jdbc.update("UPDATE route_stops SET is_visited = ?, visited_at = ?, skipped_at = ? WHERE id = ?",
                s.visited, ts(s.visitedAt), ts(s.skippedAt), s.id);
    }

    private void insertStops(Route r) {
        for (RouteStop s : r.stops) {
            s.id = jdbc.queryForObject("""
                    INSERT INTO route_stops (route_id, place_id, recommendation_id, stop_order, estimated_arrival,
                                             stay_minutes, is_visited, visited_at, skipped_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, r.id, s.place.id(), s.recommendationId, s.stopOrder,
                    Timestamp.valueOf(s.estimatedArrival), s.stayMinutes, s.visited, ts(s.visitedAt), ts(s.skippedAt));
        }
    }

    public Optional<Route> findById(long id) {
        List<Route> found = load(ROUTE_SELECT + "WHERE r.id = ?", id);
        return found.stream().findFirst();
    }

    public Optional<Route> findByStopId(long stopId) {
        return load(ROUTE_SELECT + "WHERE r.id = (SELECT route_id FROM route_stops WHERE id = ?)", stopId)
                .stream().findFirst();
    }

    /** Newest first. */
    public List<Route> findByOwner(long ownerId, int limit) {
        return load(ROUTE_SELECT + "WHERE r.owner_id = ? ORDER BY r.created_at DESC, r.id DESC LIMIT ?", ownerId, limit);
    }

    public List<Route> findByOwnerAndStatus(long ownerId, RouteStatus status) {
        return load(ROUTE_SELECT + "WHERE r.owner_id = ? AND r.status = ? ORDER BY r.opened_at DESC NULLS LAST, r.id DESC",
                ownerId, status.name());
    }

    /**
     * Closes routes whose day has passed (local time): opened ones become COMPLETED, never opened ones CANCELLED.
     */
    public int closeExpired(LocalDateTime nowLocal) {
        return jdbc.update("""
                UPDATE routes
                SET status = CASE WHEN status = 'OPENED_IN_MAPS' THEN 'COMPLETED' ELSE 'CANCELLED' END,
                    closed_at = now()
                WHERE status IN ('CREATED', 'OPENED_IN_MAPS')
                  AND (planned_start + estimated_duration_min * INTERVAL '1 minute')::date < ?::date
                """, Timestamp.valueOf(nowLocal));
    }

    private List<Route> load(String sql, Object... args) {
        List<Route> routes = jdbc.query(sql, (rs, i) -> route(rs), args);
        if (routes.isEmpty()) return routes;
        Map<Long, Route> byId = new LinkedHashMap<>();
        routes.forEach(r -> byId.put(r.id, r));
        jdbc.query("SELECT s.id, s.route_id, s.recommendation_id, s.stop_order, s.estimated_arrival, s.stay_minutes, "
                + "s.is_visited, s.visited_at, s.skipped_at, " + PLACE_COLUMNS + """
                FROM route_stops s
                JOIN places p ON p.id = s.place_id
                JOIN place_categories c ON c.id = p.category_id
                LEFT JOIN place_categories m ON m.id = c.parent_id
                WHERE s.route_id = ANY (?)
                ORDER BY s.route_id, s.stop_order
                """, rs -> {
            RouteStop s = new RouteStop(place(rs), (Long) rs.getObject("recommendation_id", Long.class),
                    rs.getInt("stay_minutes"));
            s.id = rs.getLong("id");
            s.stopOrder = rs.getInt("stop_order");
            s.estimatedArrival = rs.getTimestamp("estimated_arrival").toLocalDateTime();
            s.visited = rs.getBoolean("is_visited");
            s.visitedAt = instant(rs.getTimestamp("visited_at"));
            s.skippedAt = instant(rs.getTimestamp("skipped_at"));
            byId.get(rs.getLong("route_id")).stops.add(s);
        }, (Object) byId.keySet().toArray(Long[]::new));
        return new ArrayList<>(byId.values());
    }

    private static Route route(ResultSet rs) throws SQLException {
        Route r = new Route(rs.getLong("owner_id"), new LatLng(rs.getDouble("start_lat"), rs.getDouble("start_lng")),
                TransportMode.valueOf(rs.getString("transport_mode")), rs.getInt("time_budget_min"),
                rs.getTimestamp("planned_start").toLocalDateTime());
        r.id = rs.getLong("id");
        r.estimatedDurationMinutes = rs.getInt("estimated_duration_min");
        r.status = RouteStatus.valueOf(rs.getString("status"));
        r.createdAt = instant(rs.getTimestamp("created_at"));
        r.openedAt = instant(rs.getTimestamp("opened_at"));
        r.closedAt = instant(rs.getTimestamp("closed_at"));
        r.groupSessionId = rs.getObject("group_session_id", Long.class);
        return r;
    }

    private static PlaceInfo place(ResultSet rs) throws SQLException {
        return new PlaceInfo(rs.getLong("place_id"), rs.getString("name_cyrillic"), rs.getString("name_latin"),
                rs.getString("code"), rs.getString("name_mn"), rs.getString("main_code"),
                rs.getDouble("place_lat"), rs.getDouble("place_lng"), rs.getString("opening_hours_raw"));
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
