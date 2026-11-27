# F10 implementation — Match confirm + tap-to-swap review

Design: [design.md](design.md) (`APPROVED` 2026-11-26)

Branch: `feature/match-confirm`, cut from `feature/match-suggestions` (Match suggestions is `IN_REVIEW`, not yet on `main`; stacked branch per `.claude/flow/tracker.md`). Review diff base: `feature/match-suggestions`.

## Tasks

Small, runnable steps in order. Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01, AC-02 | `playmatch/attendance/AttendanceState`: add `canBeAssigned()` (`this == READY`), the transition check for `READY → ASSIGNED` (AD-03). Host manual moves unchanged. `AttendanceStateTest`: TC-01 | `DONE` |
| T02 | AC-03, AC-07 | `playmatch/match/MatchLineup` record (`teamA`, `teamB`): `of(List<UUID> a, List<UUID> b, MatchFormat format)` → doubles 2 + 2, singles 1 + 1, all ids distinct, else `ApiException(VALIDATION_FAILED)`; `all()` = every id sorted (lock order). `MatchLineupTest`: TC-02…TC-04 | `DONE` |
| T03 | AC-01…AC-03, AC-05, AC-07 | `ConfirmMatchRequest` (`@NotNull @Min(1) @Max(4) Integer courtNo`, `@NotNull @Valid Teams teams`, `Teams(@NotNull @Size(min=1,max=2) List<UUID> A, … B)` with JSON names `A`/`B`), `ConfirmMatchResponse` (`matchId`, `matchNo`, `courtNo`, `state`). `MatchService.confirm(caller, gameId, request)` — one `@Transactional`, steps exactly as design §4 (requireHost → game `FOR UPDATE` → closed 409 `STATE_CONFLICT` → `MatchLineup.of` 400 → court range/busy 409 → attendances `FOR UPDATE` ordered by id → missing / not `canBeAssigned` / unreleased `match_player` → 409 `MATCH_CONFLICT` → inserts + `next_match_no + 1` + `ASSIGNED`; `DuplicateKeyException` → 409 `MATCH_CONFLICT`); `Clock` for `created_at`; `UUID.randomUUID()` for the match id. `HostMatchController`: `POST /api/v1/host/games/{gameId}/matches`, `requireAccount` before the id is parsed, malformed id → 404, `@Valid @RequestBody`, `201` + `Cache-Control: no-store`. `playmatch/security/SecurityConfig`: route authenticated (POST only) | `DONE` |
| T04 | AC-05 | Gateway `routing/GatewayRouteTable`: route `host-matches`, `POST /api/v1/host/games/*/matches` → playmatch, account required, CSRF, identity token v2. Rows in `GatewayAccessMatrixIT` (TC-20) and `PlaymatchServiceAccessIT` (TC-21) | `DONE` |
| T05 | AC-01…AC-05, AC-07, AC-08 | `MatchConfirmIT` (TC-05…TC-19), same harness as `MatchSuggestionsIT` / `HostAttendanceIT` (test identity token v2, test clock, inline SQL helpers; player-service stub only where the board / suggestions are read in TC-18) | `DONE` |
| T06 | AC-06 | Vitest (OD-08 b, owner 2026-11-26): `frontend/package.json` devDependency **`vitest` `5.1.0`** (exact pin; see "Version" below) + `package-lock.json` via `npm install`; script `"test": "vitest run"`. No Vitest config file (defaults find `src/**/*.test.ts`). Rebuild the frontend image and refresh the running container's `node_modules` (`docker compose build frontend`, `docker compose exec -T frontend npm install`, memory note). `scripts/precommit.sh` step 4 becomes `npm run format:check && npm test && npm run build` (adds a check; nothing removed) | `DONE` |
| T07 | AC-06 | `features/match-review/lineup.ts` (pure, no React): types `Slot`, `CourtLineup`, `LineupState`; `initLineup(suggestions, board)`, `lineupReducer(state, action)` with actions `tap`, `saved`, `reset` exactly as design §7. `lineup.test.ts`: TC-22…TC-30 | `DONE` |
| T08 | AC-06 | UI: `api/types.ts` (`ConfirmMatchRequest`, `ConfirmedMatch`), `api/matches.ts` `confirmMatch(gameId, request)` (POST + CSRF via `client.ts`); `MatchReviewPage.tsx` holds `useReducer(lineupReducer)` + one `useMutation` per court (keyed by court), 401 → login with `next`, 403 → `hostBoard.forbidden`, 409 → `matchReview.conflict` inline on that court with edits kept, other → `ErrorBanner`; Confirm of other courts disabled while one saves; `MatchProposal.tsx` player `<button aria-pressed>` around `PlayerChip`, selected ring + check icon, edited court hides tags/warnings + `matchReview.edited`, Confirm button / "Saving…" / "Saved as match N." + `StatusBadge` "Next up"; new `BenchList.tsx` ("Ready, not picked", hidden when empty); hint line + `aria-live` selected text; copy keys from design §7 (approved) in `copy/en.ts` | `DONE` |
| T09 | — | Docs: `API Document/backend.md` (route, request, response, errors), `API Document/frontend.md` (review page swap + confirm), `.claude/flow/maps/api-routes.md` (row), `.claude/flow/maps/index.md` (new test classes, `lineup.test.ts`, Vitest in Validators, "No frontend unit-test runner" line updated) | `DONE` |
| T10 | all | Format (`backend-format`, frontend `format`), `scripts/precommit.sh` in WSL; fill "Commands executed" and "Acceptance evidence"; manual TC-31…TC-34 are the owner's | `DONE` (automated parts; manual TC-31…TC-34 `NOT VERIFIED`, owner) |

