-- FairCourt V1 schema: copied verbatim from FairCourt_Project_Blueprint.md section 12.
-- Never edit this file after it has been applied; add V2__... instead.

CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_user_email_ci ON app_user (lower(email));

CREATE TABLE player_profile (
  id UUID PRIMARY KEY,
  user_id UUID UNIQUE REFERENCES app_user(id),
  kind VARCHAR(10) NOT NULL CHECK (kind IN ('MEMBER','GUEST')),
  display_name VARCHAR(80) NOT NULL CHECK (length(trim(display_name)) > 0),
  current_band SMALLINT CHECK (current_band BETWEEN 1 AND 4),
  band_status VARCHAR(15) NOT NULL DEFAULT 'SELF_REPORTED'
    CHECK (band_status IN ('SELF_REPORTED','OBSERVED','CONFIRMED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_profile_owner CHECK (
    (kind = 'MEMBER' AND user_id IS NOT NULL) OR
    (kind = 'GUEST' AND user_id IS NULL)
  )
);

CREATE TABLE play_session (
  id UUID PRIMARY KEY,
  public_code VARCHAR(20) NOT NULL UNIQUE,
  host_user_id UUID NOT NULL REFERENCES app_user(id),
  title VARCHAR(120) NOT NULL,
  venue VARCHAR(160) NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  court_count SMALLINT NOT NULL CHECK (court_count BETWEEN 1 AND 4),
  next_match_no INTEGER NOT NULL DEFAULT 1 CHECK (next_match_no > 0),
  state VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN','CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);
CREATE INDEX ix_session_host_start ON play_session (host_user_id, starts_at DESC);

CREATE TABLE attendance (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES play_session(id),
  player_id UUID NOT NULL REFERENCES player_profile(id),
  state VARCHAR(10) NOT NULL CHECK
    (state IN ('PENDING','READY','ASSIGNED','PLAYING','RESTING','LEFT')),
  checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  available_since TIMESTAMPTZ NOT NULL DEFAULT now(),
  games_played INTEGER NOT NULL DEFAULT 0 CHECK (games_played >= 0),
  UNIQUE (session_id, player_id),
  UNIQUE (id, session_id)
);
CREATE INDEX ix_attendance_pool ON attendance (session_id, state, available_since);

CREATE TABLE skill_assessment (
  id UUID PRIMARY KEY,
  player_id UUID NOT NULL REFERENCES player_profile(id),
  session_id UUID NOT NULL REFERENCES play_session(id),
  source VARCHAR(5) NOT NULL CHECK (source IN ('SELF','HOST')),
  reviewer_user_id UUID REFERENCES app_user(id),
  answers JSONB,
  band SMALLINT CHECK (band BETWEEN 1 AND 4),
  note VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((source = 'SELF' AND reviewer_user_id IS NULL) OR
         (source = 'HOST' AND reviewer_user_id IS NOT NULL))
);
CREATE INDEX ix_assessment_player_time ON skill_assessment (player_id, created_at DESC);

CREATE TABLE guest_access (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL,
  attendance_id UUID NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY (attendance_id, session_id) REFERENCES attendance (id, session_id)
);
CREATE UNIQUE INDEX ux_active_guest_access ON guest_access (attendance_id)
  WHERE revoked_at IS NULL;

CREATE TABLE play_match (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES play_session(id),
  match_no INTEGER NOT NULL CHECK (match_no > 0),
  court_no SMALLINT NOT NULL CHECK (court_no > 0),
  state VARCHAR(10) NOT NULL CHECK (state IN ('PLANNED','ACTIVE','FINISHED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  UNIQUE (session_id, match_no),
  UNIQUE (id, session_id)
);
CREATE UNIQUE INDEX ux_busy_court ON play_match (session_id, court_no)
  WHERE state IN ('PLANNED','ACTIVE');

CREATE TABLE match_player (
  match_id UUID NOT NULL,
  session_id UUID NOT NULL,
  attendance_id UUID NOT NULL,
  team CHAR(1) NOT NULL CHECK (team IN ('A','B')),
  released_at TIMESTAMPTZ,
  PRIMARY KEY (match_id, attendance_id),
  FOREIGN KEY (match_id, session_id) REFERENCES play_match(id, session_id),
  FOREIGN KEY (attendance_id, session_id) REFERENCES attendance(id, session_id)
);
CREATE UNIQUE INDEX ux_player_in_unreleased_match
  ON match_player (session_id, attendance_id) WHERE released_at IS NULL;
CREATE INDEX ix_match_players_by_attendance ON match_player (attendance_id, match_id);

CREATE TABLE match_feedback (
  match_id UUID NOT NULL,
  attendance_id UUID NOT NULL,
  fit VARCHAR(10) NOT NULL CHECK (fit IN ('TOO_EASY','ABOUT_RIGHT','TOO_HARD')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (match_id, attendance_id),
  FOREIGN KEY (match_id, attendance_id)
    REFERENCES match_player(match_id, attendance_id)
);
