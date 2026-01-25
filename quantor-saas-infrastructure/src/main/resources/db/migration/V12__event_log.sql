CREATE TABLE IF NOT EXISTS event_log (
  id UUID PRIMARY KEY,
  source VARCHAR(30) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  user_id UUID NULL,
  payload_json TEXT NULL,
  status VARCHAR(30) NOT NULL,
  error TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_event_log_source_event ON event_log (source, event_id);
CREATE INDEX IF NOT EXISTS ix_event_log_user ON event_log (user_id);
CREATE INDEX IF NOT EXISTS ix_event_log_created ON event_log (created_at);
