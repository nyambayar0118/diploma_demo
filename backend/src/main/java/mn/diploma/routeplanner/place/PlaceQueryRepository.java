package mn.diploma.routeplanner.place;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries for the map. PostgreSQL builds the JSON directly (json_build_object),
 * so large GeoJSON responses need no object mapping in Java.
 */
@Repository
public class PlaceQueryRepository {

    private static final String PLACES_IN_BBOX_GEOJSON = """
            SELECT json_build_object('type', 'FeatureCollection', 'features', coalesce(json_agg(
                       json_build_object('type', 'Feature', 'id', p.id,
                                         'geometry', ST_AsGeoJSON(p.location, 6)::json,
                                         'properties', json_build_object(
                                             'id', p.id, 'name', p.name_cyrillic, 'nameLatin', p.name_latin,
                                             'category', p.code, 'mainCategory', p.main_code,
                                             'recommendable', p.is_recommendable))), '[]'::json))::text
            FROM (SELECT pl.id, pl.location, pl.name_cyrillic, pl.name_latin,
                         c.code, m.code AS main_code, c.is_recommendable
                  FROM places pl
                  JOIN place_categories c ON c.id = pl.category_id
                  JOIN place_categories m ON m.id = c.parent_id
                  WHERE pl.is_active
                    AND pl.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
                  ORDER BY c.is_recommendable DESC, pl.id
                  LIMIT ?) p
            """;

    private static final String CATEGORY_TREE_JSON = """
            SELECT coalesce(json_agg(json_build_object(
                       'code', m.code, 'nameMn', m.name_mn, 'nameEn', m.name_en,
                       'children', (SELECT json_agg(json_build_object(
                                        'code', c.code, 'nameMn', c.name_mn, 'nameEn', c.name_en,
                                        'recommendable', c.is_recommendable) ORDER BY c.sort_order)
                                    FROM place_categories c WHERE c.parent_id = m.id))
                   ORDER BY m.sort_order), '[]'::json)::text
            FROM place_categories m
            WHERE m.parent_id IS NULL
            """;

    /** Ranked by name similarity (Cyrillic, Latin, transliterated), then by distance from the map centre. */
    private static final String SEARCH_JSON = """
            WITH q AS (SELECT lower(?) AS q, mn_search_latin(?) AS ql,
                              ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS center)
            SELECT coalesce(json_agg(r ORDER BY r.score DESC, r."distanceM" NULLS LAST), '[]'::json)::text
            FROM (SELECT p.id, p.name_cyrillic AS name, p.name_latin AS "nameLatin",
                         c.code AS category, c.name_mn AS "categoryName", m.code AS "mainCategory",
                         round(ST_Y(p.location)::numeric, 6) AS lat, round(ST_X(p.location)::numeric, 6) AS lng,
                         round(ST_Distance(p.location::geography, q.center)) AS "distanceM",
                         greatest(word_similarity(q.q, lower(p.name_cyrillic)),
                                  word_similarity(q.q, lower(coalesce(p.name_latin, ''))),
                                  word_similarity(q.ql, p.name_translit)) AS score
                  FROM places p
                  JOIN place_categories c ON c.id = p.category_id
                  JOIN place_categories m ON m.id = c.parent_id
                  CROSS JOIN q
                  WHERE p.is_active
                    AND (?::text IS NULL OR m.code = ?::text)
                    AND (lower(p.name_cyrillic) LIKE '%' || q.q || '%'
                         OR lower(coalesce(p.name_latin, '')) LIKE '%' || q.q || '%'
                         OR p.name_translit LIKE '%' || q.ql || '%'
                         OR q.q <% lower(p.name_cyrillic)
                         OR q.q <% lower(coalesce(p.name_latin, ''))
                         OR q.ql <% p.name_translit)
                  ORDER BY score DESC, "distanceM" NULLS LAST
                  LIMIT ?) r
            """;

