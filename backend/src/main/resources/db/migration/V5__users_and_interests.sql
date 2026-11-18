-- Auth module (users, preferences, consents) and Interest Profile module (survey, weights).
-- Design: table "Өгөгдлийн сангийн хүснэгтүүд", groups "Хэрэглэгч ба нууцлал", "Сонирхлын загварчлал".

CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(100),                       -- NULL for Google / Facebook accounts
    display_name      VARCHAR(100) NOT NULL,
    username          VARCHAR(50)  NOT NULL,
    auth_provider     VARCHAR(20)  NOT NULL CHECK (auth_provider IN ('EMAIL', 'GOOGLE', 'FACEBOOK')),
    provider_user_id  VARCHAR(255),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    home_location     geometry(Point, 4326),
    reward_points     INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ                         -- ХШ08: personal data removed, row kept anonymised
);
CREATE UNIQUE INDEX users_email_unique    ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX users_username_unique ON users (lower(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX users_provider_unique ON users (auth_provider, provider_user_id) WHERE provider_user_id IS NOT NULL;

CREATE TABLE user_preferences (
    user_id          BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    transport_modes  VARCHAR(100) NOT NULL,              -- comma-separated TransportMode values
    budget_level     VARCHAR(10)  NOT NULL CHECK (budget_level IN ('FREE', 'LOW', 'MEDIUM', 'HIGH')),
    search_radius_m  INT          NOT NULL CHECK (search_radius_m BETWEEN 500 AND 50000),
    time_budget_min  INT          NOT NULL CHECK (time_budget_min BETWEEN 30 AND 720),
    stop_count       INT          NOT NULL CHECK (stop_count BETWEEN 1 AND 4)
);

CREATE TABLE privacy_consents (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    data_type       VARCHAR(30)  NOT NULL
        CHECK (data_type IN ('INTERESTS', 'LOCATION', 'LOCATION_SHARING', 'INTERACTION_HISTORY')),
    purpose         VARCHAR(255) NOT NULL,
    retention_days  INT          NOT NULL,
    is_public       BOOLEAN      NOT NULL DEFAULT FALSE,
    granted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);
-- At most one active consent per user and data type; revoked ones stay as history
CREATE UNIQUE INDEX privacy_consents_active_unique ON privacy_consents (user_id, data_type) WHERE revoked_at IS NULL;

CREATE TABLE onboarding_surveys (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    completed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE survey_categories (
    survey_id    BIGINT NOT NULL REFERENCES onboarding_surveys (id) ON DELETE CASCADE,
    category_id  BIGINT NOT NULL REFERENCES place_categories (id),
    PRIMARY KEY (survey_id, category_id)
);

CREATE TABLE interest_weights (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id  BIGINT        NOT NULL REFERENCES place_categories (id),
    weight       DOUBLE PRECISION NOT NULL CHECK (weight BETWEEN 0 AND 1),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (user_id, category_id)
);