### Version (T06)

Read from the npm registry on 2026-11-26 (`npm view vitest …` in the frontend container, WSL):

| Version | Published | Peer `vite` | `engines.node` |
|---|---|---|---|
| 5.1.2 (`latest`) | 2026-11-25 | `^6.4.0 \|\| ^7.0.0 \|\| ^8.0.0` | `^22.12.0 \|\| ^24.0.0 \|\| >=26.0.0` |
| 5.1.0 | 2026-11-02 | same | same |

Proposed pin: **5.1.0** (more than two weeks old; fits Vite 8.3.7 and Node 24.22.2). Owner approves the exact version at Gate 2. Not verified yet: that it installs and runs in the container (T06 does that).

## Test cases

At least one case per AC, negative and edge cases included. Oracle = where the expected value comes from.

### Unit — backend (T01, T02)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-02 | unit `AttendanceStateTest` | each `AttendanceState` | `canBeAssigned()` | true only for `READY` | §12 "are `READY` when planned" |
| TC-02 | AC-03 | unit `MatchLineupTest` | DOUBLES; A = [a, b], B = [c, d] distinct | `of` | lineup; `all()` = the 4 ids sorted | §12 "four distinct … two per team" |
| TC-03 | AC-03 | unit | DOUBLES; [a, a]+[c, d] · [a, b]+[a, d] · [a]+[c, d] · [a, b, c]+[d] (size check) · [a]+[b] | `of` | each → `ApiException` `VALIDATION_FAILED` | §12; design §3 |
| TC-04 | AC-07 | unit | SINGLES; [a]+[b] · [a]+[a] · [a, b]+[c, d] | `of` | ok · 400 · 400 | OD-20; design D-2 |

### Integration — `MatchConfirmIT` (T05), playmatch against `faircourt_test`

