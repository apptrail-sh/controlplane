-- Remove the group field from workloads since partOf now handles workload grouping

-- Drop the unique index that includes group
DROP INDEX idx_workload_unique;

-- Delete duplicate workloads, keeping the oldest one (lowest id) for each (kind, name) pair
DELETE FROM workloads w1
WHERE w1.id NOT IN (
    SELECT MIN(w2.id)
    FROM workloads w2
    GROUP BY w2.kind, w2.name
);

-- Create new unique index without group
CREATE UNIQUE INDEX idx_workload_unique ON workloads (kind, name);

-- Drop the group column
ALTER TABLE workloads DROP COLUMN "group";
