-- osm2pgsql-ийн завсрын хүснэгтүүдийг (osm схем) системийн хүснэгтүүд рүү хуулна.
-- Дахин дахин ажиллуулахад аюулгүй: байгаа мөрүүдийг шинэчилж, OSM-оос алга болсон
-- (эсвэл аль ч ангилалд таарахаа больсон) газрыг устгалгүй идэвхгүй болгоно.
\set ON_ERROR_STOP on
BEGIN;

-- 1. Газар: OSM объект бүрд хамгийн сайн таарах ангиллыг сонгоно (харгалзуулалтын хамгийн өндөр ач холбогдол)
CREATE TEMP TABLE matched ON COMMIT DROP AS
SELECT DISTINCT ON (p.osm_type, p.osm_id)
       CASE p.osm_type WHEN 'N' THEN 'NODE' WHEN 'W' THEN 'WAY' ELSE 'RELATION' END AS osm_type,
       p.osm_id, p.tags, p.location, p.area, m.category_id
FROM osm.pois p
JOIN osm_tag_mappings m ON p.tags ->> m.osm_key = m.osm_value
ORDER BY p.osm_type, p.osm_id, m.priority DESC, m.id;

INSERT INTO places (osm_type, osm_id, source, is_verified, name_cyrillic, name_latin, category_id,
                    phone, location, area, country, city_or_aimag, district_or_sum, khoroo_or_bag, street,
                    opening_hours_raw, osm_tags, is_active, last_synced_at)
SELECT osm_type, osm_id, 'OSM', TRUE,
       coalesce(tags ->> 'name:mn', tags ->> 'name'),
       coalesce(tags ->> 'name:en', mn_to_latin(coalesce(tags ->> 'name:mn', tags ->> 'name'))),
       category_id,
       left(coalesce(tags ->> 'phone', tags ->> 'contact:phone'), 100),
       location, area,
       'Монгол', coalesce(tags ->> 'addr:city', 'Улаанбаатар'),
       tags ->> 'addr:district', tags ->> 'addr:subdistrict',
       left(concat_ws(' ', tags ->> 'addr:street', tags ->> 'addr:housenumber'), 255),
       left(tags ->> 'opening_hours', 500), tags, TRUE, now()
FROM matched
ON CONFLICT ON CONSTRAINT places_osm_unique DO UPDATE SET
    -- СНШ10: модератор хэрэглэгчийн засварыг баталгаажуулсан талбар хэрэглэгчийн утгаа хадгална
    name_cyrillic     = CASE WHEN 'name_cyrillic' = ANY (places.user_edited_fields) THEN places.name_cyrillic ELSE EXCLUDED.name_cyrillic END,
    name_latin        = CASE WHEN 'name_latin' = ANY (places.user_edited_fields) THEN places.name_latin ELSE EXCLUDED.name_latin END,
    category_id       = CASE WHEN 'category_id' = ANY (places.user_edited_fields) THEN places.category_id ELSE EXCLUDED.category_id END,
    phone             = CASE WHEN 'phone' = ANY (places.user_edited_fields) THEN places.phone ELSE EXCLUDED.phone END,
    location          = CASE WHEN 'location' = ANY (places.user_edited_fields) THEN places.location ELSE EXCLUDED.location END,
    area              = EXCLUDED.area,
    city_or_aimag     = EXCLUDED.city_or_aimag,
    district_or_sum   = EXCLUDED.district_or_sum,
    khoroo_or_bag     = EXCLUDED.khoroo_or_bag,
    street            = CASE WHEN 'street' = ANY (places.user_edited_fields) THEN places.street ELSE EXCLUDED.street END,
    -- OSM-д ажиллах цаг байхгүй бол Minu Map-аас бөглөсөн цаг хэвээр үлдэнэ
    opening_hours_raw = CASE WHEN 'opening_hours_raw' = ANY (places.user_edited_fields) THEN places.opening_hours_raw
                             ELSE coalesce(EXCLUDED.opening_hours_raw, CASE WHEN places.minu_id IS NOT NULL THEN places.opening_hours_raw END) END,
    osm_tags          = EXCLUDED.osm_tags,
    is_active         = CASE WHEN 'is_active' = ANY (places.user_edited_fields) THEN places.is_active ELSE TRUE END,
    last_synced_at    = EXCLUDED.last_synced_at;

