-- Table: version_history
-- Tracks version changes for workload instances
CREATE TABLE version_history
(
    id                          BIGSERIAL PRIMARY KEY,
    workload_instance_id        BIGINT       NOT NULL,
    previous_version            VARCHAR(128),
    current_version             VARCHAR(128) NOT NULL,
    deployment_duration_seconds INTEGER,
    deployment_status           VARCHAR(32),
    deployment_phase            VARCHAR(32),
    deployment_started_at       TIMESTAMPTZ,
    deployment_completed_at     TIMESTAMPTZ,
    deployment_failed_at        TIMESTAMPTZ,
    detected_at                 TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_version_history_instance FOREIGN KEY (workload_instance_id)
        REFERENCES workload_instances (id) ON DELETE CASCADE
);

CREATE INDEX idx_version_history_instance_detected ON version_history (workload_instance_id, detected_at DESC);
CREATE INDEX idx_version_history_detected ON version_history (detected_at DESC);
CREATE UNIQUE INDEX idx_version_history_unique_version
    ON version_history (workload_instance_id, current_version, COALESCE(previous_version, ''));
