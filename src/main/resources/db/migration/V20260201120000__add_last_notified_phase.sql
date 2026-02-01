-- Add last_notified_phase column to track which phase was last notified
-- This enables idempotent notifications across multiple control plane replicas
ALTER TABLE version_history
    ADD COLUMN last_notified_phase VARCHAR(50);
