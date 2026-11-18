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

    private final JdbcTemplate jdbc;

    public PlaceQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String placesInBboxAsGeoJson(double minLon, double minLat, double maxLon, double maxLat, int limit) {
        return jdbc.queryForObject(PLACES_IN_BBOX_GEOJSON, String.class, minLon, minLat, maxLon, maxLat, limit);
    }

    public String categoryTreeAsJson() {
        return jdbc.queryForObject(CATEGORY_TREE_JSON, String.class);
    }
}
