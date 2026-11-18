-- auth-service V2: rename "session" to "game" (owner decision 2026-11-18, OD-22).
-- guest_access.session_id refers to a game in playmatch-service (plain UUID, no foreign key).
-- "Session" stays only for the technical login/HTTP session. V1 is already applied; this renames in place.

ALTER TABLE guest_access RENAME COLUMN session_id TO game_id;
