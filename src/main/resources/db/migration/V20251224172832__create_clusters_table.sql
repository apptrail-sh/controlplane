-- Table: clusters
-- Stores Kubernetes clusters
CREATE TABLE clusters
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL UNIQUE,
    environment VARCHAR(32),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clusters_environment ON clusters (environment);
