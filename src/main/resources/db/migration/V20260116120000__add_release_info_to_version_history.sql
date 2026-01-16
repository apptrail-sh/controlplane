-- Add release tracking columns to version_history table
ALTER TABLE version_history ADD COLUMN release_info JSONB;
ALTER TABLE version_history ADD COLUMN release_fetch_status VARCHAR(32);

-- Index for finding pending fetches efficiently
CREATE INDEX idx_version_history_fetch_status
    ON version_history (release_fetch_status)
    WHERE release_fetch_status IS NOT NULL;
