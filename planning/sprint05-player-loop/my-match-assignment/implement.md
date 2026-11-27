# F12 implementation — My game: own assignment + polling

Design: [design.md](design.md) (`APPROVED` 2026-11-27)

Branch: `feature/my-match-assignment`, cut from `main` at `9878cef` (diff base for review: `main`).

## Tasks

Small, runnable steps in order. Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01, AC-02 | **playmatch-service read.** New record `attendance/MyAssignment(int matchNo, int courtNo, MatchState matchState)`. `MyAttendanceQuery.byAttendance` / `byPlayer` run the one SQL statement of design §4 (LEFT JOIN `match_player` with `released_at IS NULL` and `play_match`, both on the same `game_id`) and return `Optional<MyAttendanceRow>` (state + nullable `MyAssignment`). `MyAttendanceResponse` becomes `(AttendanceState attendanceState, MyAssignment assignment)`; `assignment` is serialized as `null` when absent. `MemberAttendanceService.myAttendance` maps the row; `join` keeps reading the state only. Existing `MemberAttendanceIT.myAttendanceByUserAndByAttendanceReturnOnlyTheOwnState` allowlist gains `assignment` (the approved new key, design §3) — the only change to an existing assertion. | `DONE` |
| T02 | AC-01, AC-02, AC-03 | **playmatch-service IT** `MyAssignmentIT` (new): drives the real host routes (confirm, start, finish, cancel) with test identity tokens like `MatchLifecycleIT`, then reads `GET /internal/v1/games/{gameId}/my-attendance`. Cases TC-01…TC-06, TC-09. Player-service replaced by the JDK `HttpServer` stub pattern already used in `MatchLifecycleIT` / `MemberAttendanceIT` (levels + member lookup). | `DONE` |
| T03 | AC-02, AC-03 | **gateway.** New record `player/MyAssignment(int matchNo, int courtNo, String matchState)`. `PlaymatchClient.myAttendance` returns state + assignment; an assignment with `matchState` other than `PLANNED`/`ACTIVE` or a non-positive number → `503 SERVICE_UNAVAILABLE` (same rule as a missing state today). `MyAttendanceResponse(AttendanceState, MyAssignment)`; `PlayerAccessController.me` passes it through. `StubService` gains a settable assignment answer for `my-attendance`. `PlayerAccessIT` cases TC-07, TC-08. | `DONE` |
| T04 | AC-04 | **frontend data + copy.** `api/types.ts`: `MyAssignment`, `MyMatchState = 'PLANNED' \| 'ACTIVE'`, `MyAttendance.assignment: MyAssignment \| null`. `copy/en.ts` `myGame`: the seven approved strings of design §7. `lib/status.ts`: `ATTENDANCE_STATUS.ASSIGNED` → `assigned`, `.PLAYING` → `playing` (separate labels, §11); new `MY_MATCH_STATUS` (`PLANNED` → "Your next match", positive; `ACTIVE` → "Playing now", positive). | `DONE` |
| T05 | AC-04 | **hooks.** `useAsync` gains `refresh()` (re-fetch without switching to `loading`; answer replaces the state like a normal answer; aborts a still-running earlier refresh). New `hooks/usePolling.ts`: `usePolling(callback, intervalMs, enabled)` — interval only while `document.visibilityState === 'visible'`, cleared when hidden, one immediate `callback()` on becoming visible again, cleanup on unmount / when `enabled` turns false. | `DONE` |
| T06 | AC-04 | **page.** `features/my-game/myCard.ts` (pure): `cardOf(me: MyAttendance)` → `{kind: 'state', state}` or `{kind: 'assignment', label, court, matchNo, hint}`; `pollingWanted(status, errorStatus, state)` per design D-2. Vitest `myCard.test.ts` (TC-10, TC-12). `MyAssignmentCard.tsx` (new, same card look as `StateCard`, `StatusBadge` + big "Court {n}" + "Match {n}" + hint). `MyGamePage.tsx`: picks the card, Refresh button calls `refresh()`, hint text "This page updates every 15 seconds." under it, `usePolling(mine.refresh, 15000, pollingWanted(...))`. `StateCard` comment updated (no look change). | `DONE` |
| T07 | all | **docs.** `API Document/backend.md` + `frontend.md`: `/me` and internal `my-attendance` answers with `assignment`; `.claude/flow/maps/api-routes.md` row for `/me` (+ `MyAssignmentIT`), `maps/index.md` test list (`MyAssignmentIT`, `myCard.test.ts`, `usePolling`). Evidence below. | `DONE` |
| T08 | all | **validate + review.** `scripts/precommit.sh` in WSL; fresh-session `flow-reviewer`, `scope-guardian`, `privacy-reviewer` in parallel on `main...HEAD` + working tree; fix rounds; `review.md`. TC-11 is the owner's browser check. | `DONE` |

