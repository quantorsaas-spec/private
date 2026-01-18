ALTER TABLE bot_instances
  ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(64),
  ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_bot_instances_lease_due
  ON bot_instances (status, lease_until);

CREATE INDEX IF NOT EXISTS idx_bot_instances_lease_owner
  ON bot_instances (lease_owner);
