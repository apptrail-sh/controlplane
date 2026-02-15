CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    name          VARCHAR(256),
    picture_url   VARCHAR(2048),
    provider      VARCHAR(64)  NOT NULL DEFAULT 'google',
    provider_sub  VARCHAR(256) NOT NULL,
    last_login_at TIMESTAMPTZ,
    token_version INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_users_provider_sub ON users (provider, provider_sub);

CREATE TABLE api_keys
(
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(256) NOT NULL,
    key_hash     VARCHAR(128) NOT NULL UNIQUE,
    key_prefix   VARCHAR(12)  NOT NULL,
    description  TEXT,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
