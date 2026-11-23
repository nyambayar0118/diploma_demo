package mn.diploma.routeplanner.contribution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mn.diploma.routeplanner.common.ApiException;
import mn.diploma.routeplanner.common.PriceLevel;
import mn.diploma.routeplanner.contribution.ContributionDtos.ChangeRequestDto;
import mn.diploma.routeplanner.contribution.ContributionDtos.ChangeRequestView;
import mn.diploma.routeplanner.contribution.ContributionDtos.LinkType;
import mn.diploma.routeplanner.contribution.ContributionDtos.PlaceSnapshot;
import mn.diploma.routeplanner.contribution.ContributionDtos.ProposedData;

/**
 * Хувь нэмэр (АШ03, АШ04, АШ06, АШ18): хэрэглэгч шинэ газар, засвар эсвэл холбоос санал болгоно; модератор
 * баталгаажуулна (өөрчлөлтийг хэрэглэж, урамшууллын оноо олгоно) эсвэл шалтгаантай татгалзана. Баталгаажсан
 * талбарын засварыг places.user_edited_fields-д тэмдэглэдэг тул OSM-ийг дахин импортлоход хадгалагдана (СНШ10).
 */
@Service
public class ContributionService {

    private static final String SELECT = """
            SELECT r.id, r.type, r.status, r.target_place_id, r.submitter_id, u.display_name AS submitter_name,
                   r.moderator_comment, r.submitted_at, r.reviewed_at, w.points,
                   r.proposed_data ->> 'nameCyrillic' AS d_name, r.proposed_data ->> 'nameLatin' AS d_name_latin,
                   r.proposed_data ->> 'categoryCode' AS d_category, (r.proposed_data ->> 'lat')::float8 AS d_lat,
                   (r.proposed_data ->> 'lng')::float8 AS d_lng, r.proposed_data ->> 'phone' AS d_phone,
                   r.proposed_data ->> 'openingHours' AS d_hours, r.proposed_data ->> 'street' AS d_street,
                   r.proposed_data ->> 'priceLevel' AS d_price, (r.proposed_data ->> 'permanentlyClosed')::boolean AS d_closed,
                   r.proposed_data ->> 'url' AS d_url, r.proposed_data ->> 'linkType' AS d_link_type,
                   r.proposed_data ->> 'linkTitle' AS d_link_title
            FROM place_change_requests r
            LEFT JOIN users u ON u.id = r.submitter_id
            LEFT JOIN rewards w ON w.request_id = r.id
            """;

    private final JdbcTemplate jdbc;
    private final RewardService rewards;

    public ContributionService(JdbcTemplate jdbc, RewardService rewards) {
        this.jdbc = jdbc;
        this.rewards = rewards;
    }

