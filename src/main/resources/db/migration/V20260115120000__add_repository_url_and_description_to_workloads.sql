-- Add repository_url and description columns to workloads table
ALTER TABLE workloads ADD COLUMN repository_url VARCHAR(2048);
ALTER TABLE workloads ADD COLUMN description TEXT;
