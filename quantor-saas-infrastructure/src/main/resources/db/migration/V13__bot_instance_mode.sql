ALTER TABLE bot_instances
  ADD COLUMN IF NOT EXISTS mode VARCHAR(16) NOT NULL DEFAULT 'PAPER';

-- Backfill existing rows (defensive; column already has default)
UPDATE bot_instances SET mode = 'PAPER' WHERE mode IS NULL OR mode = '';