    @Transactional
    public ChangeRequestView submit(long userId, ChangeRequestDto dto) {
        ProposedData d = dto.data();
        switch (dto.type()) {
            case CREATE_PLACE -> {
                if (blank(d.nameCyrillic()) || blank(d.categoryCode()) || d.lat() == null || d.lng() == null) {
                    throw bad("Нэр, ангилал, байршил шаардлагатай");
                }
                requireCategory(d.categoryCode());
            }
            case UPDATE_PLACE -> {
                requireTarget(dto.targetPlaceId());
                boolean any = !blank(d.nameCyrillic()) || !blank(d.nameLatin()) || !blank(d.categoryCode())
                        || (d.lat() != null && d.lng() != null) || !blank(d.phone()) || !blank(d.openingHours())
                        || !blank(d.street()) || d.priceLevel() != null || Boolean.TRUE.equals(d.permanentlyClosed());
                if (!any) throw bad("Засах мэдээллээ оруулна уу");
                if (!blank(d.categoryCode())) requireCategory(d.categoryCode());
            }
            case ADD_LINK -> {
                requireTarget(dto.targetPlaceId());
                if (blank(d.url()) || d.linkType() == null) throw bad("Холбоос болон төрлийг оруулна уу");
            }
        }
        Long target = dto.type() == RequestType.CREATE_PLACE ? null : dto.targetPlaceId();
        Long id = jdbc.queryForObject("""
                INSERT INTO place_change_requests (submitter_id, target_place_id, type, proposed_data)
                VALUES (?, ?, ?, jsonb_strip_nulls(jsonb_build_object(
                    'nameCyrillic', ?::text, 'nameLatin', ?::text, 'categoryCode', ?::text,
                    'lat', ?::float8, 'lng', ?::float8, 'phone', ?::text, 'openingHours', ?::text,
                    'street', ?::text, 'priceLevel', ?::text, 'permanentlyClosed', ?::boolean,
                    'url', ?::text, 'linkType', ?::text, 'linkTitle', ?::text)))
                RETURNING id
                """, Long.class, userId, target, dto.type().name(),
                trim(d.nameCyrillic()), trim(d.nameLatin()), trim(d.categoryCode()), d.lat(), d.lng(), trim(d.phone()),
                trim(d.openingHours()), trim(d.street()), d.priceLevel() == null ? null : d.priceLevel().name(),
                d.permanentlyClosed(), trim(d.url()), d.linkType() == null ? null : d.linkType().name(),
                trim(d.linkTitle()));
        return byId(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestView> listByUser(long userId) {
        return query(SELECT + "WHERE r.submitter_id = ? ORDER BY r.submitted_at DESC LIMIT 100", userId);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestView> listByStatus(RequestStatus status) {
        String order = status == RequestStatus.PENDING ? "r.submitted_at" : "r.reviewed_at DESC";
        return query(SELECT + "WHERE r.status = ? ORDER BY " + order + " LIMIT 200", status.name());
    }

    @Transactional
    public ChangeRequestView approve(long moderatorId, long requestId, String comment) {
        ChangeRequestView r = pending(moderatorId, requestId);
        long placeId = applyChanges(r);
        jdbc.update("""
                UPDATE place_change_requests SET status = 'APPROVED', reviewer_id = ?, reviewed_at = now(),
                       moderator_comment = ?, target_place_id = ?
                WHERE id = ?
                """, moderatorId, trim(comment), placeId, requestId);
        if (r.submitterId() != null) {
            String name = jdbc.queryForObject("SELECT name_cyrillic FROM places WHERE id = ?", String.class, placeId);
            rewards.grantFor(r.submitterId(), requestId, r.type(), name);
        }
        return byId(requestId).orElseThrow();
    }

    @Transactional
    public ChangeRequestView reject(long moderatorId, long requestId, String comment) {
        if (blank(comment)) throw bad("Татгалзсан шалтгаанаа бичнэ үү");
        pending(moderatorId, requestId);
        jdbc.update("""
                UPDATE place_change_requests SET status = 'REJECTED', reviewer_id = ?, reviewed_at = now(), moderator_comment = ?
                WHERE id = ?
                """, moderatorId, comment.strip(), requestId);
        return byId(requestId).orElseThrow();
    }

    /** Баталгаажсан хүсэлтийг газрын мэдээлэлд хэрэглэнэ; газрын id-г буцаана. */
    private long applyChanges(ChangeRequestView r) {
        ProposedData d = r.data();
        switch (r.type()) {
            case CREATE_PLACE -> {
                return jdbc.queryForObject("""
                        INSERT INTO places (source, is_verified, name_cyrillic, name_latin, category_id, phone, location,
                                            country, city_or_aimag, street, price_level, opening_hours_raw, building_id)
                        SELECT 'USER', TRUE, ?, ?, c.id, ?, pt.geom, 'Монгол', 'Улаанбаатар', ?, ?, ?,
                               (SELECT b.id FROM buildings b WHERE ST_Contains(b.area, pt.geom) LIMIT 1)
                        FROM place_categories c, (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geom) pt
                        WHERE c.code = ?
                        RETURNING id
                        """, Long.class, d.nameCyrillic(), d.nameLatin(), d.phone(), d.street(),
                        d.priceLevel() == null ? null : d.priceLevel().name(), d.openingHours(), d.lng(), d.lat(),
                        d.categoryCode());
            }
            case UPDATE_PLACE -> {
                long placeId = r.target().id();
                List<String> sets = new ArrayList<>();
                List<Object> args = new ArrayList<>();
                List<String> fields = new ArrayList<>();
                set(sets, args, fields, "name_cyrillic", d.nameCyrillic());
                set(sets, args, fields, "name_latin", d.nameLatin());
                set(sets, args, fields, "phone", d.phone());
                set(sets, args, fields, "opening_hours_raw", d.openingHours());
                set(sets, args, fields, "street", d.street());
                set(sets, args, fields, "price_level", d.priceLevel() == null ? null : d.priceLevel().name());
                if (!blank(d.categoryCode())) {
                    sets.add("category_id = (SELECT id FROM place_categories WHERE code = ?)");
                    args.add(d.categoryCode());
                    fields.add("category_id");
                }
                if (d.lat() != null && d.lng() != null) {
                    sets.add("location = ST_SetSRID(ST_MakePoint(?, ?), 4326)");
                    args.add(d.lng());
                    args.add(d.lat());
                    fields.add("location");
                }
                if (Boolean.TRUE.equals(d.permanentlyClosed())) {
                    sets.add("is_active = FALSE");
                    fields.add("is_active");
                }
                sets.add("user_edited_fields = ARRAY(SELECT DISTINCT unnest(user_edited_fields || ?::text[]))");
                args.add(fields.toArray(String[]::new));
                args.add(placeId);
                jdbc.update("UPDATE places SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
                return placeId;
            }
            case ADD_LINK -> {
                long placeId = r.target().id();
                jdbc.update("""
                        INSERT INTO external_links (place_id, url, type, platform, title, is_verified, submitted_by)
                        VALUES (?, ?, ?, ?, ?, TRUE, ?)
                        ON CONFLICT (place_id, url) DO UPDATE
                        SET type = EXCLUDED.type, title = EXCLUDED.title, is_verified = TRUE
                        """, placeId, d.url(), d.linkType().name(), platform(d.url()), d.linkTitle(), r.submitterId());
                return placeId;
            }
        }
        throw new IllegalStateException();
    }

    private ChangeRequestView pending(long moderatorId, long requestId) {
        ChangeRequestView r = byId(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Хүсэлт олдсонгүй"));
        if (r.status() != RequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "Хүсэлтийг аль хэдийн хянасан");
        }
        if (r.submitterId() != null && r.submitterId() == moderatorId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWN_REQUEST", "Өөрийн хүсэлтийг хянах боломжгүй");
        }
        if (r.type() != RequestType.CREATE_PLACE && r.target() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PLACE_GONE", "Газар устгагдсан байна");
        }
        return r;
    }

    private Optional<ChangeRequestView> byId(long id) {
        return query(SELECT + "WHERE r.id = ?", id).stream().findFirst();
    }

    private List<ChangeRequestView> query(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> view(rs), args);
    }

    private ChangeRequestView view(ResultSet rs) throws SQLException {
        String price = rs.getString("d_price");
        String linkType = rs.getString("d_link_type");
        ProposedData d = new ProposedData(rs.getString("d_name"), rs.getString("d_name_latin"), rs.getString("d_category"),
                rs.getObject("d_lat", Double.class), rs.getObject("d_lng", Double.class), rs.getString("d_phone"),
                rs.getString("d_hours"), rs.getString("d_street"), price == null ? null : PriceLevel.valueOf(price),
                rs.getObject("d_closed", Boolean.class), rs.getString("d_url"),
                linkType == null ? null : LinkType.valueOf(linkType), rs.getString("d_link_title"));
        Long target = rs.getObject("target_place_id", Long.class);
        Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new ChangeRequestView(rs.getLong("id"), RequestType.valueOf(rs.getString("type")),
                RequestStatus.valueOf(rs.getString("status")), d, target == null ? null : snapshot(target),
                rs.getObject("submitter_id", Long.class), rs.getString("submitter_name"),
                rs.getString("moderator_comment"), rs.getTimestamp("submitted_at").toInstant(),
                reviewed == null ? null : reviewed.toInstant(), rs.getObject("points", Integer.class));
    }

    /** Газрын одоогийн утгууд; модератор юу өөрчлөгдөхийг харьцуулж харна. */
    private PlaceSnapshot snapshot(long placeId) {
        return jdbc.query("""
                SELECT p.id, p.name_cyrillic, p.name_latin, c.code, c.name_mn, ST_Y(p.location), ST_X(p.location),
                       p.phone, p.opening_hours_raw, p.street, p.price_level, p.is_active
                FROM places p JOIN place_categories c ON c.id = p.category_id WHERE p.id = ?
                """, (rs, i) -> new PlaceSnapshot(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getObject(6, Double.class), rs.getObject(7, Double.class), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getBoolean(12)), placeId)
                .stream().findFirst().orElse(null);
    }

    private void requireTarget(Long placeId) {
        Boolean ok = placeId != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM places WHERE id = ? AND is_active)", Boolean.class, placeId));
        if (!ok) throw new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Газар олдсонгүй");
    }

    private void requireCategory(String code) {
        Boolean ok = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM place_categories WHERE code = ? AND parent_id IS NOT NULL)", Boolean.class, code);
        if (!Boolean.TRUE.equals(ok)) throw bad("Ангилал буруу байна");
    }

    private static void set(List<String> sets, List<Object> args, List<String> fields, String column, String value) {
        if (blank(value)) return;
        sets.add(column + " = ?");
        args.add(value.strip());
        fields.add(column);
    }

    static String platform(String url) {
        String u = url.toLowerCase();
        if (u.contains("facebook.com") || u.contains("fb.com")) return "Facebook";
        if (u.contains("instagram.com")) return "Instagram";
        if (u.contains("tiktok.com")) return "TikTok";
        if (u.contains("youtube.com") || u.contains("youtu.be")) return "YouTube";
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.strip();
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
