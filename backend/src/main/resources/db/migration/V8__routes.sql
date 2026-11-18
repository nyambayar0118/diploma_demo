-- Routing module: routes built from 1-4 chosen places, their stops and the satisfaction feedback (design 3.2, Маршрут).
-- The system does not draw the way itself: it orders the stops, estimates times and hands over to Google Maps.

CREATE TABLE routes (
    id                      BIGSERIAL PRIMARY KEY,
    owner_id                BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    group_session_id        BIGINT,     -- foreign key added with the group tables
    start_location          geometry(Point, 4326) NOT NULL,
    transport_mode          VARCHAR(20) NOT NULL CHECK (transport_mode IN ('WALKING', 'DRIVING', 'BICYCLE', 'PUBLIC_TRANSPORT')),
    time_budget_min         INT         NOT NULL CHECK (time_budget_min > 0),
    estimated_duration_min  INT         NOT NULL,
    planned_start           TIMESTAMP   NOT NULL,   -- local time (Asia/Ulaanbaatar) the route starts; arrivals are computed from it
    status                  VARCHAR(20) NOT NULL DEFAULT 'CREATED'
                            CHECK (status IN ('CREATED', 'OPENED_IN_MAPS', 'COMPLETED', 'CANCELLED')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    opened_at               TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ
);
CREATE INDEX routes_owner_status ON routes (owner_id, status);

CREATE TABLE route_stops (
    id                 BIGSERIAL PRIMARY KEY,
    route_id           BIGINT    NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    place_id           BIGINT    NOT NULL REFERENCES places (id),
    recommendation_id  BIGINT    REFERENCES recommendations (id) ON DELETE SET NULL,  -- links VISIT back to the ranked list
    stop_order         INT       NOT NULL CHECK (stop_order BETWEEN 1 AND 4),
    estimated_arrival  TIMESTAMP NOT NULL,
    stay_minutes       INT       NOT NULL CHECK (stay_minutes > 0),
    is_visited         BOOLEAN   NOT NULL DEFAULT FALSE,
    visited_at         TIMESTAMPTZ,
    skipped_at         TIMESTAMPTZ,              -- the user answered "did not go" to "Очсон уу?"
    CONSTRAINT route_stops_order_unique UNIQUE (route_id, stop_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT route_stops_place_unique UNIQUE (route_id, place_id)
);

CREATE TABLE satisfaction_feedbacks (
    id                 BIGSERIAL PRIMARY KEY,
    route_id           BIGINT      NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    user_id            BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    relevance          INT         NOT NULL CHECK (relevance BETWEEN 1 AND 5),
    time_suitability   INT         NOT NULL CHECK (time_suitability BETWEEN 1 AND 5),
    estimate_accuracy  INT         NOT NULL CHECK (estimate_accuracy BETWEEN 1 AND 5),
    submitted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (route_id, user_id)
);

ALTER TABLE user_interactions
    ADD CONSTRAINT user_interactions_route_fk FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE SET NULL;
