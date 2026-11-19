-- Contribution module (АШ03-АШ06, АШ18, СНШ10): users propose new places, edits and links; moderators review.

CREATE TABLE place_change_requests (
    id                 BIGSERIAL PRIMARY KEY,
    submitter_id       BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    reviewer_id        BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    target_place_id    BIGINT      REFERENCES places (id) ON DELETE CASCADE,  -- NULL for a new place until approved
    type               VARCHAR(15) NOT NULL CHECK (type IN ('CREATE_PLACE', 'UPDATE_PLACE', 'ADD_LINK')),
    status             VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    proposed_data      JSONB       NOT NULL,
    moderator_comment  VARCHAR(500),
    submitted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at        TIMESTAMPTZ
);
CREATE INDEX place_change_requests_status ON place_change_requests (status, submitted_at);
CREATE INDEX place_change_requests_submitter ON place_change_requests (submitter_id, submitted_at DESC);

CREATE TABLE rewards (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    request_id  BIGINT      UNIQUE REFERENCES place_change_requests (id) ON DELETE SET NULL,
    points      INT         NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE external_links (
    id            BIGSERIAL PRIMARY KEY,
    place_id      BIGINT       NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    url           VARCHAR(500) NOT NULL,
    type          VARCHAR(20)  NOT NULL CHECK (type IN ('WEBSITE', 'FACEBOOK', 'INSTAGRAM', 'REVIEW_ARTICLE', 'OTHER')),
    platform      VARCHAR(50),
    title         VARCHAR(200),
    is_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    submitted_by  BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (place_id, url)
);

-- СНШ10: fields changed through an approved request; the OSM re-import keeps their values
ALTER TABLE places ADD COLUMN user_edited_fields TEXT[] NOT NULL DEFAULT '{}';
