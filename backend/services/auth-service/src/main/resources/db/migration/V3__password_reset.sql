-- auth-service V3 (schema "auth"): password reset by emailed one-time code (Password reset feature, owner approved 2026-11-18).
--   * app_user.password_changed_at: when the password last changed; the gateway ends sessions older than this;
--   * password_reset: one row per code that was sent. Only a BCrypt hash of the 8-digit code is stored, never the code.
-- V1 and V2 are already applied and stay untouched.

ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE password_reset (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  code_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  failed_attempts SMALLINT NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
  consumed_at TIMESTAMPTZ,
  CHECK (expires_at > created_at)
);
CREATE INDEX ix_password_reset_user_created ON password_reset (user_id, created_at DESC);
