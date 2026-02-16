-- V17__audit_log.sql
-- IMPORTANT:
-- audit_log table is defined in V9__audit_log_and_subscription_freeze.sql (source of truth).
-- V17 exists only for forward-compat / safety and must be idempotent.

-- Ensure created_at index exists (it should already exist from V9, but keep it safe).
CREATE INDEX IF NOT EXISTS ix_audit_log_created_at ON audit_log (created_at);
