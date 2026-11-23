package mn.diploma.routeplanner.place;

import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.auth.UserDeletedEvent;
import mn.diploma.routeplanner.common.ApiException;

/** Үнэлгээ, сэтгэгдэл (АШ16) болон зохисгүйг нуух (АШ17). */
@Service
public class ReviewService {

    public record ReviewDto(long id, long placeId, Long authorId, String authorName, int rating, String comment,
                            Instant createdAt, Instant updatedAt, boolean hidden, boolean mine) {
    }

    private final JdbcTemplate jdbc;

    public ReviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Хамгийн сүүлийнх нь эхэндээ; нуусан сэтгэгдлийг зөвхөн модератор (болон бичсэн хүн нь) харна. */
    @Transactional(readOnly = true)
    public List<ReviewDto> list(long placeId, Long viewerId, boolean moderator) {
        requirePlace(placeId);
        return jdbc.query("""
                SELECT r.id, r.place_id, r.author_id, u.display_name, r.rating, r.comment, r.created_at, r.updated_at,
                       r.is_hidden
                FROM reviews r JOIN users u ON u.id = r.author_id
                WHERE r.place_id = ? AND (NOT r.is_hidden OR ? OR r.author_id = ?)
                ORDER BY (r.author_id = ?) DESC, r.created_at DESC
                LIMIT 200
                """, (rs, i) -> {
                    long author = rs.getLong("author_id");
                    java.sql.Timestamp updated = rs.getTimestamp("updated_at");
                    return new ReviewDto(rs.getLong("id"), rs.getLong("place_id"), author, rs.getString("display_name"),
                            rs.getInt("rating"), rs.getString("comment"), rs.getTimestamp("created_at").toInstant(),
                            updated == null ? null : updated.toInstant(), rs.getBoolean("is_hidden"),
                            viewerId != null && viewerId == author);
                }, placeId, moderator, viewerId == null ? -1L : viewerId, viewerId == null ? -1L : viewerId);
    }

    /** Хэрэглэгчийн тухайн газарт өгөх үнэлгээг үүсгэнэ, аль хэдийн байвал засна. */
    @Transactional
    public ReviewDto write(long userId, long placeId, int rating, String comment) {
        requirePlace(placeId);
        String text = comment == null || comment.isBlank() ? null : comment.strip();
        Long id = jdbc.queryForObject("""
                INSERT INTO reviews (place_id, author_id, rating, comment) VALUES (?, ?, ?, ?)
                ON CONFLICT (place_id, author_id)
                DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, updated_at = now()
                RETURNING id
                """, Long.class, placeId, userId, rating, text);
        refreshAverage(placeId);
        return byId(id, userId);
    }

    @Transactional
    public void deleteOwn(long userId, long placeId) {
        if (jdbc.update("DELETE FROM reviews WHERE place_id = ? AND author_id = ?", placeId, userId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Сэтгэгдэл олдсонгүй");
        }
        refreshAverage(placeId);
    }

    /** Модератор: нуух эсвэл дахин харуулах. Нуусан үнэлгээг дунджид тооцохгүй. */
    @Transactional
    public ReviewDto setHidden(long moderatorId, long reviewId, boolean hidden) {
        List<Long> place = jdbc.queryForList("""
                UPDATE reviews SET is_hidden = ?, hidden_by = CASE WHEN ? THEN ?::bigint END,
                                   hidden_at = CASE WHEN ? THEN now() END
                WHERE id = ? RETURNING place_id
                """, Long.class, hidden, hidden, moderatorId, hidden, reviewId);
        if (place.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Сэтгэгдэл олдсонгүй");
        refreshAverage(place.getFirst());
        return byId(reviewId, moderatorId);
    }

    /** places.average_rating / review_count-ийг жагсаалт хурдан гаргах, машин сургалтын шинж чанарт зориулан давхардуулж хадгална. */
    private void refreshAverage(long placeId) {
        jdbc.update("""
                UPDATE places p SET average_rating = coalesce(s.avg, 0), review_count = coalesce(s.n, 0)
                FROM (SELECT round(avg(rating)::numeric, 2) AS avg, count(*) AS n
                      FROM reviews WHERE place_id = ? AND NOT is_hidden) s
                WHERE p.id = ?
                """, placeId, placeId);
    }

    private ReviewDto byId(long id, long viewerId) {
        return jdbc.queryForObject("""
                SELECT r.id, r.place_id, r.author_id, u.display_name, r.rating, r.comment, r.created_at, r.updated_at,
                       r.is_hidden
                FROM reviews r JOIN users u ON u.id = r.author_id WHERE r.id = ?
                """, (rs, i) -> {
                    java.sql.Timestamp updated = rs.getTimestamp("updated_at");
                    return new ReviewDto(rs.getLong("id"), rs.getLong("place_id"), rs.getLong("author_id"),
                            rs.getString("display_name"), rs.getInt("rating"), rs.getString("comment"),
                            rs.getTimestamp("created_at").toInstant(), updated == null ? null : updated.toInstant(),
                            rs.getBoolean("is_hidden"), rs.getLong("author_id") == viewerId);
                }, id);
    }

    private void requirePlace(long placeId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM places WHERE id = ? AND is_active)",
                Boolean.class, placeId);
        if (!Boolean.TRUE.equals(exists)) throw new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Газар олдсонгүй");
    }

    /** ХШ08: устгагдсан хэрэглэгчийн сэтгэгдлийг (хувийн агуулга тул) устгаж, дунджийг дахин тооцно. */
    @EventListener
    void onUserDeleted(UserDeletedEvent e) {
        List<Long> places = jdbc.queryForList("DELETE FROM reviews WHERE author_id = ? RETURNING place_id",
                Long.class, e.userId());
        places.forEach(this::refreshAverage);
    }
}
