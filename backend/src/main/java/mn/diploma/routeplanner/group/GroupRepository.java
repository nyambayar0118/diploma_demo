package mn.diploma.routeplanner.group;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.common.TransportMode;

/** Бүлгийн төлөвлөгөө, гишүүдийг JDBC-ээр хадгална (эхлэх цэг нь PostGIS-ийн цэг). */
@Repository
public class GroupRepository {

    /** Гишүүдийнхээ хамт бүлгийн төлөвлөгөө. */
    public static class Session {
        long id;
        GroupStatus status;
        int timeBudgetMinutes;
        PriceLevel budgetLevel;
        Long meetingPlaceId;
        String inviteToken;
        Instant inviteExpiresAt;
        Instant createdAt;
        List<Member> members = new ArrayList<>();

        Member initiator() {
            return members.stream().filter(m -> m.role == MemberRole.INITIATOR).findFirst().orElseThrow();
        }

        Optional<Member> memberOfUser(long userId) {
            return members.stream().filter(m -> m.userId != null && m.userId == userId).findFirst();
        }

        boolean isInviteValid(Instant now) {
            return status == GroupStatus.SHARED && inviteToken != null && inviteExpiresAt != null
                    && inviteExpiresAt.isAfter(now);
        }
    }

    /** Бүртгэлтэй гишүүн (userId) эсвэл зочин (guestName). */
    public static class Member {
        long id;
        Long userId;
        String guestName;
        String displayName;
        MemberRole role;
        Double startLat;
        Double startLng;
        StartSource startSource;
        TransportMode transportMode;
        boolean usesInterests;
        Integer travelMinutes;
        boolean joinedViaLink;

        boolean isGuest() {
            return userId == null;
        }

        boolean hasStart() {
            return startLat != null && startLng != null;
        }
    }

    private final JdbcTemplate jdbc;

    public GroupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertSession(int timeBudgetMinutes, PriceLevel budgetLevel) {
        return jdbc.queryForObject("""
                INSERT INTO group_sessions (time_budget_min, budget_level) VALUES (?, ?) RETURNING id
                """, Long.class, timeBudgetMinutes, budgetLevel.name());
    }

