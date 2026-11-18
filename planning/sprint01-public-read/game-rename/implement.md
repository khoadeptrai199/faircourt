# F19 implementation — Game rename

Design: [design.md](design.md), approved by the owner 2026-11-18. Branch `feature/public-game-page` (shared with F02).

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01, AC-02 | `V2__rename_session_to_game.sql` in playmatch, auth, player | `DONE` |
| T02 | AC-03 | Package `session` → `game`; seed and `PlaymatchSchemaIT` use `game` / `game_id` | `DONE` |
| T03 | AC-04 | Matcher `gamesPlayed` → `matchesPlayed` (name only) | `DONE` |
| T04 | AC-02 | Schema tests for the new names | `DONE` |
| T05 | — | Rules (`conventions.md` "Domain words", `backend-db.md`, `frontend-matching.md`, `privacy-auth.md`, agents, skills), `API Document/`, `CLAUDE.md`, folder renames | `DONE` |
| T06 | AC-05 | Pipeline | `DONE` (see F02 implement.md, same run) |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/playmatch-service/src/main/resources/db/migration/V2__rename_session_to_game.sql` | added | Table, columns, index, generated constraint names |
| `backend/services/auth-service/src/main/resources/db/migration/V2__rename_session_to_game.sql` | added | `guest_access.game_id` |
| `backend/services/player-service/src/main/resources/db/migration/V2__rename_session_to_game.sql` | added | `skill_assessment.game_id` |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/game/PublicCodeGenerator.java` | moved from `session/` (`git mv`) | Package name |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/seed/LocalDemoSeeder.java` | changed | `game` table, `GAME_ID` (key `session:1` kept), title for new databases "FairCourt demo game" |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/PlaymatchSchemaIT.java` | changed | New names + name check test |
| `backend/services/{auth,player}-service/src/test/java/.../*SchemaIT.java` | changed | `game_id` column test |
| `backend/libs/matching/src/{main,test}/...` | changed | `matchesPlayed` |
| `backend/services/playmatch-service/pom.xml`, `compose.yaml` | changed | Comments |
| `.claude/rules/*.md`, `.claude/agents/*.md`, `.claude/skills/{acceptance-check,new-endpoint}/SKILL.md`, `CLAUDE.md` | changed | New names for future work |
| `API Document/backend.md`, `API Document/frontend.md` | changed | Routes renamed, naming note |
| `planning/sprint01-public-read/public-game-page/`, `planning/sprint03-host-board/create-game/`, `planning/sprint05-player-loop/my-match-assignment/` | renamed folders (`git mv`) | Feature names |

Deleted files: none.

## Commands executed

See [F02 implement.md](../public-game-page/implement.md) "Commands executed": both features were built and verified in the same runs on 2026-11-18 (WSL).

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | Live stack on the existing WSL database | `PASS` | Flyway log "Migrating schema "playmatch" to version "2 - rename session to game"", "Successfully applied 1 migration"; auth and player history show V2 `success = t`; afterwards `playmatch.game` 1 row, `playmatch.attendance` 8 rows |
| AC-02 | `PlaymatchSchemaIT` (9 tests), `AuthSchemaIT` (4), `PlayerSchemaIT` (4) | `PASS` | `mvn -B clean verify`, `BUILD SUCCESS` |
| AC-03 | `seedIsIdempotent` + live log | `PASS` | log "FairCourt local demo seed (playmatch) already present \| public code: 7RQGQ484" |
| AC-04 | `MatchSuggesterTest` | `PASS` | 13 tests, 0 failures |
| AC-05 | Pipeline | `PASS` | F02 implement.md |

## Deviations and notes

- The existing local database keeps the demo title "FairCourt demo session" (seeded before the rename; the seed does not update existing rows). New databases get "FairCourt demo game". Changing the existing row needs either an SQL update or a fresh database volume; both wait for the owner.
- Older planning documents (system-design, ROADMAP history, other feature READMEs) still say "session"; not rewritten (scope). `conventions.md` "Domain words" explains the mapping.
- Not verified: the V2 migration on the Mac's database (only WSL was run).