## Test cases

Approved at Gate 2 together with the tasks. Oracle = where the expected value comes from.

| TC | Covers | Layer | Given | When | Then (status, body, rows, message) | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-01 | IT (playmatch, `MyAssignmentIT`) | Doubles game, 1 court, 5 READY attendees; host confirms 4 of them on court 1 (match 1) | each of the 4 reads own attendance (`?attendanceId=`) | `200`; body exactly `{"attendanceState":"ASSIGNED","assignment":{"matchNo":1,"courtNo":1,"matchState":"PLANNED"}}`; the fifth gets exactly `{"attendanceState":"READY","assignment":null}` | §13 example, §17 step 4 gate |
| TC-02 | AC-01 | IT (playmatch) | TC-01, host starts match 1 | a player of match 1 reads | `ASSIGNED` → `PLAYING`; `assignment.matchState` = `ACTIVE`, same `matchNo`/`courtNo` | §11 (`ASSIGNED → PLAYING`, `PLANNED → ACTIVE`), §13 start |
| TC-03 | AC-01 | IT (playmatch) | (a) an active match the host finishes; (b) a planned match the host cancels; (c) an active match the host cancels | a player of that match reads | each: `READY` + `assignment: null` | §11 finish/cancel → `READY`; §12 release on finish/cancel |
| TC-04 | AC-01 | IT (playmatch) | Planned match with 3 guests and 1 member (member's `player_id` known to the player stub) | guest reads by `?attendanceId=`, member reads by `?userId=` | both get their own `ASSIGNED` + the match's `matchNo`/`courtNo`/`PLANNED` | §13 "Member or scoped guest" |
| TC-05 | AC-01 | IT (playmatch) | Doubles game, 2 courts, 8 READY; host confirms match 1 on court 2, then match 2 on court 1 | a player of each match reads | court-2 player: `{matchNo:1, courtNo:2}`; court-1 player: `{matchNo:2, courtNo:1}` — never the other match's values | §11 privacy ("only their own … match number, court number") |
| TC-06 | AC-02 | IT (playmatch) | TC-01 and TC-02 answers | `JsonAllowlist.assertOnlyFields(body, "attendanceState", "assignment", "assignment.matchNo", "assignment.courtNo", "assignment.matchState")` | passes for `assignment` set and `null` (exact-body assertions in TC-01 also count) | §11, §13, AD-09 |
| TC-07 | AC-02 | IT (gateway, `PlayerAccessIT`) | Playmatch stub answers `{"attendanceState":"ASSIGNED","assignment":{"matchNo":3,"courtNo":2,"matchState":"PLANNED"}}` | signed-in member `GET /api/v1/games/{id}/me` | `200`, `no-store`, body exactly the same JSON; stub answer with an extra key inside `assignment` (e.g. `"team":"A"`) → the extra key is not in the gateway answer; stub `matchState: "FINISHED"` → `503 SERVICE_UNAVAILABLE` | §13 example; design §6 (gateway validation) — `agent-derived` for the extra-key and FINISHED cases |
| TC-08 | AC-03 | IT (gateway) | Playmatch stub holds an assignment for guest A's attendance only | (a) anonymous `GET /me`; (b) guest B's cookie of the same game; (c) a signed-in account with no row | (a) `401 UNAUTHENTICATED`, nothing forwarded; (b) the playmatch request carries B's attendance id, never A's, answer has B's data only; (c) `404` from playmatch passed through. Existing `GatewayAccessMatrixIT` rows for `/me` stay green | §0 step 5, §3 |
| TC-09 | AC-02, AC-03 | IT (playmatch) | Player P attends game X (READY, no match); game Y has a planned match (P not in it, P has no row in Y) | read Y with P's attendance id; read Y with P's player id | both `404 NOT_FOUND`, body has no `assignment` | §13 status codes, privacy-auth.md |
| TC-10 | AC-04 | unit (Vitest, `myCard.test.ts`) | `/me` answers | `cardOf(...)` | `READY`/`null` → state card READY; `ASSIGNED` + PLANNED court 2 match 3 → label "Your next match", court "Court 2", match "Match 3", hint "Go to the court. The host will start the match."; `PLAYING` + ACTIVE → "Playing now", hint "Your match is on."; `ASSIGNED` + `null` (defensive) → state card `ASSIGNED` ("You have a match.") | design §5, §7 copy table (owner-approved 2026-11-27) |
| TC-11 | AC-04 | visual (owner, running stack) | Guest page `/my/games/{id}` open on a phone-size window; host board open in another browser | host confirms → starts → finishes the guest's match; switch the guest tab away and back; tap Refresh | card changes within 15 s each time ("Your next match / Court n" → "Playing now" → "You're in."); no "Loading…" flash on a poll; DevTools Network: no `/me` requests while the tab is hidden, one request right after coming back; Refresh updates at once | §16, design D-2 — human check |
| TC-12 | AC-04 | unit (Vitest, `myCard.test.ts`) | page states | `pollingWanted(...)` | success + not `LEFT` → on; success + `LEFT` → off; error 401 → off; error 404 → off; error 503 / network (no status) → on; loading → off | design D-2 (owner-approved) |

No questions for the owner: every expected value above comes from the blueprint or the approved design, except the two gateway edge cases in TC-07 marked `agent-derived`.

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/MyAssignment.java` | added | T01 value record |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/MyAttendanceQuery.java` | changed | T01 SQL with assignment |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/MyAttendanceResponse.java` | changed | T01 `assignment` field |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/MemberAttendanceService.java` | changed | T01 mapping |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/MemberAttendanceIT.java` | changed | T01 allowlist gains `assignment` |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/MyAssignmentIT.java` | added | T02 |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/player/MyAssignment.java` | added | T03 |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/player/MyAttendanceResponse.java` | changed | T03 typed `assignment` |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/guest/PlaymatchClient.java` | changed | T03 read + validate assignment |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/player/PlayerAccessController.java` | changed | T03 pass-through |
| `backend/services/gateway/src/test/java/com/faircourt/gateway/StubService.java` | changed | T03 settable assignment answer |
| `backend/services/gateway/src/test/java/com/faircourt/gateway/PlayerAccessIT.java` | changed | T03 TC-07, TC-08 |
| `frontend/src/api/types.ts` | changed | T04 |
| `frontend/src/copy/en.ts` | changed | T04 approved strings |
| `frontend/src/lib/status.ts` | changed | T04 labels |
| `frontend/src/hooks/useAsync.ts` | changed | T05 `refresh()` |
| `frontend/src/hooks/usePolling.ts` | added | T05 |
| `frontend/src/features/my-game/myCard.ts` | added | T06 pure mapping |
| `frontend/src/features/my-game/myCard.test.ts` | added | T06 TC-10, TC-12 |
| `frontend/src/features/my-game/MyAssignmentCard.tsx` | added | T06 |
| `frontend/src/features/my-game/MyGamePage.tsx` | changed | T06 |
| `frontend/src/features/my-game/StateCard.tsx` | changed | T06 comment + defensive body text (see Deviations) |
| `API Document/backend.md`, `API Document/frontend.md` | changed | T07 |
| `.claude/flow/maps/api-routes.md`, `.claude/flow/maps/index.md` | changed | T07 |
| `planning/sprint05-player-loop/my-match-assignment/review.md` | added | T08 |

Deleted files: none (deletions need explicit owner consent).

No new dependency, route, table, migration, config or screen.

## Commands executed

Only commands that were actually run, with their real result. Machine: Windows PC, Docker in WSL (Ubuntu 24.04).

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-27 | Windows (Git Bash) | `git fetch`, `git switch main`, `git merge --ff-only origin/main`, `git switch -c feature/my-match-assignment` | PASS (main at `9878cef`) |
| 2026-11-27 | WSL (implementer) | `docker compose run --rm --build backend-format` | PASS |
| 2026-11-27 | WSL (implementer) | `docker compose --profile test run --rm --build backend-test mvn -B -pl services/playmatch-service,services/gateway -am verify` | first run FAIL (a new TC-08 test checked in twice in one browser; test fixed, no product change), rerun PASS (gateway 118 tests, playmatch 21 unit + 173 IT) |
| 2026-11-27 | WSL (implementer) | `docker compose run --rm --no-deps frontend sh -c "npm run format && npm run format:check && npm test && npm run build"` | first run FAIL (a truncated `myCard.ts` written by a shell heredoc; rewritten), rerun PASS |
| 2026-11-27 | WSL (implementer) | `docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm test"` | PASS (2 test files, 19 tests) |
| 2026-11-27 | WSL | `bash scripts/precommit.sh` | PASS (`PIPELINE OK`) |
| 2026-11-27 | WSL | `docker compose up -d --build gateway playmatch-service` | PASS (all services healthy) |
| 2026-11-27 | WSL | full-flow script against the running stack (local scratch file, not in the repo; steps below) | first run FAIL (gateway answered `000` right after the restart), rerun `E2E OK` (11/11 PASS) |
| 2026-11-27 | WSL | after review fix: `bash scripts/precommit.sh` | PASS (`PIPELINE OK`; `PlayerAccessIT` 27, `MemberAttendanceIT` 18, `MyAssignmentIT` 7, Vitest 2 files passed) |
| 2026-11-27 | WSL | after review fix: `docker compose up -d --build gateway` + full-flow script | `E2E OK` (11/11 PASS) |

Full-flow script steps (gateway `http://localhost:9090`, seeded demo accounts): host logs in and creates a doubles game with 2 courts → player1–4 log in and join → a guest checks in (`/me` = `PENDING`, `assignment: null`) → host approves the guest (`READY`, `null`) → host asks for suggestions and confirms the first court → the four in the match each read exactly `{"attendanceState":"ASSIGNED","assignment":{"matchNo":1,"courtNo":1,"matchState":"PLANNED"}}`, the fifth reads `READY` + `null` → anonymous `/me` = 401, player8 (not joined) = 404, `Cache-Control: no-store` → host starts: all four `PLAYING` + `ACTIVE` → host finishes: all five `READY` + `null` → frontend `/my/games/{id}` = 200 and serves the three new strings.

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-01…TC-05 (`MyAssignmentIT`), full-flow script | `PASS` | precommit 2026-11-27; `E2E OK` "four see own match, fifth sees none", start/finish steps |
| AC-02 | TC-06, TC-07, TC-09 (`MyAssignmentIT`, `PlayerAccessIT`) | `PASS` | precommit 2026-11-27; exact bodies + `JsonAllowlist` |
| AC-03 | TC-08 (`PlayerAccessIT`), TC-09, `GatewayAccessMatrixIT`, full-flow anonymous 401 / non-joined 404 | `PASS` | precommit 2026-11-27; `E2E OK` |
| AC-04 | TC-10, TC-12 (Vitest) `PASS`; TC-11 browser check | `NOT VERIFIED` | Vitest PASS proves the card and polling rules; live polling in a browser (15 s update, pause when hidden, no "Loading…" flash) is the owner's TC-11 check, not run |

## Deviations from the design

- `StateCard.bodyOf` returns "You have a match." for `ASSIGNED`/`PLAYING` (the defensive case of design §5/§7, TC-10). Without it the fallback card would repeat the new badge label as body text. Not only a comment change, as T06 first said.
- Row records are nested: `MyAttendanceQuery.MyAttendanceRow` (playmatch) and `PlaymatchClient.MyAttendance` (gateway); no extra top-level files.
- TC-08(b) is proven by the attendance id the gateway forwards to playmatch (the gateway stub has one global assignment answer, not one per guest). Per-row privacy is proven on the playmatch side (TC-04, TC-05, TC-09).
- Review fix: the gateway maps any unreadable playmatch answer (`RestClientException`, e.g. `"matchNo":"x"`) to `503` instead of a likely `500`; case added to `PlayerAccessIT` (TC-07). `myCard.pollingWanted` derives its status type from `AsyncState`.
- Known old habit kept (conventions say list it, not refactor): `MyAssignmentIT` copies the setup helpers of `MatchLifecycleIT` with raw `INSERT` text; playmatch has no `support/TestData` builders yet.