    public long insertMember(long sessionId, Member m) {
        return jdbc.queryForObject("""
                INSERT INTO group_members (session_id, user_id, guest_name, role, start_location, start_source,
                                           transport_mode, uses_interests, joined_via_link)
                VALUES (?, ?, ?, ?, CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(?, ?), 4326) END, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, sessionId, m.userId, m.guestName, m.role.name(), m.hasStart(),
                m.startLng, m.startLat, m.startSource == null ? null : m.startSource.name(), m.transportMode.name(),
                m.usesInterests, m.joinedViaLink);
    }

    public void updateMemberStart(long memberId, Double lat, Double lng, StartSource source, TransportMode mode) {
        jdbc.update("""
                UPDATE group_members
                SET start_location = CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(?, ?), 4326) ELSE start_location END,
                    start_source = coalesce(?, start_source), transport_mode = ?
                WHERE id = ?
                """, lat != null && lng != null, lng, lat, source == null ? null : source.name(), mode.name(), memberId);
    }

    public void updateTravel(long memberId, Integer minutes) {
        jdbc.update("UPDATE group_members SET estimated_travel_min = ? WHERE id = ?", minutes, memberId);
    }

    public void deleteMember(long memberId) {
        jdbc.update("DELETE FROM group_members WHERE id = ?", memberId);
    }

    public void share(long sessionId, long placeId, String token, Instant expires) {
        jdbc.update("""
                UPDATE group_sessions SET status = 'SHARED', meeting_place_id = ?, invite_token = ?,
                                          invite_expires_at = ?, shared_at = now()
                WHERE id = ?
                """, placeId, token, Timestamp.from(expires), sessionId);
    }

    /** Төлөвлөгөөг хаана; урилгын холбоос ажиллахаа болино. */
    public void close(long sessionId, GroupStatus status) {
        jdbc.update("""
                UPDATE group_sessions SET status = ?, invite_token = NULL, invite_expires_at = NULL, closed_at = now()
                WHERE id = ?
                """, status.name(), sessionId);
    }

    public Optional<Session> findById(long id) {
        return load("WHERE s.id = ?", id).stream().findFirst();
    }

    public Optional<Session> findByInviteToken(String token) {
        return load("WHERE s.invite_token = ?", token).stream().findFirst();
    }

    public List<Session> findByMemberUser(long userId) {
        return load("WHERE s.id IN (SELECT session_id FROM group_members WHERE user_id = ?) "
                + "ORDER BY s.created_at DESC LIMIT 20", userId);
    }

    public int closeExpired() {
        int completed = jdbc.update("""
                UPDATE group_sessions SET status = 'COMPLETED', invite_token = NULL, invite_expires_at = NULL,
                                          closed_at = now()
                WHERE status = 'SHARED'
                  AND (shared_at AT TIME ZONE 'Asia/Ulaanbaatar')::date < (now() AT TIME ZONE 'Asia/Ulaanbaatar')::date
                """);
        int cancelled = jdbc.update("""
                UPDATE group_sessions SET status = 'CANCELLED', closed_at = now()
                WHERE status = 'RECOMMENDED' AND created_at < now() - INTERVAL '1 day'
                """);
        return completed + cancelled;
    }

    private List<Session> load(String where, Object... args) {
        List<Session> sessions = jdbc.query("""
                SELECT s.id, s.status, s.time_budget_min, s.budget_level, s.meeting_place_id, s.invite_token,
                       s.invite_expires_at, s.created_at
                FROM group_sessions s
                """ + where, (rs, i) -> session(rs), args);
        for (Session s : sessions) {
            s.members = jdbc.query("""
                    SELECT g.id, g.user_id, g.guest_name, coalesce(u.display_name, g.guest_name) AS display_name, g.role,
                           ST_Y(g.start_location) AS lat, ST_X(g.start_location) AS lng, g.start_source,
                           g.transport_mode, g.uses_interests, g.estimated_travel_min, g.joined_via_link
                    FROM group_members g LEFT JOIN users u ON u.id = g.user_id
                    WHERE g.session_id = ?
                    ORDER BY g.role, g.id
                    """, (rs, i) -> member(rs), s.id);
        }
        return sessions;
    }

    private static Session session(ResultSet rs) throws SQLException {
        Session s = new Session();
        s.id = rs.getLong("id");
        s.status = GroupStatus.valueOf(rs.getString("status"));
        s.timeBudgetMinutes = rs.getInt("time_budget_min");
        s.budgetLevel = PriceLevel.valueOf(rs.getString("budget_level"));
        s.meetingPlaceId = rs.getObject("meeting_place_id", Long.class);
        s.inviteToken = rs.getString("invite_token");
        Timestamp exp = rs.getTimestamp("invite_expires_at");
        s.inviteExpiresAt = exp == null ? null : exp.toInstant();
        s.createdAt = rs.getTimestamp("created_at").toInstant();
        return s;
    }

    private static Member member(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.id = rs.getLong("id");
        m.userId = rs.getObject("user_id", Long.class);
        m.guestName = rs.getString("guest_name");
        m.displayName = rs.getString("display_name");
        m.role = MemberRole.valueOf(rs.getString("role"));
        m.startLat = rs.getObject("lat", Double.class);
        m.startLng = rs.getObject("lng", Double.class);
        String source = rs.getString("start_source");
        m.startSource = source == null ? null : StartSource.valueOf(source);
        m.transportMode = TransportMode.valueOf(rs.getString("transport_mode"));
        m.usesInterests = rs.getBoolean("uses_interests");
        m.travelMinutes = rs.getObject("estimated_travel_min", Integer.class);
        m.joinedViaLink = rs.getBoolean("joined_via_link");
        return m;
    }
}
