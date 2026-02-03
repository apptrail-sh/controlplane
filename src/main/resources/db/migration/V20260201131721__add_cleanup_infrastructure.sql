-- Add heartbeat tracking and status to clusters
ALTER TABLE clusters
    ADD COLUMN last_heartbeat_at TIMESTAMPTZ;
ALTER TABLE clusters
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ONLINE';

-- Index for finding offline clusters
CREATE INDEX idx_clusters_status ON clusters (status) WHERE status = 'OFFLINE';

-- Heartbeat history table
CREATE TABLE cluster_heartbeats
(
    id            BIGSERIAL PRIMARY KEY,
    cluster_id    BIGINT      NOT NULL REFERENCES clusters (id) ON DELETE CASCADE,
    agent_version VARCHAR(64),
    received_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    node_count    INT         NOT NULL DEFAULT 0,
    pod_count     INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cluster_heartbeats_cluster_time
    ON cluster_heartbeats (cluster_id, received_at DESC);

-- Indexes for efficient cleanup queries on nodes
CREATE INDEX idx_nodes_cluster_deleted
    ON nodes (cluster_id, deleted_at) WHERE deleted_at IS NULL;

-- Indexes for efficient cleanup queries on pods
CREATE INDEX idx_pods_cluster_deleted
    ON pods (cluster_id, deleted_at) WHERE deleted_at IS NULL;
