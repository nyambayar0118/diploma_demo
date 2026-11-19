-- Reviews (АШ16) and moderation (АШ17). One review per user and place; writing again edits it.
CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    place_id    BIGINT      NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    author_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    is_hidden   BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden_by   BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    hidden_at   TIMESTAMPTZ,
    UNIQUE (place_id, author_id)
);
CREATE INDEX reviews_place_time ON reviews (place_id, created_at DESC);
