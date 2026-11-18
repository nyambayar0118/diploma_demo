-- Recommendation module. Every list shown to a user is stored (batch + ranked items): these rows,
-- joined with user_interactions, are the training data of the LightGBM LambdaRank model (one batch = one query group).

-- How suitable a category is at a time of day (0 = never recommend then, 1 = ideal).
CREATE TABLE category_time_priors (
    id           BIGSERIAL PRIMARY KEY,
    category_id  BIGINT       NOT NULL REFERENCES place_categories (id),
    time_of_day  VARCHAR(10)  NOT NULL CHECK (time_of_day IN ('MORNING', 'MIDDAY', 'AFTERNOON', 'EVENING', 'NIGHT')),
    day_type     VARCHAR(10)  NOT NULL CHECK (day_type IN ('WEEKDAY', 'WEEKEND')),
    suitability  DOUBLE PRECISION NOT NULL CHECK (suitability BETWEEN 0 AND 1),
    UNIQUE (category_id, time_of_day, day_type)
);

CREATE TABLE recommendation_batches (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    group_session_id   BIGINT,     -- foreign key added with the group tables; group batches are not used for training
    user_location      geometry(Point, 4326),   -- only with the LOCATION consent
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    available_minutes  INT         NOT NULL,
    budget_level       VARCHAR(10) NOT NULL CHECK (budget_level IN ('FREE', 'LOW', 'MEDIUM', 'HIGH')),
    transport_mode     VARCHAR(20) NOT NULL CHECK (transport_mode IN ('WALKING', 'DRIVING', 'BICYCLE', 'PUBLIC_TRANSPORT')),
    ranker_name        VARCHAR(30) NOT NULL,
    model_version      VARCHAR(50)
);
CREATE INDEX recommendation_batches_user_time ON recommendation_batches (user_id, requested_at);

CREATE TABLE recommendations (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        BIGINT           NOT NULL REFERENCES recommendation_batches (id) ON DELETE CASCADE,
    place_id        BIGINT           NOT NULL REFERENCES places (id),
    score           DOUBLE PRECISION NOT NULL,
    rank            INT              NOT NULL,
    is_exploration  BOOLEAN          NOT NULL DEFAULT FALSE,
    UNIQUE (batch_id, rank)
);

ALTER TABLE user_interactions
    ADD CONSTRAINT user_interactions_recommendation_fk FOREIGN KEY (recommendation_id) REFERENCES recommendations (id);

-- Seed: suitability per subcategory and time of day (same for weekdays and weekends in this first version).
-- Categories not listed get 0.7 during the day and 0.2 at night.
WITH priors (code, morning, midday, afternoon, evening, night) AS (VALUES
    ('cafe',        0.9, 0.8, 1.0, 0.7, 0.2),
    ('restaurant',  0.4, 1.0, 0.6, 1.0, 0.3),
    ('fast_food',   0.5, 1.0, 0.7, 0.8, 0.5),
    ('pub_bar',     0.0, 0.2, 0.4, 1.0, 0.9),
    ('dessert',     0.7, 0.8, 1.0, 0.7, 0.1),
    ('cinema',      0.3, 0.6, 0.8, 1.0, 0.6),
    ('game_center', 0.3, 0.6, 0.9, 1.0, 0.6),
    ('karaoke',     0.0, 0.2, 0.5, 1.0, 0.9),
    ('nightclub',   0.0, 0.0, 0.0, 0.6, 1.0),
    ('theme_park',  0.6, 0.9, 1.0, 0.5, 0.0),
    ('zoo',         0.7, 0.9, 1.0, 0.3, 0.0),
    ('museum',      0.8, 0.9, 1.0, 0.3, 0.0),
    ('gallery',     0.7, 0.9, 1.0, 0.4, 0.0),
    ('theatre',     0.1, 0.3, 0.5, 1.0, 0.2),
    ('library',     0.9, 0.9, 1.0, 0.5, 0.0),
    ('historic',    0.8, 0.9, 1.0, 0.5, 0.1),
    ('worship',     0.9, 0.8, 0.8, 0.3, 0.0),
    ('park',        0.8, 0.9, 1.0, 0.6, 0.1),
    ('scenic',      0.8, 0.9, 1.0, 0.6, 0.0),
    ('picnic',      0.6, 1.0, 1.0, 0.4, 0.0),
    ('fitness',     1.0, 0.7, 0.8, 1.0, 0.2),
    ('sports_hall', 0.6, 0.7, 1.0, 1.0, 0.1),
    ('swimming',    0.8, 0.8, 1.0, 0.8, 0.0),
    ('ice_rink',    0.4, 0.7, 1.0, 1.0, 0.2),
    ('mall',        0.5, 0.9, 1.0, 0.8, 0.0),
    ('market',      0.9, 1.0, 0.8, 0.2, 0.0),
    ('bookshop',    0.6, 0.9, 1.0, 0.6, 0.0),
    ('fashion',     0.4, 0.8, 1.0, 0.7, 0.0),
    ('gift',        0.5, 0.9, 1.0, 0.6, 0.0),
    ('spa',         0.4, 0.7, 0.9, 1.0, 0.4)
)
INSERT INTO category_time_priors (category_id, time_of_day, day_type, suitability)
SELECT c.id, slot.time_of_day, d.day_type,
       coalesce(CASE slot.time_of_day
                    WHEN 'MORNING' THEN p.morning WHEN 'MIDDAY' THEN p.midday
                    WHEN 'AFTERNOON' THEN p.afternoon WHEN 'EVENING' THEN p.evening ELSE p.night END,
                CASE WHEN slot.time_of_day = 'NIGHT' THEN 0.2 ELSE 0.7 END)
FROM place_categories c
CROSS JOIN (VALUES ('MORNING'), ('MIDDAY'), ('AFTERNOON'), ('EVENING'), ('NIGHT')) AS slot (time_of_day)
CROSS JOIN (VALUES ('WEEKDAY'), ('WEEKEND')) AS d (day_type)
LEFT JOIN priors p ON p.code = c.code
WHERE c.is_recommendable;
