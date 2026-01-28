-- Create repositories table to normalize and deduplicate repository URLs
CREATE TABLE repositories
(
    id         BIGSERIAL PRIMARY KEY,
    url        VARCHAR(2048) NOT NULL UNIQUE, -- Normalized URL (lowercase, no .git suffix)
    provider   VARCHAR(64)   NOT NULL,        -- 'github', 'gitlab', etc.
    owner      VARCHAR(256),                  -- e.g., 'anthropics'
    name       VARCHAR(256),                  -- e.g., 'claude-code'
    created_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repositories_provider ON repositories (provider);
CREATE INDEX idx_repositories_owner_name ON repositories (owner, name);

-- Create release_fetch_attempts table to track failed fetch attempts
-- This prevents repeatedly calling the GitHub API for versions with no release
CREATE TABLE release_fetch_attempts
(
    id            BIGSERIAL PRIMARY KEY,
    repository_id BIGINT       NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    version       VARCHAR(128) NOT NULL,
    attempted_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_fetch_attempts_repo_version UNIQUE (repository_id, version)
);

CREATE INDEX idx_fetch_attempts_repository ON release_fetch_attempts (repository_id);
CREATE INDEX idx_fetch_attempts_attempted_at ON release_fetch_attempts (attempted_at);

-- Add repository_id column to workloads table
ALTER TABLE workloads
    ADD COLUMN repository_id BIGINT REFERENCES repositories (id);
CREATE INDEX idx_workloads_repository ON workloads (repository_id);

-- Add repository_id column to releases table
ALTER TABLE releases
    ADD COLUMN repository_id BIGINT REFERENCES repositories (id);
CREATE INDEX idx_releases_repository ON releases (repository_id);

-- Migrate existing workload repository URLs to repositories table
INSERT INTO repositories (url, provider, owner, name, created_at, updated_at)
SELECT DISTINCT LOWER(REGEXP_REPLACE(repository_url, '\.git$', '')),
                CASE
                    WHEN repository_url ILIKE '%github.com%' THEN 'github'
                    WHEN repository_url ILIKE '%gitlab.com%' THEN 'gitlab'
                    ELSE 'unknown'
                    END,
                (REGEXP_MATCH(LOWER(repository_url), '(?:github|gitlab)\.com/([^/]+)/([^/]+)'))[1],
                REGEXP_REPLACE((REGEXP_MATCH(LOWER(repository_url), '(?:github|gitlab)\.com/([^/]+)/([^/]+)'))[2],
                               '\.git$', ''),
                NOW(),
                NOW()
FROM workloads
WHERE repository_url IS NOT NULL
  AND repository_url != ''
ON CONFLICT (url) DO NOTHING;

-- Also migrate release repository URLs that might not be in workloads
INSERT INTO repositories (url, provider, owner, name, created_at, updated_at)
SELECT DISTINCT LOWER(REGEXP_REPLACE(repository_url, '\.git$', '')),
                provider,
                (REGEXP_MATCH(LOWER(repository_url), '(?:github|gitlab)\.com/([^/]+)/([^/]+)'))[1],
                REGEXP_REPLACE((REGEXP_MATCH(LOWER(repository_url), '(?:github|gitlab)\.com/([^/]+)/([^/]+)'))[2],
                               '\.git$', ''),
                NOW(),
                NOW()
FROM releases
WHERE repository_url IS NOT NULL
  AND repository_url != ''
ON CONFLICT (url) DO NOTHING;

-- Link workloads to repositories
UPDATE workloads w
SET repository_id = r.id FROM repositories r
WHERE LOWER (REGEXP_REPLACE(w.repository_url
    , '\.git$'
    , '')) = r.url
  AND w.repository_url IS NOT NULL
  AND w.repository_url != '';

-- Link releases to repositories
UPDATE releases rel
SET repository_id = r.id FROM repositories r
WHERE LOWER (REGEXP_REPLACE(rel.repository_url, '\.git$', '')) = r.url;

-- Make releases.repository_id NOT NULL now that data is migrated
ALTER TABLE releases
    ALTER COLUMN repository_id SET NOT NULL;

-- Update unique constraint on releases to use repository_id instead of repository_url
ALTER TABLE releases
DROP
CONSTRAINT IF EXISTS uk_releases_repo_tag;
ALTER TABLE releases
    ADD CONSTRAINT uk_releases_repo_tag UNIQUE (repository_id, tag_name);

-- Drop old index on repository_url since we now use repository_id
DROP INDEX IF EXISTS idx_releases_repository_url;
