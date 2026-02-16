-- 1) FK на users (поймает мусорные user_id)
ALTER TABLE subscriptions
  ADD CONSTRAINT fk_subscriptions_user
  FOREIGN KEY (user_id) REFERENCES users(id);

-- 2) План/статус — хотя бы CHECK, чтобы не прилетало "proo" и т.п.
ALTER TABLE subscriptions
  ADD CONSTRAINT ck_subscriptions_plan
  CHECK (plan IN ('FREE','PRO','PRO_PLUS','ENTERPRISE'));

ALTER TABLE subscriptions
  ADD CONSTRAINT ck_subscriptions_status
  CHECK (status IN ('ACTIVE','PAST_DUE','CANCELLED','EXPIRED','TRIALING'));

-- 3) Частичный UNIQUE: у пользователя максимум 1 "активная" подписка
-- (если у тебя допускается несколько статусов "активности" — подправь список)
CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_user_active
  ON subscriptions(user_id)
  WHERE status IN ('ACTIVE','TRIALING') AND frozen = FALSE;
