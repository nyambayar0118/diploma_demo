-- Loads the Minu Map download (data/minu/fetch.py -> companies.jsonl) into places.
--   * only companies inside the Ulaanbaatar box whose category maps to ours (minu_category_mappings)
--   * a Minu company that is the same place as an existing OSM/user place (similar name within 75 m)
--     only fills that place's empty fields and adds its website; nothing OSM has is overwritten
--   * every other company becomes a place with source = 'MINU'
-- Safe to run repeatedly. The download is read from standard input:
--   psql -f load_minu.sql < /data/raw/minu/companies.jsonl
\set ON_ERROR_STOP on

CREATE SCHEMA IF NOT EXISTS minu;
DROP TABLE IF EXISTS minu.raw;
CREATE TABLE minu.raw (doc JSONB);
-- One JSON document per line; the odd quote/delimiter characters make COPY take each line as is
\copy minu.raw (doc) FROM pstdin WITH (FORMAT csv, QUOTE E'\x01', DELIMITER E'\x02')

-- Minu "days" = 7 ranges, Monday first ("09:00-20:00;...;11:00-17:00") -> OSM opening_hours.
-- "00:00-00:00" means open all day; an empty day is closed; no times at all -> unknown (NULL).
CREATE OR REPLACE FUNCTION pg_temp.minu_hours(days TEXT) RETURNS TEXT
    LANGUAGE plpgsql IMMUTABLE AS
