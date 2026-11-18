-- playmatch-service V2: rename "session" to "game" (owner decision 2026-11-18, OD-22).
-- A game is the play event a host posts; a single 21-point contest is a match (code) / set (UI).
-- V1 is already applied on local databases, so it stays untouched; this file renames in place and keeps all data.

ALTER TABLE play_session RENAME TO game;

ALTER TABLE attendance RENAME COLUMN session_id TO game_id;
ALTER TABLE play_match RENAME COLUMN session_id TO game_id;
ALTER TABLE match_player RENAME COLUMN session_id TO game_id;

-- A finished contest is a match, never a "game" (OD-22).
ALTER TABLE attendance RENAME COLUMN games_played TO matches_played;

ALTER INDEX ix_session_host_start RENAME TO ix_game_host_start;

-- PostgreSQL generated the names of V1's unnamed constraints from the old table and column names
-- (e.g. play_session_pkey, attendance_session_id_fkey, attendance_games_played_check).
-- Rename every constraint of this schema whose name still says "session" or "games_played".
-- Renaming a primary key or unique constraint also renames its index.
DO $$
DECLARE
  c RECORD;
BEGIN
  FOR c IN
    SELECT con.conname, rel.relname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = current_schema()
      AND (con.conname LIKE '%session%' OR con.conname LIKE '%games_played%')
  LOOP
    EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
      c.relname, c.conname,
      replace(replace(replace(c.conname, 'play_session', 'game'), 'session_id', 'game_id'),
              'games_played', 'matches_played'));
  END LOOP;
END $$;
