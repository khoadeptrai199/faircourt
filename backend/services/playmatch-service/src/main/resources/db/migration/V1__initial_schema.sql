-- playmatch-service V1 (schema "playmatch"): play_session, attendance, play_match, match_player
-- from blueprint section 12. Changes (F18 design section 4):
--   * no foreign keys to other services' schemas: play_session.host_user_id (auth-service) and
--     attendance.player_id (player-service) are plain UUID columns;
--   * play_session: match_format (OD-20), min_sets (OD-19), price per person by gender as a range (OD-18);
--   * attendance: display_name, copied from player-service at join.
-- match_feedback belongs to rating-service (S05). Never edit this file after it has been applied; add V2__... instead.

CREATE TABLE play_session (
  id UUID PRIMARY KEY,
  public_code VARCHAR(20) NOT NULL UNIQUE,
  host_user_id UUID NOT NULL,
  title VARCHAR(120) NOT NULL,
  venue VARCHAR(160) NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  court_count SMALLINT NOT NULL CHECK (court_count BETWEEN 1 AND 4),
  next_match_no INTEGER NOT NULL DEFAULT 1 CHECK (next_match_no > 0),
  state VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN','CLOSED')),
  match_format VARCHAR(7) NOT NULL DEFAULT 'DOUBLES' CHECK (match_format IN ('SINGLES','DOUBLES')),
  min_sets SMALLINT NOT NULL DEFAULT 6 CHECK (min_sets BETWEEN 1 AND 20),
  -- Price per person by gender, as a range in VND; all NULL = no price shown (OD-18).
  price_male_min_vnd INTEGER CHECK (price_male_min_vnd >= 0),
  price_male_max_vnd INTEGER CHECK (price_male_max_vnd >= 0),
  price_female_min_vnd INTEGER CHECK (price_female_min_vnd >= 0),
  price_female_max_vnd INTEGER CHECK (price_female_max_vnd >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at),
  CHECK (price_male_min_vnd IS NULL OR price_male_max_vnd IS NULL OR price_male_min_vnd <= price_male_max_vnd),
  CHECK (price_female_min_vnd IS NULL OR price_female_max_vnd IS NULL OR price_female_min_vnd <= price_female_max_vnd)
);
CREATE INDEX ix_session_host_start ON play_session (host_user_id, starts_at DESC);

CREATE TABLE attendance (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES play_session(id),
  player_id UUID NOT NULL,
  display_name VARCHAR(80) NOT NULL CHECK (length(trim(display_name)) > 0),
  state VARCHAR(10) NOT NULL CHECK
    (state IN ('PENDING','READY','ASSIGNED','PLAYING','RESTING','LEFT')),
  checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  available_since TIMESTAMPTZ NOT NULL DEFAULT now(),
  games_played INTEGER NOT NULL DEFAULT 0 CHECK (games_played >= 0),
  UNIQUE (session_id, player_id),
  UNIQUE (id, session_id)
);
CREATE INDEX ix_attendance_pool ON attendance (session_id, state, available_since);

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
