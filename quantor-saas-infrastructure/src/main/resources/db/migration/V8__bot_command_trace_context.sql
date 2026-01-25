ALTER TABLE bot_commands
  ADD COLUMN IF NOT EXISTS request_id varchar(64);

ALTER TABLE bot_commands
  ADD COLUMN IF NOT EXISTS traceparent varchar(256);

CREATE INDEX IF NOT EXISTS idx_bot_commands_request_id
  ON bot_commands(request_id);
