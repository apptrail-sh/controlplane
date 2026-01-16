-- Table: version_impact_analyses
-- Stores health check analysis results for deployments (Version Impact Analysis feature)
CREATE TABLE version_impact_analyses
(
    id                            BIGSERIAL PRIMARY KEY,
    version_history_id            BIGINT       NOT NULL,
    status                        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    result                        VARCHAR(32),
    scheduled_at                  TIMESTAMPTZ  NOT NULL,
    started_at                    TIMESTAMPTZ,
    completed_at                  TIMESTAMPTZ,
    pre_deployment_window_start   TIMESTAMPTZ,
    pre_deployment_window_end     TIMESTAMPTZ,
    post_deployment_window_start  TIMESTAMPTZ,
    post_deployment_window_end    TIMESTAMPTZ,
    metrics                       JSONB,
    error_message                 TEXT,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_via_version_history FOREIGN KEY (version_history_id)
        REFERENCES version_history (id) ON DELETE CASCADE
);

-- Index for finding pending analyses ready to execute
CREATE INDEX idx_via_status_scheduled ON version_impact_analyses (status, scheduled_at);

-- Unique constraint: one analysis per version history entry
CREATE UNIQUE INDEX idx_via_unique_version_history ON version_impact_analyses (version_history_id);
