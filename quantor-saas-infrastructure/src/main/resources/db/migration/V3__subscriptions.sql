CREATE TABLE IF NOT EXISTS subscriptions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  plan VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  current_period_ends_at TIMESTAMPTZ NULL,
  external_subscription_id VARCHAR(100) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_external ON subscriptions (external_subscription_id);
CREATE INDEX IF NOT EXISTS ix_subscriptions_user ON subscriptions (user_id);
CREATE INDEX IF NOT EXISTS ix_subscriptions_status ON subscriptions (status);
