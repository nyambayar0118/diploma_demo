-- Additions after the first Ulaanbaatar import (most frequent unmapped OSM tags).
-- Statues and public art are recommendable as historic/cultural sights.
INSERT INTO osm_tag_mappings (osm_key, osm_value, category_id, priority)
SELECT 'tourism', 'artwork', id, 2 FROM place_categories WHERE code = 'historic';

-- Beauty salons and hairdressers: searchable, but not leisure places to recommend.
INSERT INTO place_categories (parent_id, code, name_mn, name_en, is_recommendable, sort_order)
SELECT id, 'beauty', 'Гоо сайхны салон', 'Beauty salon', FALSE, 2 FROM place_categories WHERE code = 'wellness';

INSERT INTO osm_tag_mappings (osm_key, osm_value, category_id, priority)
SELECT key, value, (SELECT id FROM place_categories WHERE code = 'beauty'), 10
FROM (VALUES ('shop', 'beauty'), ('shop', 'hairdresser')) AS t(key, value);
