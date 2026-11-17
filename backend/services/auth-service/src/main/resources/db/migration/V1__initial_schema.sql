-- auth-service V1 (schema "auth"): app_user and guest_access from blueprint section 12.
-- Change from the blueprint (F18 design section 4): guest_access no longer has a foreign key to attendance,
-- which lives in playmatch-service; session_id and attendance_id stay as plain UUID columns.
-- Never edit this file after it has been applied; add V2__... instead.

CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_user_email_ci ON app_user (lower(email));

CREATE TABLE guest_access (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL,
  attendance_id UUID NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_active_guest_access ON guest_access (attendance_id)
  WHERE revoked_at IS NULL;
