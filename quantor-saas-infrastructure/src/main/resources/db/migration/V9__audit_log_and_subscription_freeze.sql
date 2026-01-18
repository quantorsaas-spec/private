-- Adds admin audit log and subscription freeze flag (support operations).

ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS frozen BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS frozen_at TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS audit_log (
  id UUID PRIMARY KEY,
  actor_type VARCHAR(32) NOT NULL,
  actor_id UUID NULL,
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  request_id VARCHAR(128) NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_audit_log_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS ix_audit_log_actor ON audit_log(actor_type, actor_id);
CREATE INDEX IF NOT EXISTS ix_audit_log_target ON audit_log(target_type, target_id);
