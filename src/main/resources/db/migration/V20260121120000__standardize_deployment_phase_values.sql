-- Standardize deployment_phase values to use 'completed' instead of 'success'
-- Agent sends COMPLETED (stored as 'completed'), but some code incorrectly checked for 'success'
UPDATE version_history
SET deployment_phase = 'completed'
WHERE deployment_phase = 'success';
