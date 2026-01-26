-- Create releases table as first-class entity for storing release information
CREATE TABLE releases
(
    id             BIGSERIAL PRIMARY KEY,
    repository_url VARCHAR(2048) NOT NULL,
    tag_name       VARCHAR(128)  NOT NULL,
    name           VARCHAR(256),
    body           TEXT,
    html_url       VARCHAR(2048),
    published_at   TIMESTAMPTZ,
    is_draft       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_prerelease  BOOLEAN       NOT NULL DEFAULT FALSE,
    authors        JSONB,
    provider       VARCHAR(64)   NOT NULL,
    fetched_at     TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_releases_repo_tag UNIQUE (repository_url, tag_name)
);

CREATE INDEX idx_releases_repository_url ON releases (repository_url);
CREATE INDEX idx_releases_tag_name ON releases (tag_name);

-- Add FK to releases table in version_history
ALTER TABLE version_history
    ADD COLUMN release_id BIGINT REFERENCES releases (id);
CREATE INDEX idx_version_history_release_id ON version_history (release_id);

-- Drop old release tracking columns (no backward compatibility needed)
DROP INDEX IF EXISTS idx_version_history_fetch_status;
ALTER TABLE version_history DROP COLUMN IF EXISTS release_info;
ALTER TABLE version_history DROP COLUMN IF EXISTS release_fetch_status;
