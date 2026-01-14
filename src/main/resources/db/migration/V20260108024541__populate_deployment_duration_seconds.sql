-- Populate deployment_duration_seconds from timestamps for existing records
UPDATE version_history
SET deployment_duration_seconds = EXTRACT(EPOCH FROM (deployment_completed_at - deployment_started_at))::INTEGER
WHERE deployment_duration_seconds IS NULL
  AND deployment_started_at IS NOT NULL
  AND deployment_completed_at IS NOT NULL;
