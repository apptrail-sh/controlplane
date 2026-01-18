-- Add part_of column to workloads table
-- This stores the app.kubernetes.io/part-of label value for grouping related workloads
ALTER TABLE workloads ADD COLUMN part_of VARCHAR(253);