"Unchanged" = `play_match` and `match_player` row counts, every attendance `state`, `available_since`, `matches_played` and `game.next_match_no` equal before and after.

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-05 | AC-01 | IT | DOUBLES game, 2 courts, `next_match_no` 1, 8 READY | host `POST …/matches` court 1, A=[p1,p2], B=[p3,p4] | 201, `Cache-Control: no-store`; body exactly `{matchId (UUID), matchNo: 1, courtNo: 1, state: "PLANNED"}` (whole-body equality except the generated id); 1 `play_match` row (`PLANNED`, court 1, match_no 1, `created_at` = test clock); 4 `match_player` rows, teams A,A,B,B, `released_at` null; p1–p4 `ASSIGNED`; p5–p8 still `READY`; `next_match_no` 2; `available_since`, `matches_played` unchanged for all | §13, §15; design §4 |
| TC-06 | AC-01 | IT | After TC-05 | confirm court 2 with p5–p8 | 201, `matchNo` 2; `next_match_no` 3 | §15 allocate/increment |
| TC-07 | AC-02 | IT | p4 is `PENDING` (then `LEFT`, then `RESTING`) | confirm p1–p4 on court 1 | 409 `MATCH_CONFLICT`, message "A player or court is no longer available."; unchanged | §13 error body |
| TC-08 | AC-02 | IT | p1–p4 already in a PLANNED match on court 1 | confirm p1, p5, p6, p7 on court 2 | 409 `MATCH_CONFLICT`; unchanged | §12 "no other unreleased match" |
| TC-09 | AC-02 | IT | p4 `READY` but still has an unreleased `match_player` row (inconsistent data guard) | confirm p1–p4 | 409; unchanged | §12; design §4 step 4 |
| TC-10 | AC-02 | IT | Court 1 has a PLANNED match (then: ACTIVE) | confirm 4 other READY on court 1 | 409; unchanged | §12 one busy match per court |
| TC-11 | AC-02 | IT | 2-court game | confirm on court 3 | 409 `MATCH_CONFLICT`; unchanged | design D-3 |
| TC-12 | AC-02 | IT | p4 is an attendance of **another** game (READY) · a random UUID | confirm p1, p2, p3 + that id | 409 (not 404); unchanged in both games | §12 "belong to its session"; design §3 |
| TC-13 | AC-03 | IT | 8 READY | body: duplicate id · A of 1 on doubles · `teams` missing · id `"abc"` · `courtNo` 0 · `courtNo` 5 · no body | 400 `VALIDATION_FAILED`; unchanged | §13 "`400` invalid payload"; design §3 |
| TC-14 | AC-04 | IT | 8 READY, 2 free courts | two threads start together: court 1 = p1–p4, court 2 = p4–p7 (p4 shared) | exactly one 201 and one 409 `MATCH_CONFLICT`; 1 `play_match`, 4 `match_player`; exactly 4 attendances `ASSIGNED`; `next_match_no` 2 | §0 step 4; §15 lock |
| TC-15 | AC-04 | IT | 8 READY | two threads: both court 1, disjoint players | one 201, one 409; 1 match | §12 one busy match per court |
| TC-16 | AC-05 | IT | Valid game | other account · no token · token for another path (`POST …/suggestions`) · unknown game id · malformed id | 403 `FORBIDDEN` · 401 · 401 · 404 · 404; unchanged | §13 status codes; identity token v2 |
| TC-17 | AC-05 | IT | CLOSED game, 4 READY | host confirm | 409 `STATE_CONFLICT`; unchanged | §11 "no new assignments" |
| TC-18 | AC-08 | IT | After TC-05 (player-service stub for levels) | host `GET …/board`; host `POST …/suggestions` | board court 1 has the match (`PLANNED`, matchNo 1, teams as confirmed); suggestions contain only court 2 and none of p1–p4 | §5.1; design AC-08 |
| TC-19 | AC-07 | IT | SINGLES game, 1 court, 2 READY | confirm A=[s1], B=[s2] · then on a fresh game A=[s1,s2], B=[s3,s4] | 201, 2 `match_player` rows, both `ASSIGNED` · 400; unchanged | OD-20; design D-2 |

### Access matrices (T04)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-20 | AC-05 | gateway IT | Downstream stub | `POST /api/v1/host/games/{id}/matches` anonymous · guest cookie only · account without CSRF · account with CSRF | 401 · 401 · 403 `CSRF_TOKEN_INVALID` · forwarded once with a token bound to POST + this path; `GET` same path → not routed | suggestions TC-23 pattern |
| TC-21 | AC-05 | playmatch IT | No identity token | `POST …/matches` | 401 JSON; route open for POST only | `PlaymatchServiceAccessIT` pattern |

### Unit — frontend `lineup.test.ts` (T07, Vitest)

Fixture: 2 courts, court 1 A=[a1,a2] B=[b1,b2], court 2 A=[c1,c2] B=[d1,d2], bench [e1, e2].

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-22 | AC-06 | unit | `initLineup` from suggestions + board with READY e1, e2 not in any proposal, plus a PENDING and an ASSIGNED player | init | courts as suggested, `edited` false, `saved` null; bench = [e1, e2] in board order (PENDING/ASSIGNED excluded); nothing selected | design §7 |
| TC-23 | AC-06 | unit | nothing selected | tap a1 · tap a1 again | a1 selected · nothing selected, no change | §16 "tap a player then another" |
| TC-24 | AC-06 | unit | a1 selected | tap a2 | a1/a2 swapped within team A; court 1 `edited`; nothing selected | agent-derived (design §7 "swap the two players") |
| TC-25 | AC-06 | unit | a1 selected | tap b2 | court 1 A=[b2,a2], B=[b1,a1]; `edited` | §14 "swap" |
| TC-26 | AC-06 | unit | a1 selected | tap d1 | court 1 A=[d1,a2]; court 2 B=[a1,d2]; both `edited` | §14 "swap across courts" |
| TC-27 | AC-06 | unit | b1 selected | tap e2 | court 1 B=[e2,b2]; bench=[e1,b1]; court 1 `edited` | §0 step 3 "swaps them in"; §14 "replace any player" |
| TC-28 | AC-06 | unit | e1 selected | tap e2 | nothing changes except selection cleared | design §7 |
| TC-29 | AC-06 | unit | court 1 `saved(1, 3)` | tap a1; with c1 selected tap a1 | court 1 `saved` {matchNo 3}; its slots not selectable, nothing swapped | design §7 |
| TC-30 | AC-06 | unit | edited state | `reset` with new suggestions | state equals `initLineup` of the new data | design §7 |

