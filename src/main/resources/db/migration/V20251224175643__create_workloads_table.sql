-- Table: workloads
-- Stores logical workloads (independent of where they're deployed)
-- Unique by: group, kind, name
CREATE TABLE workloads
(
    id         BIGSERIAL PRIMARY KEY,
    "group"    VARCHAR(253) NOT NULL,
    kind       VARCHAR(63)  NOT NULL,
    name       VARCHAR(253) NOT NULL,
    team       VARCHAR(128),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraint: A workload is unique by group, kind, and name
CREATE UNIQUE INDEX idx_workload_unique ON workloads ("group", kind, name);
CREATE INDEX idx_workloads_team ON workloads (team);
CREATE INDEX idx_workloads_kind ON workloads (kind);

-- Table: workload_instances
-- Stores concrete deployments of workloads in specific clusters/namespaces
-- Unique by: workload_id, cluster_id, namespace
CREATE TABLE workload_instances
(
    id              BIGSERIAL PRIMARY KEY,
    workload_id     BIGINT       NOT NULL,
    cluster_id      BIGINT       NOT NULL,
    namespace       VARCHAR(253) NOT NULL,
    environment     VARCHAR(32)  NOT NULL,
    current_version VARCHAR(128),
    labels          JSONB,
    first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_instance_workload FOREIGN KEY (workload_id)
        REFERENCES workloads (id) ON DELETE CASCADE,
    CONSTRAINT fk_instance_cluster FOREIGN KEY (cluster_id)
        REFERENCES clusters (id) ON DELETE CASCADE
);

-- Unique constraint: workload can only be deployed once per cluster/namespace
CREATE UNIQUE INDEX idx_instance_unique ON workload_instances (workload_id, cluster_id, namespace);
CREATE INDEX idx_instance_workload ON workload_instances (workload_id);
CREATE INDEX idx_instance_cluster ON workload_instances (cluster_id);
CREATE INDEX idx_instance_cluster_namespace ON workload_instances (cluster_id, namespace);
CREATE INDEX idx_instance_namespace ON workload_instances (namespace);
CREATE INDEX idx_instance_environment ON workload_instances (environment);
CREATE INDEX idx_instance_workload_environment ON workload_instances (workload_id, environment);
CREATE INDEX idx_instance_last_updated ON workload_instances (last_updated_at DESC);
