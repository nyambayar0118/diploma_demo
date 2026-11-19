-- Friend module (АШ20) and Location module (АШ21, ХШ07).

CREATE TABLE friendships (
    id            BIGSERIAL PRIMARY KEY,
    requester_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    CHECK (requester_id <> addressee_id)
);
-- One row per pair of users, whoever asked first
CREATE UNIQUE INDEX friendships_pair ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX friendships_addressee ON friendships (addressee_id, status);

-- Only the latest position is kept (no location history)
CREATE TABLE user_locations (
    user_id                 BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    location                geometry(Point, 4326) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_shared_with_friends  BOOLEAN     NOT NULL DEFAULT FALSE
);
