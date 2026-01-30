-- Table: pods
-- Stores Kubernetes pods with workload and node correlation
-- Tracks current state only, no event history
CREATE TABLE pods
(
    id                   BIGSERIAL PRIMARY KEY,
    cluster_id           BIGINT       NOT NULL,
    workload_instance_id BIGINT,
    node_id              BIGINT,
    namespace            VARCHAR(253) NOT NULL,
    name                 VARCHAR(253) NOT NULL,
    uid                  VARCHAR(36)  NOT NULL,
    labels               JSONB,
    status               JSONB,
    first_seen_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pod_cluster FOREIGN KEY (cluster_id)
        REFERENCES clusters (id) ON DELETE CASCADE,
    CONSTRAINT fk_pod_workload_instance FOREIGN KEY (workload_instance_id)
        REFERENCES workload_instances (id) ON DELETE SET NULL,
    CONSTRAINT fk_pod_node FOREIGN KEY (node_id)
        REFERENCES nodes (id) ON DELETE SET NULL
);

-- Unique constraint: pod name is unique within a cluster/namespace
CREATE UNIQUE INDEX uk_pod_cluster ON pods (cluster_id, namespace, name);
CREATE INDEX idx_pods_cluster_namespace ON pods (cluster_id, namespace);
CREATE INDEX idx_pods_workload_instance ON pods (workload_instance_id);
CREATE INDEX idx_pods_node ON pods (node_id);
CREATE INDEX idx_pods_deleted_at ON pods (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_pods_labels ON pods USING GIN (labels);
CREATE INDEX idx_pods_status ON pods USING GIN (status);

-- Add comments for documentation
COMMENT ON TABLE pods IS 'Kubernetes pods with correlation to workload instances and nodes';
COMMENT ON COLUMN pods.workload_instance_id IS 'Links pod to its owning workload instance (via owner references)';
COMMENT ON COLUMN pods.node_id IS 'Links pod to the node where it is scheduled';
COMMENT ON COLUMN pods.status IS 'Pod status including phase, conditions, containerStatuses';
COMMENT ON COLUMN pods.deleted_at IS 'Soft delete timestamp; NULL means pod is active';
