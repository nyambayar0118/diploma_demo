package mn.diploma.routeplanner.place;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Газрын модулийн нийтийн сервис: бусад модульд хэрэгтэй ангиллын мэдээлэл. */
@Service
public class CategoryLookup {

    /** Санал болгож болох дэд ангилал болон түүний харьяалагдах үндсэн ангилал. */
    public record Subcategory(long id, String code, long mainId, String mainCode) {
    }

    private final JdbcTemplate jdbc;

    public CategoryLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Идэвхтэй газрын ангиллын id, газар байгаа бол. */
    public java.util.Optional<Long> categoryIdOfPlace(long placeId) {
        return jdbc.query("SELECT category_id FROM places WHERE id = ? AND is_active",
                (rs, i) -> rs.getLong(1), placeId).stream().findFirst();
    }

    public List<Subcategory> recommendableSubcategories() {
        return jdbc.query("""
                SELECT c.id, c.code, m.id AS main_id, m.code AS main_code
                FROM place_categories c
                JOIN place_categories m ON m.id = c.parent_id
                WHERE c.is_recommendable
                ORDER BY m.sort_order, c.sort_order
                """,
                (rs, i) -> new Subcategory(rs.getLong("id"), rs.getString("code"),
                        rs.getLong("main_id"), rs.getString("main_code")));
    }
}