### Frontend (manual, owner) (T10)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-31 | AC-06 | UI | Seed game, host, 8 READY + 1 approved guest | review page; tap a player then the guest in "Ready, not picked"; Confirm on court 1 | swap visible; "You changed this court."; tags/warnings gone on that court; Confirm → "Saving…" → "Saved as match 1." + "Next up"; board shows the match on Court 1 | §0 step 3; design §7 copy |
| TC-32 | AC-06 | UI | Same, keyboard only | Tab to a player, Enter, Tab to another, Enter, Tab to Confirm, Enter | same result; focus ring visible; screen-reader text "{name} selected. Tap another player to swap." present while selected | §16 keyboard, no drag-and-drop |
| TC-33 | AC-06 | UI | Review open; in another tab mark one of court 1's players Left on the board | Confirm court 1 | "This lineup changed. Refresh suggestions" on court 1; edits on court 2 kept; "Refresh suggestions" rebuilds | §16 409 copy |
| TC-34 | AC-06 | UI | Seed | 320, 375, 768, 1280px | phone: one court per row, bench below, sticky bar, no horizontal scroll; desktop 2 columns, max 1200px; all buttons ≥ 44px | §16 |

Questions for the owner: none — every case follows from the approved design.

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/AttendanceState.java` | changed | T01 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/MatchLineup.java` | added | T02 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/ConfirmMatchRequest.java` | added | T03 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/ConfirmMatchResponse.java` | added | T03 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/MatchService.java` | added | T03 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/HostMatchController.java` | added | T03 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/security/SecurityConfig.java` | changed | T03 |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/routing/GatewayRouteTable.java` | changed | T04 |
| `backend/services/gateway/src/test/java/com/faircourt/gateway/GatewayAccessMatrixIT.java` | changed | T04 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/PlaymatchServiceAccessIT.java` | changed | T04 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/attendance/AttendanceStateTest.java` | changed | T01 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/match/MatchLineupTest.java` | added | T02 |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/MatchConfirmIT.java` | added | T05 |
| `frontend/package.json`, `frontend/package-lock.json` | changed | T06 |
| `scripts/precommit.sh` | changed | T06 (adds `npm test`) |
| `frontend/src/features/match-review/lineup.ts` | added | T07 |
| `frontend/src/features/match-review/lineup.test.ts` | added | T07 |
| `frontend/src/api/types.ts`, `frontend/src/api/matches.ts` | changed | T08 |
| `frontend/src/features/match-review/MatchReviewPage.tsx`, `MatchProposal.tsx` | changed | T08 |
| `frontend/src/features/match-review/BenchList.tsx` | added | T08 |
| `frontend/src/copy/en.ts` | changed | T08 |
| `API Document/backend.md`, `API Document/frontend.md` | changed | T09 |
| `.claude/flow/maps/api-routes.md`, `.claude/flow/maps/index.md` | changed | T09 |
| `planning/sprint04-match-loop/match-confirm/implement.md`, `review.md`, `README.md`, `planning/README.md`, `planning/sprint04-match-loop/SPRINT.md` | changed / added | T10 evidence + status |

