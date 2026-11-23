package mn.diploma.routeplanner.friend;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.UserDeletedEvent;
import mn.diploma.routeplanner.common.ApiException;

/** Найзын модуль (АШ20): хэрэглэгчийн нэр эсвэл урилгын холбоосоор хүсэлт илгээх, зөвшөөрөх / татгалзах, хасах. */
@Service
public class FriendService {

    static final String INVITE_PURPOSE = "friend";
    static final Duration INVITE_TTL = Duration.ofDays(7);

    public record UserRef(long id, String username, String displayName) {
    }

    /** {@code direction}: FRIEND (найз), INCOMING (миний хариуг хүлээж буй) эсвэл OUTGOING (би илгээсэн). */
    public record FriendshipDto(long id, String direction, UserRef user, Instant since) {
    }

    public record InviteLink(String token, Instant expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final InviteTokens tokens;

    public FriendService(JdbcTemplate jdbc, InviteTokens tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public List<FriendshipDto> list(long userId) {
        return jdbc.query("""
                SELECT f.id, f.status, f.requester_id, coalesce(f.responded_at, f.created_at) AS since,
                       u.id AS uid, u.username, u.display_name
                FROM friendships f
                JOIN users u ON u.id = CASE WHEN f.requester_id = ? THEN f.addressee_id ELSE f.requester_id END
                WHERE (f.requester_id = ? OR f.addressee_id = ?) AND f.status IN ('PENDING', 'ACCEPTED')
                  AND u.deleted_at IS NULL
                ORDER BY f.status, u.display_name
                """, (rs, i) -> new FriendshipDto(rs.getLong("id"),
                        "ACCEPTED".equals(rs.getString("status")) ? "FRIEND"
                                : rs.getLong("requester_id") == userId ? "OUTGOING" : "INCOMING",
                        new UserRef(rs.getLong("uid"), rs.getString("username"), rs.getString("display_name")),
                        rs.getTimestamp("since").toInstant()),
                userId, userId, userId);
    }

    /** Зөвшөөрөгдсөн найзуудын id (Байршил, Бүлгийн модуль ашиглана). */
    public List<Long> friendIds(long userId) {
        return jdbc.queryForList("""
                SELECT CASE WHEN requester_id = ? THEN addressee_id ELSE requester_id END
                FROM friendships WHERE (requester_id = ? OR addressee_id = ?) AND status = 'ACCEPTED'
                """, Long.class, userId, userId, userId);
    }

    public boolean areFriends(long a, long b) {
        return friendIds(a).contains(b);
    }

    @Transactional
    public FriendshipDto request(long userId, String username) {
        Long target;
        try {
            target = jdbc.queryForObject(
                    "SELECT id FROM users WHERE lower(username) = lower(?) AND deleted_at IS NULL", Long.class,
                    username.trim().replaceFirst("^@", ""));
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Ийм хэрэглэгчийн нэр олдсонгүй");
        }
        return connect(userId, target, false);
    }

    @Transactional
    public FriendshipDto respond(long userId, long friendshipId, boolean accept) {
        int n = jdbc.update("""
                UPDATE friendships SET status = ?, responded_at = now()
                WHERE id = ? AND addressee_id = ? AND status = 'PENDING'
                """, accept ? "ACCEPTED" : "REJECTED", friendshipId, userId);
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Хүсэлт олдсонгүй");
        return accept ? find(userId, friendshipId) : null;
    }

    /** Найзыг хасах, эсвэл хүлээгдэж буй хүсэлтийг цуцлах / татгалзах. */
    @Transactional
    public void remove(long userId, long friendshipId) {
        int n = jdbc.update("DELETE FROM friendships WHERE id = ? AND (requester_id = ? OR addressee_id = ?)",
                friendshipId, userId, userId);
        if (n == 0) throw new ApiException(HttpStatus.NOT_FOUND, "FRIEND_NOT_FOUND", "Олдсонгүй");
    }

    public InviteLink inviteLink(long userId) {
        String token = tokens.issue(INVITE_PURPOSE, userId, INVITE_TTL);
        return new InviteLink(token, tokens.expiresAt(token));
    }

    /** Урилгыг хэн илгээсэн (зөвшөөрөхөөс өмнө харуулна). */
    @Transactional(readOnly = true)
    public UserRef inviter(String token) {
        long inviter = tokens.verify(INVITE_PURPOSE, token).orElseThrow(FriendService::invalidInvite);
        return jdbc.query("SELECT id, username, display_name FROM users WHERE id = ? AND deleted_at IS NULL",
                (rs, i) -> new UserRef(rs.getLong(1), rs.getString(2), rs.getString(3)), inviter)
                .stream().findFirst().orElseThrow(FriendService::invalidInvite);
    }

    /** Найзын урилгын холбоосыг нээхэд хоёулаа шууд найз болно: холбоос өөрөө урьсан хүний зөвшөөрөл болно. */
    @Transactional
    public FriendshipDto acceptInvite(long userId, String token) {
        long inviter = inviter(token).id();
        return connect(userId, inviter, true);
    }

    private FriendshipDto connect(long me, long other, boolean preApproved) {
        if (me == other) throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_FRIEND", "Өөрийгөө найзаар нэмэх боломжгүй");
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, requester_id, status FROM friendships
                WHERE LEAST(requester_id, addressee_id) = LEAST(?::bigint, ?::bigint)
                  AND GREATEST(requester_id, addressee_id) = GREATEST(?::bigint, ?::bigint)
                """, me, other, me, other);
        if (existing.isEmpty()) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO friendships (requester_id, addressee_id, status, responded_at)
                    VALUES (?, ?, ?, CASE WHEN ? THEN now() END) RETURNING id
                    """, Long.class, me, other, preApproved ? "ACCEPTED" : "PENDING", preApproved);
            return find(me, id);
        }
        Map<String, Object> row = existing.getFirst();
        long id = ((Number) row.get("id")).longValue();
        String status = (String) row.get("status");
        boolean iAsked = ((Number) row.get("requester_id")).longValue() == me;
        if ("ACCEPTED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_FRIENDS", "Та хоёр аль хэдийн найзууд");
        }
        if ("PENDING".equals(status) && iAsked && !preApproved) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REQUESTED", "Хүсэлт аль хэдийн илгээсэн");
        }
        if (preApproved || ("PENDING".equals(status) && !iAsked)) {
            // Тэр намайг аль хэдийн урьсан (эсвэл холбоос зөвшөөрлийг илэрхийлнэ): найз болно
            jdbc.update("UPDATE friendships SET status = 'ACCEPTED', responded_at = now() WHERE id = ?", id);
        } else {
            // Өмнө татгалзсан: дахин хүсэлт илгээнэ
            jdbc.update("""
                    UPDATE friendships SET requester_id = ?, addressee_id = ?, status = 'PENDING', created_at = now(),
                                           responded_at = NULL WHERE id = ?
                    """, me, other, id);
        }
        return find(me, id);
    }

    private FriendshipDto find(long userId, long friendshipId) {
        return list(userId).stream().filter(f -> f.id() == friendshipId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FRIEND_NOT_FOUND", "Олдсонгүй"));
    }

    private static ApiException invalidInvite() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITE_INVALID", "Урилгын холбоос хүчингүй эсвэл хугацаа нь дууссан");
    }

    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        jdbc.update("DELETE FROM friendships WHERE requester_id = ? OR addressee_id = ?", e.userId(), e.userId());
    }
}