$$
DECLARE
    names  TEXT[] := ARRAY['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];
    parts  TEXT[];
    vals   TEXT[] := '{}';
    rules  TEXT[] := '{}';
    v      TEXT;
    i      INT;
    j      INT;
    known  BOOLEAN := FALSE;
BEGIN
    IF days IS NULL THEN RETURN NULL; END IF;
    parts := string_to_array(days, ';');
    IF coalesce(array_length(parts, 1), 0) <> 7 THEN RETURN NULL; END IF;
    FOR i IN 1..7 LOOP
        v := trim(parts[i]);
        IF v ~ '^\d{2}:\d{2}-\d{2}:\d{2}$' THEN
            known := TRUE;
            IF v IN ('00:00-00:00', '00:00-24:00') THEN v := '00:00-24:00'; END IF;
            IF split_part(v, '-', 1) = split_part(v, '-', 2) THEN v := 'off'; END IF;
        ELSE
            v := 'off';
        END IF;
        vals := vals || v;
    END LOOP;
    IF NOT known THEN RETURN NULL; END IF;
    IF vals = ARRAY_FILL('00:00-24:00'::TEXT, ARRAY[7]) THEN RETURN '24/7'; END IF;
    -- Merge runs of identical days: "Mo-Fr 09:00-20:00; Sa-Su 11:00-17:00"
    i := 1;
    WHILE i <= 7 LOOP
        j := i;
        WHILE j < 7 AND vals[j + 1] = vals[i] LOOP j := j + 1; END LOOP;
        rules := rules || (CASE WHEN j > i THEN names[i] || '-' || names[j] ELSE names[i] END || ' ' || vals[i]);
        i := j + 1;
    END LOOP;
    RETURN array_to_string(rules, '; ');
END
$$;

BEGIN;

-- 1. One row per Minu company, cleaned up
CREATE TEMP TABLE mc ON COMMIT DROP AS
WITH latest AS (
    SELECT DISTINCT ON (doc ->> 'companyId') doc
    FROM minu.raw
    WHERE doc ->> 'companyId' IS NOT NULL
    ORDER BY doc ->> 'companyId', (doc ->> '_version_')::NUMERIC DESC NULLS LAST
), base AS (
    SELECT doc ->> 'companyId' AS minu_id, doc,
           nullif(trim(split_part(doc ->> 'companyShortName', ';', 1)), '')    AS short_name,
           nullif(trim(split_part(doc ->> 'companyOfficialName', ';', 1)), '') AS official_name,
           nullif(trim(doc ->> 'companyDisplayName'), '')                        AS display_name,
           nullif(trim(doc ->> 'companyNameEng'), '')                            AS name_eng,
           nullif(trim(split_part(doc ->> 'galigShortName', ';', 1)), '')        AS galig,
           CASE WHEN doc ->> 'x' ~ '^\d+(\.\d+)?$' THEN (doc ->> 'x')::FLOAT8 END AS lon,
           CASE WHEN doc ->> 'y' ~ '^\d+(\.\d+)?$' THEN (doc ->> 'y')::FLOAT8 END AS lat,
           nullif(trim(doc ->> 'addressDescription'), '') AS addr
    FROM latest
), named AS (
    -- Display name: the first of short / official / display name that is written in Cyrillic
    SELECT n.*,
           coalesce(CASE WHEN n.short_name ~ '[А-Яа-яӨөҮүЁё]' THEN n.short_name END,
                    CASE WHEN n.official_name ~ '[А-Яа-яӨөҮүЁё]' THEN n.official_name END,
                    CASE WHEN n.display_name ~ '[А-Яа-яӨөҮүЁё]' THEN n.display_name END,
                    n.short_name, n.official_name, n.display_name) AS name_cyr
    FROM base n
)
SELECT b.minu_id, b.doc, b.lon, b.lat, b.name_eng,
       ST_SetSRID(ST_MakePoint(b.lon, b.lat), 4326) AS location,
       b.name_cyr,
       coalesce(b.name_eng, initcap(b.galig), mn_to_latin(b.name_cyr)) AS name_lat,
       cat.category_id,
       pg_temp.minu_hours(b.doc ->> 'days') AS hours,
       CASE WHEN b.doc ->> 'web' ~* '^https?://[^ ]+\.[^ ]+$' THEN left(b.doc ->> 'web', 500) END AS website,
       CASE WHEN split_part(b.addr, ',', 1) ~ 'дүүрэг' THEN trim(split_part(b.addr, ',', 1)) END AS district,
       CASE WHEN split_part(b.addr, ',', 2) ~ 'хороо' THEN trim(split_part(b.addr, ',', 2)) END AS khoroo,
       left(nullif(trim(regexp_replace(b.addr, '^[^,]*дүүрэг,\s*[^,]*хороо,?\s*', '')), ''), 255) AS street,
       coalesce(b.doc ->> 'status', '') = 'approved' AS verified
FROM named b
CROSS JOIN LATERAL (
    SELECT m.category_id
    FROM jsonb_array_elements_text(CASE WHEN jsonb_typeof(b.doc -> 'categoryNameEng') = 'array'
                                        THEN b.doc -> 'categoryNameEng' ELSE '[]'::JSONB END) AS c(name)
    JOIN minu_category_mappings m ON trim(c.name) ~* m.pattern
    ORDER BY m.priority DESC, m.id
    LIMIT 1
) cat
WHERE b.lon BETWEEN 106.55 AND 107.25 AND b.lat BETWEEN 47.75 AND 48.05
  AND b.name_cyr IS NOT NULL
  AND b.name_cyr !~* '(^|[^[:alpha:]])(тест|test)([^[:alpha:]]|$)'
  AND coalesce(b.display_name, '') !~* '(^|[^[:alpha:]])(тест|test)([^[:alpha:]]|$)';

CREATE INDEX ON mc USING GIST (location);

-- 2. Same place already on the map? Best name match within 75 m, one Minu company per place
CREATE TEMP TABLE candidates ON COMMIT DROP AS
SELECT mc.minu_id, p.id AS place_id, p.score, p.dist
FROM mc
JOIN LATERAL (
    SELECT p.id,
           greatest(similarity(lower(p.name_cyrillic), lower(mc.name_cyr)),
                    similarity(lower(coalesce(p.name_latin, '')), lower(coalesce(mc.name_lat, '')))) AS score,
           ST_Distance(p.location::geography, mc.location::geography) AS dist
    FROM places p
    WHERE p.source <> 'MINU' AND p.is_active
      AND ST_DWithin(p.location::geography, mc.location::geography, 75)
) p ON p.score >= 0.35;

CREATE TEMP TABLE matched ON COMMIT DROP AS
SELECT minu_id, place_id FROM (
    SELECT c.*,
           row_number() OVER (PARTITION BY c.minu_id ORDER BY c.score DESC, c.dist) AS by_company,
           row_number() OVER (PARTITION BY c.place_id ORDER BY c.score DESC, c.dist) AS by_place
    FROM candidates c
) r
WHERE by_company = 1 AND by_place = 1;

-- A Minu-only place that OSM has since added: retire the Minu copy, the OSM place takes over the link
UPDATE places p SET is_active = FALSE, minu_id = NULL
FROM matched m
WHERE p.source = 'MINU' AND p.minu_id = m.minu_id;

-- Links that no longer hold are released before new ones are set (minu_id is unique)
UPDATE places p SET minu_id = NULL
WHERE p.source <> 'MINU' AND p.minu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM matched m WHERE m.place_id = p.id AND m.minu_id = p.minu_id);

-- 3. Enrich the matched places: only empty fields are filled, user-approved edits are kept
UPDATE places p SET
    minu_id           = mc.minu_id,
    minu_data         = mc.doc,
    opening_hours_raw = CASE WHEN p.opening_hours_raw IS NULL
                              AND NOT ('opening_hours_raw' = ANY (p.user_edited_fields))
                             THEN mc.hours ELSE p.opening_hours_raw END,
    name_latin        = CASE WHEN p.osm_tags ->> 'name:en' IS NULL AND mc.name_eng IS NOT NULL
                              AND NOT ('name_latin' = ANY (p.user_edited_fields))
                             THEN left(mc.name_eng, 255) ELSE p.name_latin END,
    district_or_sum   = coalesce(p.district_or_sum, mc.district),
    khoroo_or_bag     = coalesce(p.khoroo_or_bag, mc.khoroo)
FROM matched m
JOIN mc ON mc.minu_id = m.minu_id
WHERE p.id = m.place_id;

-- 4. Everything else becomes a Minu place
INSERT INTO places (source, is_verified, name_cyrillic, name_latin, category_id, location,
                    country, city_or_aimag, district_or_sum, khoroo_or_bag, street,
                    opening_hours_raw, minu_id, minu_data, is_active, last_synced_at)
