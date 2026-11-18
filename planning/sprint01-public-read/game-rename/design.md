# F19 design — Game rename

Status: `APPROVED` (2026-11-18, by owner: "duyệt hết").

## 1. Summary

Rename the domain word "session" (the play event) to "game" (OD-22). Already applied V1 migrations are never edited, so each database service gets a `V2__rename_session_to_game.sql` that renames in place and keeps all data.

## 2. Mapping

| Before | After |
|---|---|
| table `playmatch.play_session` | `playmatch.game` |
| `session_id` in `attendance`, `play_match`, `match_player`, `auth.guest_access`, `player.skill_assessment` | `game_id` |
| `attendance.games_played` | `matches_played` |
| index `ix_session_host_start` | `ix_game_host_start` |
| constraints generated from the old names (`play_session_pkey`, `attendance_session_id_fkey`, …) | same name with `game` / `game_id` / `matches_played` (renamed by a loop in V2) |
| package `com.faircourt.playmatch.session` | `com.faircourt.playmatch.game` |
| `BoardSnapshot.ReadyPlayer.gamesPlayed` | `matchesPlayed` |
| API `/public/sessions/{publicCode}` (blueprint §13) | `/public/games/{publicCode}`; later routes follow the table in `conventions.md` "Domain words" |
| page `/s/:publicCode` (§16) | `/g/:publicCode` |
| copy "Join session", "Session not found", home page "Join a session.", "Find a fair game." | "Join game", "Game not found", "Join a game.", "Find a fair match." |

Unchanged on purpose: the login/HTTP session (`JSESSIONID`, "account session"); the seed key `session:1` used to derive the demo game's UUID (changing it would make the seed insert a second demo game into existing local databases).

## 3. Risks

- V2 runs once on each existing local database (Mac and WSL). Renames are metadata-only in PostgreSQL; no data is rewritten.
- The blueprint keeps its words; `conventions.md` and `API Document/` carry the mapping.

## 4. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-02 | Schema names contain no `session` / `games_played`; `game_id`, `matches_played`, `game_pkey`, `ix_game_host_start` exist | `PlaymatchSchemaIT` |
| TC-02 | AC-02 | `guest_access` / `skill_assessment` have `game_id`, not `session_id` | `AuthSchemaIT`, `PlayerSchemaIT` |
| TC-03 | AC-01, AC-03 | Live stack on a database with V1 data: V2 applied, 1 game and 8 attendance rows kept, seed "already present" | manual, recorded |
| TC-04 | AC-04 | Existing matcher tests | `MatchSuggesterTest` |
