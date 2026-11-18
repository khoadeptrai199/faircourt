-- player-service V2: rename "session" to "game" (owner decision 2026-11-18, OD-22).
-- skill_assessment.session_id refers to a game in playmatch-service (plain UUID, no foreign key).
-- V1 is already applied; this renames in place.

ALTER TABLE skill_assessment RENAME COLUMN session_id TO game_id;
