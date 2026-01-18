-- Add period_ms to bot_instances (default 1000ms for existing rows)
ALTER TABLE bot_instances
  ADD COLUMN IF NOT EXISTS period_ms BIGINT NOT NULL DEFAULT 1000;

-- Commands queue table
CREATE TABLE IF NOT EXISTS bot_commands (
  id UUID PRIMARY KEY,
  bot_instance_id UUID NOT NULL REFERENCES bot_instances(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  command VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  worker_id VARCHAR(64),
  error_message VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_bot_commands_status_created
  ON bot_commands(status, created_at);
