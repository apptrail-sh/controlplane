-- Add shard column to workload_instances table
-- Shard represents a logical subdivision within an environment (e.g., canary, main)
-- Shard order is resolved at query time from configuration, not stored in DB

ALTER TABLE workload_instances
    ADD COLUMN shard VARCHAR(64);

-- Add index for queries filtering by shard
CREATE INDEX idx_workload_instances_shard ON workload_instances (shard);

-- Add composite index for environment + shard queries
CREATE INDEX idx_workload_instances_env_shard ON workload_instances (environment, shard);
