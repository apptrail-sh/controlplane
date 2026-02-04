-- AppTrail Control Plane Baseline Schema
-- =============================================================================
-- Table: clusters
-- Stores Kubernetes clusters
-- =============================================================================
CREATE TABLE clusters
(
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(128) NOT NULL UNIQUE,
    last_heartbeat_at TIMESTAMPTZ,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ONLINE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clusters_status ON clusters (status) WHERE status = 'OFFLINE';

-- =============================================================================
-- Table: repositories
-- Normalizes and deduplicates repository URLs
-- =============================================================================
CREATE TABLE repositories
(
    id         BIGSERIAL PRIMARY KEY,
    url        VARCHAR(2048) NOT NULL UNIQUE,
    provider   VARCHAR(64)   NOT NULL,
    owner      VARCHAR(256),
    name       VARCHAR(256),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repositories_provider ON repositories (provider);
CREATE INDEX idx_repositories_owner_name ON repositories (owner, name);

-- =============================================================================
-- Table: workloads
-- Stores logical workloads (independent of where they're deployed)
-- Unique by: kind, name
-- =============================================================================
CREATE TABLE workloads
(
    id            BIGSERIAL PRIMARY KEY,
    kind          VARCHAR(63)  NOT NULL,
    name          VARCHAR(253) NOT NULL,
    team          VARCHAR(128),
    description   TEXT,
    part_of       VARCHAR(253),
    repository_id BIGINT REFERENCES repositories (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_workload_unique ON workloads (kind, name);
CREATE INDEX idx_workloads_team ON workloads (team);
CREATE INDEX idx_workloads_kind ON workloads (kind);
CREATE INDEX idx_workloads_repository ON workloads (repository_id);

-- =============================================================================
-- Table: workload_instances
-- Stores concrete deployments of workloads in specific clusters/namespaces
-- Unique by: workload_id, cluster_id, namespace
-- =============================================================================
CREATE TABLE workload_instances
(
    id              BIGSERIAL PRIMARY KEY,
    workload_id     BIGINT       NOT NULL,
    cluster_id      BIGINT       NOT NULL,
    namespace       VARCHAR(253) NOT NULL,
    environment     VARCHAR(32)  NOT NULL,
    cell            VARCHAR(64),
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

CREATE UNIQUE INDEX idx_instance_unique ON workload_instances (workload_id, cluster_id, namespace);
CREATE INDEX idx_instance_workload ON workload_instances (workload_id);
CREATE INDEX idx_instance_cluster ON workload_instances (cluster_id);
CREATE INDEX idx_instance_cluster_namespace ON workload_instances (cluster_id, namespace);
CREATE INDEX idx_instance_namespace ON workload_instances (namespace);
CREATE INDEX idx_instance_environment ON workload_instances (environment);
CREATE INDEX idx_instance_workload_environment ON workload_instances (workload_id, environment);
CREATE INDEX idx_instance_last_updated ON workload_instances (last_updated_at DESC);
CREATE INDEX idx_workload_instances_shard ON workload_instances (cell);
CREATE INDEX idx_workload_instances_env_shard ON workload_instances (environment, cell);

-- =============================================================================
-- Table: releases
-- First-class entity for storing release information
-- =============================================================================
CREATE TABLE releases
(
    id            BIGSERIAL PRIMARY KEY,
    repository_id BIGINT       NOT NULL REFERENCES repositories (id),
    tag_name      VARCHAR(128) NOT NULL,
    name          VARCHAR(256),
    body          TEXT,
    html_url      VARCHAR(2048),
    published_at  TIMESTAMPTZ,
    is_draft      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_prerelease BOOLEAN      NOT NULL DEFAULT FALSE,
    authors       JSONB,
    provider      VARCHAR(64)  NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_releases_repo_tag UNIQUE (repository_id, tag_name)
);

CREATE INDEX idx_releases_repository ON releases (repository_id);
CREATE INDEX idx_releases_tag_name ON releases (tag_name);

-- =============================================================================
-- Table: version_history
-- Tracks version changes for workload instances
-- =============================================================================
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
    release_id                  BIGINT REFERENCES releases (id),
    last_notified_phase         VARCHAR(50),
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
CREATE INDEX idx_version_history_release_id ON version_history (release_id);

-- =============================================================================
-- Table: release_fetch_attempts
-- Tracks failed fetch attempts to prevent repeated API calls
-- =============================================================================
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

-- =============================================================================
-- Table: nodes
-- Stores Kubernetes nodes (cluster-scoped resources)
-- Tracks current state only, no event history
-- =============================================================================
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

CREATE UNIQUE INDEX uk_node_cluster ON nodes (cluster_id, name);
CREATE INDEX idx_nodes_cluster ON nodes (cluster_id);
CREATE INDEX idx_nodes_deleted_at ON nodes (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_nodes_labels ON nodes USING GIN (labels);
CREATE INDEX idx_nodes_cluster_deleted ON nodes (cluster_id, deleted_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE nodes IS 'Kubernetes nodes within each cluster, tracking current state';
COMMENT ON COLUMN nodes.status IS 'Node status including phase, conditions, capacity, allocatable';
COMMENT ON COLUMN nodes.deleted_at IS 'Soft delete timestamp; NULL means node is active';

-- =============================================================================
-- Table: pods
-- Stores Kubernetes pods with workload and node correlation
-- Tracks current state only, no event history
-- =============================================================================
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

CREATE UNIQUE INDEX uk_pod_cluster ON pods (cluster_id, namespace, name);
CREATE INDEX idx_pods_cluster_namespace ON pods (cluster_id, namespace);
CREATE INDEX idx_pods_workload_instance ON pods (workload_instance_id);
CREATE INDEX idx_pods_node ON pods (node_id);
CREATE INDEX idx_pods_deleted_at ON pods (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_pods_labels ON pods USING GIN (labels);
CREATE INDEX idx_pods_status ON pods USING GIN (status);
CREATE INDEX idx_pods_cluster_deleted ON pods (cluster_id, deleted_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE pods IS 'Kubernetes pods with correlation to workload instances and nodes';
COMMENT ON COLUMN pods.workload_instance_id IS 'Links pod to its owning workload instance (via owner references)';
COMMENT ON COLUMN pods.node_id IS 'Links pod to the node where it is scheduled';
COMMENT ON COLUMN pods.status IS 'Pod status including phase, conditions, containerStatuses';
COMMENT ON COLUMN pods.deleted_at IS 'Soft delete timestamp; NULL means pod is active';

-- =============================================================================
-- Table: cluster_heartbeats
-- Heartbeat history for cluster health monitoring
-- =============================================================================
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

-- =============================================================================
-- Materialized View: cluster_pod_counts
-- Aggregated pod counts per cluster, refreshed periodically
-- =============================================================================
CREATE MATERIALIZED VIEW cluster_pod_counts AS
SELECT cluster_id,
       COUNT(*)                                                 as total_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Running')   as running_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Pending')   as pending_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Failed')    as failed_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Succeeded') as succeeded_pods,
       SUM(COALESCE(
               (SELECT SUM((cs ->> 'restartCount')::int)
                FROM jsonb_array_elements(status -> 'containerStatuses') cs),
               0
           ))::int                                              as total_restarts
FROM pods
WHERE deleted_at IS NULL
GROUP BY cluster_id;

CREATE UNIQUE INDEX idx_cluster_pod_counts_cluster ON cluster_pod_counts (cluster_id);

COMMENT ON MATERIALIZED VIEW cluster_pod_counts IS 'Aggregated pod counts per cluster, refreshed periodically';

-- =============================================================================
-- Materialized View: cluster_node_counts
-- Aggregated node counts per cluster, refreshed periodically
-- =============================================================================
CREATE MATERIALIZED VIEW cluster_node_counts AS
SELECT cluster_id,
       COUNT(*)                                                       as total_nodes,
       COUNT(*) FILTER (WHERE EXISTS (SELECT 1
                                      FROM jsonb_array_elements(status -> 'conditions') c
                                      WHERE c ->> 'type' = 'Ready'
                                        AND c ->> 'status' = 'True')) as ready_nodes,
       COUNT(*) FILTER (WHERE EXISTS (SELECT 1
                                      FROM jsonb_array_elements(status -> 'conditions') c
                                      WHERE c ->> 'type' IN ('MemoryPressure', 'DiskPressure', 'PIDPressure')
                                        AND c ->> 'status' = 'True')) as nodes_with_pressure
FROM nodes
WHERE deleted_at IS NULL
GROUP BY cluster_id;

CREATE UNIQUE INDEX idx_cluster_node_counts_cluster ON cluster_node_counts (cluster_id);

COMMENT ON MATERIALIZED VIEW cluster_node_counts IS 'Aggregated node counts per cluster, refreshed periodically';
