-- Table: nodes
-- Stores Kubernetes nodes (cluster-scoped resources)
-- Tracks current state only, no event history
CREATE TABLE nodes
(
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT       NOT NULL,
    name            VARCHAR(253) NOT NULL,
    uid             VARCHAR(36)  NOT NULL,
    labels          JSONB,
    status          JSONB,
    first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_node_cluster FOREIGN KEY (cluster_id)
        REFERENCES clusters (id) ON DELETE CASCADE
);

-- Unique constraint: node name is unique within a cluster
CREATE UNIQUE INDEX uk_node_cluster ON nodes (cluster_id, name);
CREATE INDEX idx_nodes_cluster ON nodes (cluster_id);
CREATE INDEX idx_nodes_deleted_at ON nodes (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_nodes_labels ON nodes USING GIN (labels);

-- Add comment for documentation
COMMENT ON TABLE nodes IS 'Kubernetes nodes within each cluster, tracking current state';
COMMENT ON COLUMN nodes.status IS 'Node status including phase, conditions, capacity, allocatable';
COMMENT ON COLUMN nodes.deleted_at IS 'Soft delete timestamp; NULL means node is active';