SELECT 'MINU', mc.verified, left(mc.name_cyr, 255), left(mc.name_lat, 255), mc.category_id, mc.location,
       'Монгол', 'Улаанбаатар', mc.district, mc.khoroo, mc.street,
       mc.hours, mc.minu_id, mc.doc, TRUE, now()
FROM mc
WHERE NOT EXISTS (SELECT 1 FROM matched m WHERE m.minu_id = mc.minu_id)
ON CONFLICT (minu_id) WHERE minu_id IS NOT NULL DO UPDATE SET
    is_verified       = EXCLUDED.is_verified,
    name_cyrillic     = CASE WHEN 'name_cyrillic' = ANY (places.user_edited_fields) THEN places.name_cyrillic ELSE EXCLUDED.name_cyrillic END,
    name_latin        = CASE WHEN 'name_latin' = ANY (places.user_edited_fields) THEN places.name_latin ELSE EXCLUDED.name_latin END,
    category_id       = CASE WHEN 'category_id' = ANY (places.user_edited_fields) THEN places.category_id ELSE EXCLUDED.category_id END,
    location          = CASE WHEN 'location' = ANY (places.user_edited_fields) THEN places.location ELSE EXCLUDED.location END,
    district_or_sum   = EXCLUDED.district_or_sum,
    khoroo_or_bag     = EXCLUDED.khoroo_or_bag,
    street            = CASE WHEN 'street' = ANY (places.user_edited_fields) THEN places.street ELSE EXCLUDED.street END,
    opening_hours_raw = CASE WHEN 'opening_hours_raw' = ANY (places.user_edited_fields) THEN places.opening_hours_raw ELSE EXCLUDED.opening_hours_raw END,
    minu_data         = EXCLUDED.minu_data,
    is_active         = CASE WHEN 'is_active' = ANY (places.user_edited_fields) THEN places.is_active ELSE TRUE END,
    last_synced_at    = EXCLUDED.last_synced_at;

-- Minu places that dropped out of Minu (or out of our categories) are hidden, not deleted
UPDATE places p SET is_active = FALSE
WHERE p.source = 'MINU' AND p.is_active
  AND NOT ('is_active' = ANY (p.user_edited_fields))
  AND NOT EXISTS (SELECT 1 FROM mc WHERE mc.minu_id = p.minu_id);

-- 5. Websites (ExternalLink, verified because they come from the source)
INSERT INTO external_links (place_id, url, type, is_verified)
SELECT p.id, mc.website, 'WEBSITE', TRUE
FROM places p JOIN mc ON mc.minu_id = p.minu_id
WHERE mc.website IS NOT NULL
ON CONFLICT (place_id, url) DO NOTHING;

-- 6. Buildings for the new Minu places (smallest building containing the point)
UPDATE places p SET building_id = (
    SELECT b.id FROM buildings b
    WHERE ST_Contains(b.area, p.location)
    ORDER BY ST_Area(b.area) LIMIT 1)
WHERE p.source = 'MINU' AND p.building_id IS NULL;

COMMIT;

-- Summary
SELECT count(*) AS "Minu: компани (нийт)" FROM minu.raw;
SELECT count(*) AS "Minu: Улаанбаатар, ангилал таарсан" FROM places WHERE minu_id IS NOT NULL OR source = 'MINU';
SELECT count(*) AS "OSM газрыг баяжуулсан" FROM places WHERE source <> 'MINU' AND minu_id IS NOT NULL;
SELECT count(*) AS "Шинээр нэмэгдсэн Minu газар" FROM places WHERE source = 'MINU' AND is_active;
SELECT c.code AS "Ангилал", count(*) AS "Minu газар"
FROM places p JOIN place_categories c ON c.id = p.category_id
WHERE p.source = 'MINU' AND p.is_active
GROUP BY c.code ORDER BY 2 DESC;
-- Most common Minu categories in Ulaanbaatar that map to nothing: candidates for minu_category_mappings
SELECT trim(c.name) AS "Тохироогүй Minu ангилал", count(*) AS "Компани"
FROM minu.raw r
CROSS JOIN LATERAL jsonb_array_elements_text(CASE WHEN jsonb_typeof(r.doc -> 'categoryNameEng') = 'array'
                                                  THEN r.doc -> 'categoryNameEng' ELSE '[]'::JSONB END) AS c(name)
WHERE CASE WHEN r.doc ->> 'x' ~ '^\d+(\.\d+)?$' THEN (r.doc ->> 'x')::FLOAT8 END BETWEEN 106.55 AND 107.25
  AND CASE WHEN r.doc ->> 'y' ~ '^\d+(\.\d+)?$' THEN (r.doc ->> 'y')::FLOAT8 END BETWEEN 47.75 AND 48.05
  AND NOT EXISTS (SELECT 1 FROM minu_category_mappings m WHERE trim(c.name) ~* m.pattern)
GROUP BY 1 ORDER BY 2 DESC LIMIT 30;
