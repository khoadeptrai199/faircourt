-- player-service V1 (schema "player"): player_profile and skill_assessment from blueprint section 12.
-- Changes from the blueprint (F18 design section 4): no foreign keys to other services' schemas.
-- player_profile.user_id (auth-service), skill_assessment.session_id (playmatch-service) and
-- skill_assessment.reviewer_user_id (auth-service) are plain UUID columns.
-- Never edit this file after it has been applied; add V2__... instead.

CREATE TABLE player_profile (
  id UUID PRIMARY KEY,
  user_id UUID UNIQUE,
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

CREATE TABLE skill_assessment (
  id UUID PRIMARY KEY,
  player_id UUID NOT NULL REFERENCES player_profile(id),
  session_id UUID NOT NULL,
  source VARCHAR(5) NOT NULL CHECK (source IN ('SELF','HOST')),
  reviewer_user_id UUID,
  answers JSONB,
  band SMALLINT CHECK (band BETWEEN 1 AND 4),
  note VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((source = 'SELF' AND reviewer_user_id IS NULL) OR
         (source = 'HOST' AND reviewer_user_id IS NOT NULL))
);
CREATE INDEX ix_assessment_player_time ON skill_assessment (player_id, created_at DESC);