    /** Full details of one place. The traditional-script name is any OSM tag value in Mongolian script. */
    private static final String DETAILS_JSON = """
            SELECT json_build_object(
                'id', p.id, 'name', p.name_cyrillic, 'nameLatin', p.name_latin,
                'nameTraditional', (SELECT t.value FROM jsonb_each_text(p.osm_tags) t
                                    WHERE t.value ~ '[\\u1800-\\u18AF]' LIMIT 1),
                'category', c.code, 'categoryName', c.name_mn, 'mainCategory', m.code, 'mainCategoryName', m.name_mn,
                'recommendable', c.is_recommendable,
                'lat', round(ST_Y(p.location)::numeric, 6), 'lng', round(ST_X(p.location)::numeric, 6),
                'address', nullif(concat_ws(', ', p.street, p.khoroo_or_bag, p.district_or_sum, p.city_or_aimag), ''),
                'building', b.name,
                'phone', p.phone,
                'website', coalesce((SELECT e.url FROM external_links e WHERE e.place_id = p.id AND e.is_verified
                                     AND e.type = 'WEBSITE' ORDER BY e.id DESC LIMIT 1),
                                    p.osm_tags ->> 'website', p.osm_tags ->> 'contact:website'),
                'facebook', coalesce((SELECT e.url FROM external_links e WHERE e.place_id = p.id AND e.is_verified
                                      AND e.type = 'FACEBOOK' ORDER BY e.id DESC LIMIT 1),
                                     p.osm_tags ->> 'contact:facebook', p.osm_tags ->> 'facebook'),
                'instagram', coalesce((SELECT e.url FROM external_links e WHERE e.place_id = p.id AND e.is_verified
                                       AND e.type = 'INSTAGRAM' ORDER BY e.id DESC LIMIT 1),
                                      p.osm_tags ->> 'contact:instagram', p.osm_tags ->> 'instagram'),
                'links', (SELECT coalesce(json_agg(json_build_object('url', e.url, 'type', e.type, 'title', e.title,
                                                                     'platform', e.platform) ORDER BY e.id), '[]'::json)
                          FROM external_links e WHERE e.place_id = p.id AND e.is_verified
                            AND e.type IN ('REVIEW_ARTICLE', 'OTHER')),
                'openingHours', p.opening_hours_raw,
                'priceLevel', p.price_level,
                'averageRating', p.average_rating, 'reviewCount', p.review_count,
                'source', p.source, 'fromMinu', p.minu_id IS NOT NULL)::text
            FROM places p
            JOIN place_categories c ON c.id = p.category_id
            JOIN place_categories m ON m.id = c.parent_id
            LEFT JOIN buildings b ON b.id = p.building_id
            WHERE p.id = ? AND p.is_active
            """;

    private static final String SUMMARIES_JSON = """
            SELECT coalesce(json_agg(json_build_object(
                       'id', p.id, 'name', p.name_cyrillic, 'nameLatin', p.name_latin,
                       'category', c.code, 'categoryName', c.name_mn, 'mainCategory', m.code,
                       'lat', round(ST_Y(p.location)::numeric, 6), 'lng', round(ST_X(p.location)::numeric, 6))
                   ORDER BY array_position(?::bigint[], p.id)), '[]'::json)::text
            FROM places p
            JOIN place_categories c ON c.id = p.category_id
            JOIN place_categories m ON m.id = c.parent_id
            WHERE p.id = ANY (?::bigint[])
            """;

    private final JdbcTemplate jdbc;

    public PlaceQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String placesInBboxAsGeoJson(double minLon, double minLat, double maxLon, double maxLat, int limit) {
        return jdbc.queryForObject(PLACES_IN_BBOX_GEOJSON, String.class, minLon, minLat, maxLon, maxLat, limit);
    }

    public String search(String query, String mainCategory, Double lat, Double lng, int limit) {
        return jdbc.queryForObject(SEARCH_JSON, String.class, query, query, lng, lat, mainCategory, mainCategory, limit);
    }

    /** JSON of the place, or null when it does not exist or is inactive. */
    public String detailsAsJson(long id) {
        return jdbc.query(DETAILS_JSON, rs -> rs.next() ? rs.getString(1) : null, id);
    }

    public String summariesAsJson(java.util.List<Long> ids) {
        Long[] array = ids.toArray(Long[]::new);
        return jdbc.queryForObject(SUMMARIES_JSON, String.class, array, array);
    }

    public boolean exists(long id) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM places WHERE id = ? AND is_active)",
                Boolean.class, id);
        return Boolean.TRUE.equals(found);
    }

    public String categoryTreeAsJson() {
        return jdbc.queryForObject(CATEGORY_TREE_JSON, String.class);
    }
}
