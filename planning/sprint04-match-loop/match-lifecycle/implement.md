# F11 implementation — Match start / finish / cancel

Design: [design.md](design.md) (`APPROVED` 2026-11-27)

Branch: `feature/match-lifecycle`, cut from `main` at `8397441` on 2026-11-27 (Match confirm was already merged: PR #8). Review diff base = `main`. Nothing is committed or pushed.

## Tasks

Small, runnable steps in order. Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T00 | — | `git fetch`; fast-forward local `main`; cut `feature/match-lifecycle` from the right base (see above). No commit, no push | `DONE` |
| T01 | AC-01…AC-04 | `playmatch/match/MatchState`: add `canStart()` (`PLANNED`), `canFinish()` (`ACTIVE`), `canCancel()` (`PLANNED` or `ACTIVE`) (AD-03). `playmatch/attendance/AttendanceState`: add `expectedInMatch(MatchState)` → `ASSIGNED` for `PLANNED`, `PLAYING` for `ACTIVE`, else no value (caller treats as conflict). `MatchStateTest` (new), `AttendanceStateTest`: TC-01, TC-02 | `DONE` |
| T02 | AC-01…AC-04, AC-08, AC-09 | `HostMatchStateResponse` record (`matchId`, `matchNo`, `courtNo`, `state`). `MatchService.start/finish/cancel(CallerIdentity, UUID matchId)`, each one `@Transactional`, sharing a private load-and-lock exactly as design §4 (match → game id, 404 if none → `requireHost` → game `FOR SHARE`, `CLOSED` → 409 `STATE_CONFLICT` → match `FOR UPDATE` → transition check 409 → the match's attendance rows `FOR UPDATE OF a` ordered by id → expected state check 409) then the writes of design §4 (start / finish / cancel), one `clock.instant()` per command, affected-row counts checked (mismatch throws → rollback). `JdbcClient` named parameters, no string concatenation | `DONE` |
| T03 | AC-07 | `HostMatchLifecycleController`: `PATCH /api/v1/host/matches/{matchId}/start`, `/finish`, `/cancel`; `policy.requireAccount` first, malformed id → 404, `200` + `Cache-Control: no-store`, no `@RequestBody`. `playmatch/security/SecurityConfig`: three `PATCH` rules, same style as the attendance rule | `DONE` |
| T04 | AC-07 | `gateway/routing/GatewayRouteTable`: routes `host-match-start`, `host-match-finish`, `host-match-cancel` (`PATCH`, `/api/v1/host/matches/*/<action>`, `RouteAccess.HOST`, playmatch). Rows in `GatewayAccessMatrixIT` (TC-18) and `PlaymatchServiceAccessIT` (TC-19) | `DONE` |
| T05 | AC-01…AC-05, AC-08, AC-09 | `MatchLifecycleIT` (TC-03…TC-17): same harness as `MatchConfirmIT` (test identity token v2, test clock, inline SQL helpers, player-service stub only for TC-12). Add planned / active match builders only if `support/TestData` has none | `DONE` |
| T06 | AC-06 | Frontend API: `api/matches.ts` `startMatch`, `finishMatch`, `cancelMatch` (`apiPatch`, existing `client.ts` helper, CSRF included); `api/types.ts` `HostMatchState`. Copy keys of design §7 (all approved 2026-11-27) in `copy/en.ts` | `DONE` |
| T07 | AC-06 | `components/CourtCard.tsx`: optional `actions` slot below the teams (card stays presentational). New `features/host-board/CourtActions.tsx`: `PLANNED` → Start match (primary) + Cancel match; `ACTIVE` → Finish (primary) + Cancel match; Finish and Cancel use existing `ConfirmDialog`, Start acts at once; hidden when the game is `CLOSED`; disabled while any action saves (button shows "Saving…"). `HostBoardPage.tsx`: one `useMutation` for match actions next to the attendance one; success → reload board; 409 → `matchConflict` text + reload; 401 → login with `next`; 403 → existing forbidden page; other → `ErrorBanner` with retry; both mutations block each other while saving. Layout per design §7 (≥ 44px, stacked on phone) | `DONE` |
| T08 | — | Docs: `API Document/backend.md` (three routes, errors), `API Document/frontend.md` (court card actions), `.claude/flow/maps/api-routes.md` (three rows; "Not built yet" line), `.claude/flow/maps/index.md` (new test classes) | `DONE` |
| T09 | all | `backend-format`, frontend `format`, `scripts/precommit.sh` in WSL; fill "Commands executed" and "Acceptance evidence"; manual TC-20, TC-21 are the owner's | `DONE` for the parts run; `scripts/precommit.sh` as one run is `NOT VERIFIED` (crashed the WSL VM twice, see "Commands executed"); manual TC-20, TC-21 `NOT VERIFIED` (owner) |

No new dependency, no migration, no new file outside the table in "Files".

## Test cases

At least one case per AC, negative and edge cases included. Oracle = where the expected value comes from; `agent-derived` = I computed it, challenge it if wrong.

### Unit — backend (T01)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-01…AC-04 | unit `MatchStateTest` | each of the 4 `MatchState` values | `canStart`, `canFinish`, `canCancel` | start only `PLANNED`; finish only `ACTIVE`; cancel `PLANNED` and `ACTIVE`; `FINISHED` and `CANCELLED` allow nothing | §11 "Match states" |
| TC-02 | AC-01…AC-04 | unit `AttendanceStateTest` | `PLANNED`, `ACTIVE`, `FINISHED`, `CANCELLED` | `expectedInMatch` | `ASSIGNED`, `PLAYING`, none, none | §11 "cancelling a planned match changes `ASSIGNED → READY`; … active … `PLAYING → READY`" |

### Integration — `MatchLifecycleIT` (T05), playmatch against `faircourt_test`

"Unchanged" = `play_match` columns (`state`, `started_at`, `finished_at`), `match_player` `released_at`, every attendance `state`, `available_since`, `matches_played` and `game.next_match_no` equal before and after. Fixture: DOUBLES game, 2 courts, 8 READY players p1–p8, test clock `T`, helper creates a match through the real confirm route (so the fixture is what confirm produces).

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-03 | AC-01 | IT | Match 1 `PLANNED` on court 1 with p1–p4 (`ASSIGNED`) | host `PATCH …/matches/{id}/start` | 200, `Cache-Control: no-store`, body exactly `{matchId, matchNo: 1, courtNo: 1, state: "ACTIVE"}`; `started_at` = `T`, `finished_at` null; p1–p4 `PLAYING`; 4 `match_player` rows still `released_at` null; p5–p8 `READY`; counters and `available_since` unchanged | §13 start; §11 |
| TC-04 | AC-02 | IT | Match 1 `ACTIVE` (p1–p4 `PLAYING`); p5–p8 `READY` with `available_since` older than `T` | host `PATCH …/finish` | 200, body `state: "FINISHED"`; `finished_at` = `T`; 4 `match_player.released_at` = `T`; p1–p4 `READY`, `matches_played` 1, `available_since` = `T`; p5–p8 completely unchanged | §11 "Finishing…"; §12 |
| TC-05 | AC-03 | IT | Match `PLANNED` (p1–p4 `ASSIGNED`) | host `PATCH …/cancel` | 200, `state: "CANCELLED"`; 4 `released_at` = `T`; p1–p4 `READY`; `matches_played` 0 and `available_since` unchanged; `started_at` and `finished_at` null | §11; design D-1 (owner 2026-11-27) |
| TC-06 | AC-03 | IT | Match `ACTIVE` after start (p1–p4 `PLAYING`) | host cancel | 200 `CANCELLED`; players `READY`, counters and `available_since` unchanged; `started_at` kept, `finished_at` null; released | §11; design D-1, D-4 |
| TC-07 | AC-04 | IT | Matches in each state | every illegal move: start on `ACTIVE` / `FINISHED` / `CANCELLED`; finish on `PLANNED` / `FINISHED` / `CANCELLED`; cancel on `FINISHED` / `CANCELLED` | each 409 `STATE_CONFLICT`, message "This changed in the meantime. Refresh and try again."; unchanged | §11 transitions; design D-5 |
| TC-08 | AC-04 | IT | Same match: start twice; finish twice; cancel twice | second call | first 200, second 409; state and counters as after the first call only (`matches_played` 1, not 2) | design D-5 |
| TC-09 | AC-04 | IT | Game `CLOSED`, with an `ACTIVE` match | start / finish / cancel | 409 `STATE_CONFLICT`; unchanged | §11 "closed … no new assignments"; design D-3 |
| TC-10 | AC-04 | IT | `ACTIVE` match; one player row set to `READY` directly in SQL (drift) | finish; then (fresh) start on a `PLANNED` match whose player is `PLAYING` | 409 `STATE_CONFLICT`; unchanged (match not finished, no counter moved) | design §3 last row; agent-derived |
| TC-11 | AC-05 | IT | Match on court 1 finished (then, separately, cancelled) | host confirm court 1 with the same p1–p4 | 201 `PLANNED`, `matchNo` 2; court 1 busy again only by the new match | §17 "finish/cancel releases both" |
| TC-12 | AC-05 | IT | 1-court game, p1–p8 all 0 sets, equal `available_since`; match p1–p4 confirmed, started, finished | host `POST …/suggestions` (player stub for levels) | one proposal on court 1; its players are p5–p8, none of p1–p4 | §0 step 4; §17 "Second round accounts for time spent waiting" |
| TC-13 | AC-05 | IT | Match `PLANNED` on court 1 | cancel; then suggestions | proposal may include p1–p4 again (they are `READY` and not in an unreleased match) | §17 checklist |
| TC-14 | AC-08 | IT | SINGLES game, 1 court, 2 READY; confirm 1 + 1 | start, finish | both `PLAYING` then both `READY`; exactly 2 `match_player` rows released; both `matches_played` 1 | OD-20; design AC-08 |
| TC-15 | AC-09 | IT | `ACTIVE` match | two threads `finish` the same match together | exactly one 200 and one 409; `matches_played` 1 for each of the 4 (not 2); one `finished_at` | design AC-09; §15 |
| TC-16 | AC-09 | IT | `ACTIVE` match | two threads: one `finish`, one `cancel` | exactly one 200 and one 409; final state matches the winner; players consistent (`READY`; counters +1 only if finish won) | design AC-09 |
| TC-17 | AC-07 | IT | Valid match | other account · no token · token bound to another path (`PATCH …/start` token used on `…/finish`) · unknown match id · malformed id `abc` · match of another game's host | 403 `FORBIDDEN` · 401 · 401 · 404 · 404 · 403; unchanged, for each of the three routes | §13 status codes; identity token v2 |

### Access matrices (T04)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-18 | AC-07 | gateway IT | Downstream stub | `PATCH /api/v1/host/matches/{id}/start` (and `finish`, `cancel`) anonymous · guest cookie only · account without CSRF · account with CSRF; also `GET` same path and `PATCH …/matches/{id}/pause` | 401 · 401 · 403 `CSRF_TOKEN_INVALID` · forwarded once with a token bound to PATCH + this path; `GET` and the unknown action → not routed (401) | `GatewayAccessMatrixIT` pattern of Match confirm |
| TC-19 | AC-07 | playmatch IT | No identity token | the three routes | 401 JSON; routes open for `PATCH` only (not `GET`/`POST`) | `PlaymatchServiceAccessIT` pattern |

### Visual / manual (owner)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-20 | AC-06 | visual (owner, 320 / 768 / 1280px) | Seeded host, 2 courts, 8 Ready | confirm a match, then tap Start match; Finish (dialog); confirm a second, Start, Cancel match (dialog); keyboard only (Tab + Enter) | planned card "Next up" + Start match / Cancel match; active card "Playing" + Finish / Cancel match; Assigned and Playing player lists separate; Start has no dialog, Finish and Cancel do; buttons say "Saving…" and everything else is disabled while saving; after Finish the court says "Free" and the players are Ready again | §11 "UI must show `ASSIGNED` separately"; design §7 |
| TC-21 | AC-06, §0 step 4/6 | manual (owner) | Match active; second browser tab on the same board | finish in tab 1, then Start / Finish again in tab 2 | tab 2 shows "This match changed. The board was refreshed." and the board reloads; then restart Compose without removing volumes: board loads, finished match remains, court free | §0 steps 4, 6; design D-5 |

Questions for the owner (cases I could not derive from the criteria): none open. Agent-derived cases are TC-10 and the exact "two threads: finish vs cancel" outcome in TC-16 (any one winner is accepted; I assert only consistency).

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/MatchState.java` | changed | transition checks |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/AttendanceState.java` | changed | expected in-match state |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/HostMatchStateResponse.java` | added | response record |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/MatchService.java` | changed | start / finish / cancel |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/HostMatchLifecycleController.java` | added | three PATCH routes |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/security/SecurityConfig.java` | changed | three route rules |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/routing/GatewayRouteTable.java` | changed | three routes |
| `backend/services/gateway/src/test/java/…/GatewayAccessMatrixIT.java` | changed | TC-18 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/match/MatchStateTest.java` | added | TC-01 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/attendance/AttendanceStateTest.java` | changed | TC-02 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/MatchLifecycleIT.java` | added | TC-03…TC-17 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/PlaymatchServiceAccessIT.java` | changed | TC-19 |
| `frontend/src/api/matches.ts`, `frontend/src/api/types.ts` | changed | client calls, type |
| `frontend/src/copy/en.ts` | changed | approved copy keys |
| `frontend/src/components/CourtCard.tsx` | changed | actions slot |
| `frontend/src/features/host-board/CourtActions.tsx` | added | court buttons + dialogs |
| `frontend/src/features/host-board/HostBoardPage.tsx` | changed | match mutation, reload |
| `API Document/backend.md`, `API Document/frontend.md`, `.claude/flow/maps/api-routes.md`, `.claude/flow/maps/index.md` | changed | docs |

Deleted files: none (deletions need explicit owner consent).

## Commands executed

Only commands that were actually run, with their real result. Machine: Windows PC, all commands inside WSL Ubuntu through `wsl.exe` (Docker in WSL). Before the verify runs, the app containers (gateway, auth, player, playmatch, frontend, mailpit) were stopped with the owner's OK to free RAM; only postgres stayed up.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-27 | Windows + WSL | `docker compose run --rm --build backend-format` (Spotless apply) | PASS (exit 0), run before each verify below |
| 2026-11-27 | Windows + WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl services/playmatch-service,services/gateway -am verify` (first, by the implementer, 6 attempts) | NO RESULT: the WSL VM died each time ("Insufficient system resources") with the app containers still running |
| 2026-11-27 | Windows + WSL | same `mvn verify`, run 1 (after the app containers were stopped) | FAIL: gateway 114 tests, 1 failure (`GatewayAccessMatrixIT` TC-18, wrong expected status, see Deviations); playmatch skipped |
| 2026-11-27 | Windows + WSL | same `mvn verify`, run 2 | FAIL: gateway PASS; playmatch 166 tests, 3 errors in `MatchLifecycleIT` (test helper could not read a null time, see Deviations) |
| 2026-11-27 | Windows + WSL | same `mvn verify`, run 3 | PASS: `BUILD SUCCESS`; gateway 114 tests, playmatch 166 tests (`MatchLifecycleIT` 15, `MatchStateTest` 4), 0 failures |
| 2026-11-27 | Windows + WSL | `bash scripts/precommit.sh` (two runs) | NO RESULT: the WSL VM died both times, in the auth-service tests (first run's log lost; second run exit 253) |
| 2026-11-27 | Windows + WSL | `docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm test && npm run build"` | PASS (exit 0): format check, Vitest, Vite build |
| 2026-11-27 | Windows + WSL | `mvn -B -pl services/player-service -am verify`, then `-pl services/auth-service -am verify` (run one after the other) | PASS: player-service 23 tests, auth-service 42 tests, `BUILD SUCCESS` each |
| 2026-11-27 | Windows + WSL | After review fixes round 1: `backend-format`; `mvn verify` for playmatch + gateway; frontend `npm run format && npm run format:check && npm test && npm run build` (by the implementer) | PASS: `BUILD SUCCESS`, playmatch 166 tests, `MatchLifecycleIT` 15; Vitest 11 tests; build OK. Gateway total not read from the log, overall `BUILD SUCCESS` |

Not run: `scripts/precommit.sh` as one successful run (the VM crashes in its auth-service step, which this feature did not touch); the same checks were run piece by piece above. No browser run.

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-03 `MatchLifecycleIT` | `PASS` | `mvn verify` run 3 and the post-fix run: `BUILD SUCCESS`, `MatchLifecycleIT` 15 tests 0 failures |
| AC-02 | TC-04 | `PASS` | same run |
| AC-03 | TC-05, TC-06 | `PASS` | same run |
| AC-04 | TC-07…TC-10 (+ `MatchStateTest` TC-01, `AttendanceStateTest` TC-02) | `PASS` | same run; each 409 case compares a full row snapshot before and after |
| AC-05 | TC-11…TC-13 | `PASS` | same run |
| AC-06 | TC-20, TC-21 | `NOT VERIFIED` | frontend format, Vitest and build pass, but no browser or UI test of the court buttons, dialogs and 409 text exists; owner visual and manual check |
| AC-07 | TC-17…TC-19 | `PASS` | `MatchLifecycleIT` TC-17, `GatewayAccessMatrixIT` TC-18, `PlaymatchServiceAccessIT` TC-19, same runs |
| AC-08 | TC-14 | `PASS` | same run |
| AC-09 | TC-15, TC-16 | `PASS` | same run (two-thread tests) |

TC-01…TC-19 each have a test in the passing run. Of these, TC-10 and the TC-16 outcome were agent-derived (marked in the plan).

## Deviations from the design

- `GatewayAccessMatrixIT` TC-18: the plan said an unknown action (`pause`) answers 401. The gateway answers 401 to anonymous callers and 403 to a signed-in caller (the same as the existing signed-in `GET` on a `PATCH`-only route). The test now asserts both: anonymous 401, signed-in 403, never forwarded. The plan's expectation was wrong; the test is stricter, not weaker (found in run 1, fixed, run 3 green).
- `MatchLifecycleIT`: the `matchTime` helper reads a nullable time with `list().getFirst()` because `single()` refuses a null value (found in run 2).
- TC-03 in `MatchLifecycleIT`: the unused `others` capture was removed after the review; the per-player loop already checks all 8 players.
- File outside the plan's Files table: `frontend/src/features/host-board/PlayerList.tsx` (new `busy` prop, review fix round 1, no behaviour change beyond the planned "all buttons disabled while one saves").
- Frontend review fixes (no behaviour change): "Saving…" now shows on the button of the action being saved; the board passes a `busy` flag to `PlayerList` instead of an empty-string `savingId`.
- `MatchService`: three fixed SQL blocks (`markStarted`, `markFinished`, `markCancelled`) replaced the single method that joined a constant fragment into the SQL.
- Draft acceptance table in the feature `README.md` marked superseded by `design.md`.
