-- Drop repository_url column from releases table
-- This column is no longer needed since releases now reference repositories via repository_id FK
ALTER TABLE releases DROP COLUMN IF EXISTS repository_url;
