-- Bot commands: production-grade locking/retry fields
ALTER TABLE bot_commands
  ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_bot_commands_pending_due
  ON bot_commands(status, next_run_at, created_at);

-- Worker heartbeat (to see who is alive)
CREATE TABLE IF NOT EXISTS quantor_workers (
  worker_id VARCHAR(64) PRIMARY KEY,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
