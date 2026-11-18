-- Place module tables (design: table "Өгөгдлийн сангийн хүснэгтүүд", group "Газар").
-- Coordinates are WGS84 (EPSG:4326), the same as OpenStreetMap, GPS and Google Maps.

-- Staging schema filled by osm2pgsql (data/osm/places.lua); data/sql/load_places.sql copies it into places.
CREATE SCHEMA IF NOT EXISTS osm;

CREATE TABLE place_categories (
    id                BIGSERIAL PRIMARY KEY,
    parent_id         BIGINT REFERENCES place_categories (id),
    code              VARCHAR(40)  NOT NULL UNIQUE,   -- stable key used by the API and the ML features
    name_mn           VARCHAR(100) NOT NULL,
    name_en           VARCHAR(100) NOT NULL,
    is_recommendable  BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order        INT          NOT NULL DEFAULT 0
);

-- OSM tag (key=value) -> system category. When a place matches several tags the highest priority wins.
CREATE TABLE osm_tag_mappings (
    id           BIGSERIAL PRIMARY KEY,
    osm_key      VARCHAR(50) NOT NULL,
    osm_value    VARCHAR(50) NOT NULL,
    category_id  BIGINT      NOT NULL REFERENCES place_categories (id),
    priority     INT         NOT NULL DEFAULT 0,
    UNIQUE (osm_key, osm_value)
);

CREATE TABLE buildings (
    id                BIGSERIAL PRIMARY KEY,
    osm_type          VARCHAR(10)  NOT NULL CHECK (osm_type IN ('WAY', 'RELATION')),
    osm_id            BIGINT       NOT NULL,
    name              VARCHAR(255),
    type              VARCHAR(20)  NOT NULL CHECK (type IN ('RESIDENTIAL', 'OFFICE', 'SERVICE')),
    location          geometry(Point, 4326)        NOT NULL,
    area              geometry(MultiPolygon, 4326) NOT NULL,
    country           VARCHAR(100),
    city_or_aimag     VARCHAR(100),
    district_or_sum   VARCHAR(100),
    khoroo_or_bag     VARCHAR(100),
    street            VARCHAR(255),
    UNIQUE (osm_type, osm_id)
);
CREATE INDEX buildings_area_gist ON buildings USING GIST (area);

CREATE TABLE places (
    id                 BIGSERIAL PRIMARY KEY,
    osm_type           VARCHAR(10)  CHECK (osm_type IN ('NODE', 'WAY', 'RELATION')),
    osm_id             BIGINT,
    source             VARCHAR(10)  NOT NULL CHECK (source IN ('OSM', 'USER')),
    is_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    name_cyrillic      VARCHAR(255) NOT NULL,
    name_latin         VARCHAR(255),
    category_id        BIGINT       NOT NULL REFERENCES place_categories (id),
    building_id        BIGINT       REFERENCES buildings (id) ON DELETE SET NULL,
    phone              VARCHAR(100),
    location           geometry(Point, 4326) NOT NULL,
    area               geometry(MultiPolygon, 4326),
    country            VARCHAR(100),
    city_or_aimag      VARCHAR(100),
    district_or_sum    VARCHAR(100),
    khoroo_or_bag      VARCHAR(100),
    street             VARCHAR(255),
    price_level        VARCHAR(10)  CHECK (price_level IN ('FREE', 'LOW', 'MEDIUM', 'HIGH')),
    opening_hours_raw  VARCHAR(500),
    osm_tags           JSONB,                          -- all original OSM tags, for later features
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE, -- FALSE when removed from OSM (rows stay for history)
    average_rating     NUMERIC(3, 2) NOT NULL DEFAULT 0,
    review_count       INT          NOT NULL DEFAULT 0,
    last_synced_at     TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT places_osm_unique UNIQUE (osm_type, osm_id)
);
CREATE INDEX places_location_gist ON places USING GIST (location);
CREATE INDEX places_category_idx  ON places (category_id);
CREATE INDEX places_name_cyr_trgm ON places USING GIN (name_cyrillic gin_trgm_ops);
CREATE INDEX places_name_lat_trgm ON places USING GIN (name_latin gin_trgm_ops);

-- Mongolian Cyrillic -> Latin transliteration (АШ02: search by Latin spelling).
-- Multi-letter digraphs first, then single letters; keeps capitalisation.
CREATE FUNCTION mn_to_latin(input TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS
$$
SELECT translate(
    replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(
    replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(
    -- 'е' after a consonant is 'e' (музей -> muzei); elsewhere 'ye' (Ерөнхий -> Yerönkhii)
    regexp_replace(input, '([бвгджзйклмнпрстфхцчшщБВГДЖЗЙКЛМНПРСТФХЦЧШЩ])е', '\1e', 'g'),
    'Х', 'Kh'), 'х', 'kh'), 'Ц', 'Ts'), 'ц', 'ts'), 'Ч', 'Ch'), 'ч', 'ch'),
    'Ш', 'Sh'), 'ш', 'sh'), 'Щ', 'Sh'), 'щ', 'sh'), 'Ю', 'Yu'), 'ю', 'yu'),
    'Я', 'Ya'), 'я', 'ya'), 'Ё', 'Yo'), 'ё', 'yo'), 'Е', 'Ye'), 'е', 'ye'),
    'Ж', 'J'), 'ж', 'j'),
    'АаБбВвГгДдЗзИиЙйКкЛлМмНнОоӨөПпРрСсТтУуҮүФфЫыЭэЪъЬь',
    'AaBbVvGgDdZzIiIiKkLlMmNnOoÖöPpRrSsTtUuÜüFfYyEe'
)
$$;
