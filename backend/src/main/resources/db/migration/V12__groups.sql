-- Group module (АШ10, АШ24): a plan with friends (and guests through an invite link) to meet at one place.

CREATE TABLE group_sessions (
    id                 BIGSERIAL PRIMARY KEY,
    status             VARCHAR(12) NOT NULL DEFAULT 'RECOMMENDED'
                       CHECK (status IN ('RECOMMENDED', 'SHARED', 'COMPLETED', 'CANCELLED')),
    time_budget_min    INT         NOT NULL CHECK (time_budget_min BETWEEN 30 AND 720),
    budget_level       VARCHAR(10) NOT NULL CHECK (budget_level IN ('FREE', 'LOW', 'MEDIUM', 'HIGH')),
    meeting_place_id   BIGINT      REFERENCES places (id),
    invite_token       VARCHAR(64) UNIQUE,
    invite_expires_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    shared_at          TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ
);

CREATE TABLE group_members (
    id                    BIGSERIAL PRIMARY KEY,
    session_id            BIGINT      NOT NULL REFERENCES group_sessions (id) ON DELETE CASCADE,
    user_id               BIGINT      REFERENCES users (id) ON DELETE SET NULL,   -- NULL for guests
    guest_name            VARCHAR(100),
    role                  VARCHAR(10) NOT NULL CHECK (role IN ('INITIATOR', 'MEMBER')),
    start_location        geometry(Point, 4326),
    start_source          VARCHAR(20) CHECK (start_source IN ('SHARED_LOCATION', 'PINNED', 'SAME_AS_INITIATOR')),
    transport_mode        VARCHAR(20) NOT NULL CHECK (transport_mode IN ('WALKING', 'DRIVING', 'BICYCLE', 'PUBLIC_TRANSPORT')),
    uses_interests        BOOLEAN     NOT NULL DEFAULT FALSE,
    estimated_travel_min  INT,
    joined_via_link       BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (user_id IS NOT NULL OR guest_name IS NOT NULL)
);
CREATE UNIQUE INDEX group_members_user ON group_members (session_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX group_members_by_user ON group_members (user_id);

ALTER TABLE routes
    ADD CONSTRAINT routes_group_session_fk FOREIGN KEY (group_session_id) REFERENCES group_sessions (id) ON DELETE SET NULL;
ALTER TABLE recommendation_batches
    ADD CONSTRAINT recommendation_batches_group_session_fk
        FOREIGN KEY (group_session_id) REFERENCES group_sessions (id) ON DELETE SET NULL;
