CREATE TABLE IF NOT EXISTS entitlements (
  user_id UUID PRIMARY KEY,
  max_bots INT NOT NULL,
  max_trades_per_day INT NOT NULL,
  daily_loss_limit_pct DOUBLE PRECISION NOT NULL,
  telegram_control BOOLEAN NOT NULL,
  advanced_strategies BOOLEAN NOT NULL,
  paper_trading_allowed BOOLEAN NOT NULL,
  live_trading_allowed BOOLEAN NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_entitlements_user ON entitlements (user_id);