Deleted files: none (deletions need explicit owner consent).

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend npm view vitest dist-tags time.modified --json` | PASS — `latest` 5.1.2 |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend npm view vitest@5.1.2 peerDependencies engines time --json` | PASS — table above |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend npm view vitest@5.1.0 peerDependencies.vite engines.node` | PASS — same ranges |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend npm install --save-dev --save-exact vitest@5.1.0` (frontend implementer) | PASS — 16 packages added, 0 vulnerabilities; lock resolves 5.1.0 |
| 2026-11-26 | Windows / WSL | `docker compose build frontend`; `docker compose run --rm --no-deps frontend npm install` (frontend implementer) | PASS — the first `npm test` before the refresh failed with "vitest: not found" (stale `node_modules` volume) |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm test && npm run build"` (frontend implementer) | PASS — 9 tests; after fix round 1: PASS — 11 tests |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --build backend-format` (backend implementer, 3×) | PASS |
| 2026-11-26 | Windows / WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl services/playmatch-service,services/gateway -am verify` (backend implementer) | 1st FAIL (TC-21: empty body gave 400 before 401); 2nd PASS; after fix round 1 run twice, both PASS — gateway 113 tests, playmatch 16 unit + 150 IT, 0 failures; TC-14/TC-15 races passed in both runs |
| 2026-11-26 | Windows / WSL | `bash scripts/precommit.sh` (orchestrator, before review) | PASS — `PIPELINE OK` |
| 2026-11-26 | Windows / WSL | `bash scripts/precommit.sh` (orchestrator, after fix round 1) | PASS — `PIPELINE OK`; backend `BUILD SUCCESS` (all modules 0 failures, playmatch IT 150); Vitest 1 file, 11 passed |
| 2026-11-27 | Windows / WSL | `bash scripts/precommit.sh` after the Gate 3 changes (`PlayerSlotButton.tsx` split out, Refresh disabled while a court saves) | PASS (`PIPELINE OK`) |

Counts reported by the implementers are as they reported them; the orchestrator's own pipeline log confirms the 113 / 16 / 150 and 11 counts.

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-05, TC-06, TC-10 (`MatchConfirmIT`) | `PASS` | pipeline 2026-11-26 after fix round 1 |
| AC-02 | TC-01 (`AttendanceStateTest`), TC-07…TC-12 (`MatchConfirmIT`, row snapshot unchanged) | `PASS` | same run |
| AC-03 | TC-02, TC-03 (`MatchLineupTest`), TC-13 + `aBadBodyDoesNotHideAnUnauthenticatedUnknownOrForbiddenCaller` (`MatchConfirmIT`) | `PASS` | same run |
| AC-04 | TC-14, TC-15 (two threads) | `PASS` | passed in 3 runs (2 implementer, 1 pipeline); flakiness beyond that not measured |
| AC-05 | TC-16, TC-17, wrong-audience token test (`MatchConfirmIT`), TC-20 (`GatewayAccessMatrixIT`), TC-21 (`PlaymatchServiceAccessIT`) | `PASS` | same run; guest-only caller proven at the gateway only (services have no guest caller kind) |
| AC-06 | TC-22…TC-30 + 2 added cases (`lineup.test.ts`, Vitest) | `PASS` (logic) | Vitest 11 passed |
| AC-06 | TC-31…TC-34 (browser, keyboard, layout) | `NOT VERIFIED` | manual, owner |
| AC-07 | TC-04 (`MatchLineupTest`), TC-19 (`MatchConfirmIT`) | `PASS` | same run |
| AC-08 | TC-18 (`MatchConfirmIT`) | `PASS` | same run |

## Deviations from the design

- Known convention gaps followed as in earlier features: no JPA entities (`JdbcClient` writes, design A-2); no `support/TestData` builders in playmatch tests (inline SQL helpers as in `MatchSuggestionsIT`).
- Body validation moved from the controller (`@Valid`, conventions) into `MatchService`: the controller takes the raw body (`@RequestBody(required = false) String`), and the service parses it with `JsonMapper` + jakarta `Validator` after `requireHost`. Reason: design §3.1 error order (401 → 404 → 403 → 400), found in review. Older spot with the old order (400 before the access check): `HostAttendanceController` (not changed). Owner accepted this deviation at Gate 3 (2026-11-27).
- `lineup.ts` has, besides `tap` / `saved` / `reset`, a `saving` field and `saving` / `failed` actions: while a court saves, every player button is frozen (review finding, fix round 1). Two extra Vitest cases cover them.
- `reset` is dispatched by an effect when new review data arrives; because "Refresh suggestions" also remounts the court list, the effect is redundant today (harmless).
- `PlayerSlotButton` started inside `MatchProposal.tsx` (a new file was not in the plan); owner approved the move at Gate 3 (2026-11-27), now its own file `PlayerSlotButton.tsx`.
- "Refresh suggestions" is disabled while a court saves (owner approved at Gate 3, 2026-11-27; review finding 12).
- Not tested: the `DuplicateKeyException` → 409 path (design §4 step 6); the game row lock makes it hard to reach and no approved case covers it.
- Service-level guest caller row (review finding) replaced by a wrong-audience token → 401 test: `CallerIdentity` has no guest kind yet.