UPDATE places p SET is_active = FALSE
WHERE p.source = 'OSM' AND p.is_active
  AND NOT EXISTS (SELECT 1 FROM matched m WHERE m.osm_type = p.osm_type AND m.osm_id = p.osm_id);

-- 2. Барилга: нэртэй болон дор хаяж нэг газар агуулсан барилгыг үлдээнэ (ДШ07)
INSERT INTO buildings (osm_type, osm_id, name, type, location, area,
                       country, city_or_aimag, district_or_sum, khoroo_or_bag, street)
SELECT CASE b.osm_type WHEN 'W' THEN 'WAY' ELSE 'RELATION' END, b.osm_id,
       coalesce(b.tags ->> 'name:mn', b.tags ->> 'name'),
       CASE
           WHEN b.tags ->> 'building' IN ('apartments', 'residential', 'house', 'detached', 'dormitory',
                                          'terrace', 'semidetached_house', 'ger') THEN 'RESIDENTIAL'
           WHEN b.tags ->> 'building' IN ('office', 'commercial', 'government', 'public') THEN 'OFFICE'
           ELSE 'SERVICE'
       END,
       b.location, b.area,
       'Монгол', coalesce(b.tags ->> 'addr:city', 'Улаанбаатар'),
       b.tags ->> 'addr:district', b.tags ->> 'addr:subdistrict',
       nullif(left(concat_ws(' ', b.tags ->> 'addr:street', b.tags ->> 'addr:housenumber'), 255), '')
FROM osm.buildings b
WHERE b.tags ? 'name'
   OR EXISTS (SELECT 1 FROM places p WHERE p.is_active AND ST_Contains(b.area, p.location))
ON CONFLICT (osm_type, osm_id) DO UPDATE SET
    name = EXCLUDED.name, type = EXCLUDED.type, location = EXCLUDED.location, area = EXCLUDED.area,
    district_or_sum = EXCLUDED.district_or_sum, khoroo_or_bag = EXCLUDED.khoroo_or_bag,
    street = EXCLUDED.street;

-- 3. Газар бүрийг түүнийг агуулсан хамгийн жижиг барилгатай холбоно (цэг олон өнцөгт дотор)
UPDATE places p SET building_id = (
    SELECT b.id FROM buildings b
    WHERE ST_Contains(b.area, p.location)
    ORDER BY ST_Area(b.area) LIMIT 1)
WHERE p.source = 'OSM';

-- 4. Өөрийн хаяггүй газар барилгын хаягийг авна
UPDATE places p SET
    street          = coalesce(nullif(p.street, ''), b.street),
    district_or_sum = coalesce(p.district_or_sum, b.district_or_sum),
    khoroo_or_bag   = coalesce(p.khoroo_or_bag, b.khoroo_or_bag)
FROM buildings b
WHERE p.building_id = b.id;

UPDATE places SET street = NULL WHERE street = '';

COMMIT;

-- Хураангуй
SELECT main.name_mn AS "Ангилал", count(p.id) AS "Газрын тоо",
       count(p.id) FILTER (WHERE sub.is_recommendable) AS "Санал болгох"
FROM place_categories main
JOIN place_categories sub ON sub.parent_id = main.id
LEFT JOIN places p ON p.category_id = sub.id AND p.is_active
WHERE main.parent_id IS NULL
GROUP BY main.id, main.name_mn, main.sort_order
ORDER BY main.sort_order;

SELECT count(*) AS "Барилга" FROM buildings;
SELECT count(*) AS "Барилга дотор байрлах газар" FROM places WHERE building_id IS NOT NULL AND is_active;
