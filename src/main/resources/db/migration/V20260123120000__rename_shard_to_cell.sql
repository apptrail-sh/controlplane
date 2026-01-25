-- Rename shard column to cell in workload_instances table
ALTER TABLE workload_instances RENAME COLUMN shard TO cell;
