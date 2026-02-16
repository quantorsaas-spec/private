-- Adds optional user_id for lookup; keeps existing audit_log schema intact
ALTER TABLE audit_log
  ADD COLUMN IF NOT EXISTS user_id UUID;

CREATE INDEX IF NOT EXISTS ix_audit_log_user_id ON audit_log (user_id);
