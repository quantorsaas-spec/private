CREATE TABLE IF NOT EXISTS bot_instances (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  job_key VARCHAR(255) NOT NULL UNIQUE,
  strategy_id VARCHAR(64) NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  interval VARCHAR(32) NOT NULL,
  lookback INTEGER NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bot_instances_user_status ON bot_instances(user_id, status);
