-- Interaction module: saved / hidden places and the full interaction log (training data for the ML model).

CREATE TABLE place_marks (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    place_id    BIGINT      NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    type        VARCHAR(10) NOT NULL CHECK (type IN ('SAVED', 'HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, place_id)
);

CREATE TABLE user_interactions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      REFERENCES users (id) ON DELETE SET NULL,  -- NULL after account deletion (ХШ08)
    place_id           BIGINT      REFERENCES places (id) ON DELETE SET NULL,
    type               VARCHAR(20) NOT NULL CHECK (type IN ('VIEW', 'ACCEPT', 'REJECT', 'SAVE', 'UNSAVE', 'HIDE',
                                                            'UNHIDE', 'ROUTE_MODIFY', 'OPEN_IN_MAPS', 'VISIT')),
    recommendation_id  BIGINT,     -- foreign keys added with the recommendation / routing tables
    route_id           BIGINT,
    user_location      geometry(Point, 4326),
    timestamp          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX user_interactions_user_time ON user_interactions (user_id, timestamp);

-- Search (АШ01, АШ02): Latin transliteration of the Cyrillic name, so "kino" also finds "кино театр"
-- even when the place's Latin name is an English one ("... Cinema"). ö and ü are folded to "u",
-- the way Mongolian is usually typed in Latin letters (Өргөө -> urguu, Мөнгөн -> mungun).
CREATE FUNCTION mn_search_latin(input TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS
$$ SELECT lower(translate(mn_to_latin(input), 'ÖöÜü', 'uuuu')) $$;

ALTER TABLE places ADD COLUMN name_translit VARCHAR(255) GENERATED ALWAYS AS (mn_search_latin(name_cyrillic)) STORED;
CREATE INDEX places_name_translit_trgm ON places USING GIN (name_translit gin_trgm_ops);
