# F07 implementation — Attendance management (approve, left)

Design: [design.md](design.md) — `APPROVED` 2026-11-26 (Gate 1). Plan and test cases approved 2026-11-26 (Gate 2).

## Tasks

Order: shared token change first (it touches every forwarded route), then backend of the feature, then frontend (owner rule: backend first, then its frontend). Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-05 | `common-web`: `IdentityToken` v2 — `issue(userId, audience, method, path)`, `verify(token, audience, method, path)`; v1 refused. Claims JSON built and parsed with a strict pattern as today (no new dependency). `CallerIdentityArgumentResolver` passes `spring.application.name`, `request.getMethod()`, `request.getRequestURI()` | `DONE` |
| T02 | AC-05 | Gateway: `GatewayRoute` gets `audience` (service name, `null` for local routes); `GatewayRoutes` sets it on every forwarded route; `IdentityTokenFilter` issues v2 with the route's audience and the request's method + path | `DONE` |
| T03 | AC-05 | Adjust existing tests that build v1 tokens (`IdentityTokenTest`, `CallerIdentityArgumentResolverTest`, playmatch `HostBoardIT`, `HostGamesIT`, `PlaymatchServiceAccessIT`, gateway `HostRoutesIT`) to v2; add TC-11…TC-13 | `DONE` |
| T04 | AC-02, AC-03 | `AttendanceState`: `isHostTarget()`, `hostMayMoveTo(target)`, `inMatch()`; unit test TC-01 | `DONE` |
| T05 | AC-01…AC-04 | playmatch: `ChangeAttendanceRequest`, `HostAttendanceResponse`, `AttendanceService.changeState` (one `@Transactional`, game `FOR SHARE`, attendance `FOR UPDATE`, no `available_since` change), `HostAttendanceController` (`PATCH`, `no-store`), `SecurityConfig` opens the route | `DONE` |
| T06 | AC-01…AC-04, AC-07 | playmatch IT `HostAttendanceIT` (TC-02…TC-10, TC-14…TC-16) + rows in `PlaymatchServiceAccessIT` | `DONE` |
| T07 | AC-04 | Gateway route `host-attendance` (`PATCH /api/v1/host/games/*/attendance/*`, `HOST`, → playmatch); rows in `GatewayAccessMatrixIT`; `HostRoutesIT` cases TC-17, TC-18 | `DONE` |
| T08 | AC-06 | Frontend: `apiPatch` in `api/client.ts`; `changeAttendance` in `api/host.ts`; `HostAttendanceChange` in `api/types.ts`; copy keys in `copy/en.ts` | `DONE` |
| T09 | AC-06 | Frontend: `components/ConfirmDialog.tsx`; `features/host-board/AttendanceActions.tsx`; `PlayerList.tsx` and `HostBoardPage.tsx` wiring (one mutation, all buttons disabled while saving, reload on success / conflict, closed note) | `DONE` |
| T10 | — | `API Document/` entry for the PATCH route + token v2 note; pre-commit pipeline `scripts/precommit.sh` | `DONE` (`API Document/backend.md` + `frontend.md`; `scripts/precommit.sh` PIPELINE OK) |
| T11 | AC-01, AC-06 | Live check in Docker (TC-21) + owner visual check (TC-19, TC-20) | `DOING` (TC-21 PASS; TC-19, TC-20 wait for the owner) |

## Test cases

Oracle: §n = blueprint; design = approved design.md section; owner = owner decision 2026-11-26; existing = behavior already shipped and tested.

| TC | Covers | Layer | Given | When | Then (status, body, rows, message) | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-02, AC-03 | unit `AttendanceStateTest` | every pair (from ∈ 6 states, to ∈ 6 states) | `isHostTarget(to)` and `hostMayMoveTo` | host targets = exactly {READY, LEFT}; allowed moves = exactly PENDING→READY, PENDING→LEFT, READY→LEFT; same state for READY and LEFT is "no change"; `inMatch()` true only for ASSIGNED, PLAYING | design §5, owner |
| TC-02 | AC-01 | IT `HostAttendanceIT` | OPEN game hosted by H; PENDING guest G, `available_since` = T0 | H: `PATCH {"state":"READY"}` | 200, `no-store`, body `{attendanceId: G, state: READY, availableSince: T0}`; row state READY, `available_since` = T0, `matches_played` unchanged | §11, OD-07 owner |
| TC-03 | AC-02 | IT | PENDING guest G | H: `{"state":"LEFT"}` | 200 state LEFT; row LEFT, `available_since` unchanged | OD-15 owner |
| TC-04 | AC-02 | IT | READY member M | H: `{"state":"LEFT"}` | 200 state LEFT; row LEFT | §11 |
| TC-05 | AC-02 | IT | READY member M | H: `{"state":"READY"}` | 200 unchanged body; row unchanged (state, `available_since`) | design D-2 |
| TC-06 | AC-03 | IT | ASSIGNED player (PLANNED match in test data) and PLAYING player (ACTIVE match) | H: `LEFT`, then `READY`, for each | 409 `STATE_CONFLICT`, message "Cancel or finish this player's match first."; rows, match and `match_player` row counts unchanged | §11 |
| TC-07 | AC-03 | IT | LEFT player; RESTING player (test data) | H: `READY` on LEFT; `READY` and `LEFT` on RESTING; `READY` on a LEFT row twice | 409 `STATE_CONFLICT`, rows unchanged; `LEFT` on LEFT → 200 no change | owner (OD-24, no Rest) |
| TC-08 | AC-03 | IT | CLOSED game with a PENDING guest | H: `READY` | 409 `STATE_CONFLICT` "This game is closed."; row unchanged | §11 |
| TC-09 | AC-04 | IT | PENDING guest | H sends body: `{}`; `{"state":null}`; `{"state":"RESTING"}`; `{"state":"PLAYING"}`; `{"state":"PENDING"}`; `{"state":"FOO"}`; `not json`; `{"state":"READY","extra":1}` | first seven → 400 `VALIDATION_FAILED`, row unchanged; last → 200 (unknown field ignored) | §13, design §3.1 |
| TC-10 | AC-04 | IT | game A and game B both hosted by H; attendance X in B | H: PATCH `/host/games/A/attendance/X` | 404 `NOT_FOUND`; X unchanged | design §8 |
| TC-11 | AC-05 | unit `IdentityTokenTest` | fixed `Clock`; token issued for (playmatch-service, GET, `/api/v1/host/games/g/board`) | verify with: same triple; other method; other path; other audience; v1-format token with a valid signature; `exp` + 1 s | only the same triple within 30 s passes; every other case empty | design §3.2 |
| TC-12 | AC-05 | unit `CallerIdentityArgumentResolverTest` | resolver with service name `playmatch-service` | request `PATCH /a/b` with token for `PATCH /a/b` / for `GET /a/b` / for `player-service` | Account / 401 / 401 | design §3.2 |
| TC-13 | AC-05 | IT `HostAttendanceIT` | valid v2 token issued for `GET /api/v1/host/games/{A}/board` | sent on `PATCH /api/v1/host/games/{A}/attendance/{G}` | 401 `UNAUTHENTICATED`; row unchanged | design §3.2, Host board review #17 |
| TC-14 | AC-01 | IT | PENDING guest G | two `READY` PATCHes from H in parallel threads | both 200; one row READY; `available_since` = T0 | design D-2, §4 lock |
| TC-15 | AC-01 | IT (`JsonAllowlist`, host set) | TC-02 answer | read keys | exactly `attendanceId`, `state`, `availableSince` | design §3.1 |
| TC-16 | AC-07 | IT | guest G and member M marked LEFT by H | G reads own status (`/internal/v1/games/{id}/my-attendance?attendanceId=G`); M joins again (`/internal/v1/games/{id}/member-attendances`) | G: `LEFT`; M: 409 "You were removed from this game." | existing (My attendance D-4), design §5 |
| TC-17 | AC-04 | IT `PlaymatchServiceAccessIT` rows | PATCH route | no token; account not host; host of the game; unknown game; malformed game id; malformed attendance id | 401; 403; 200; 404; 404; 404 — row unchanged except the host row | §3, §13, §15 |
| TC-18 | AC-04 | gateway `GatewayAccessMatrixIT` + `HostRoutesIT` (stub playmatch) | `PATCH /api/v1/host/games/g/attendance/a` | anonymous; guest cookie only; account without `X-CSRF-TOKEN`; account with CSRF; browser-sent `X-Internal-Identity` | 401; 401; 403 `CSRF_TOKEN_INVALID`; forwarded once with one v2 token whose `aud` = playmatch-service, method PATCH, path = request path; browser header removed; existing board / games / my-attendance routes still forwarded with v2 | AD-14, design §3 |
| TC-19 | AC-06 | visual (owner) | seeded game with 1 pending guest, ready members, a closed game | open board | Pending row: Approve + Reject; Ready row: Mark as left; others none; Reject / Mark as left open dialog, Keep closes it with no request; while saving every action button disabled with "Saving…"; after success the board re-reads; forced 409 (change in another tab) shows "This player's status changed. The board was refreshed."; closed game shows "This game is closed. The board is read-only." and no buttons | design §7, §16 |
| TC-20 | AC-06 | visual (owner) | board at 320 px and 1280 px | keyboard only | tab reaches each button; Enter opens dialog; focus inside dialog; Esc cancels and focus returns to the button; each button ≥ 44 px high | §16, conventions |
| TC-21 | AC-01 | live (Docker, through the gateway) | stack running, seed data | guest checks in from the public page; host signs in, approves on the board; `player1` sends the PATCH with a CSRF token; anonymous `curl` PATCH | guest appears under Ready; guest's My game shows Ready; `player1` → 403; anonymous → 401 (or 403 CSRF first — record the real answer) | §0 step 3 |
| TC-22 | — | IT `SchemaBoundaryTest` (existing) | — | run | still passes | AD-12 |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/libs/common-web/src/main/java/com/faircourt/common/identity/IdentityToken.java` | changed | v2 claims (T01) |
| `backend/libs/common-web/src/main/java/com/faircourt/common/identity/CallerIdentityArgumentResolver.java` | changed | audience + request line (T01) |
| `backend/libs/common-web/src/main/java/com/faircourt/common/web/CommonWebAutoConfiguration.java` | changed | pass `spring.application.name` (T01) |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/routing/GatewayRoute.java`, `GatewayRoutes.java`, `GatewayRouteTable.java`, `IdentityTokenFilter.java` | changed | audience per route, v2 issue, new route (T02, T07) |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/AttendanceState.java` | changed | transition table (T04) |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/attendance/{AttendanceService,HostAttendanceController,ChangeAttendanceRequest,HostAttendanceResponse}.java` | added | the write (T05) |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/security/SecurityConfig.java` | changed | open PATCH route (T05) |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/{HostAttendanceIT,attendance/AttendanceStateTest}.java` | added | T04, T06 |
| existing tests listed in T03, `PlaymatchServiceAccessIT`, `GatewayAccessMatrixIT`, `HostRoutesIT` | changed | v2 tokens + new rows |
| `frontend/src/api/{client,host,types}.ts`, `frontend/src/copy/en.ts` | changed | T08 |
| `frontend/src/components/ConfirmDialog.tsx`, `frontend/src/features/host-board/AttendanceActions.tsx` | added | T09 |
| `frontend/src/features/host-board/{PlayerList,HostBoardPage}.tsx` | changed | T09 |
| `API Document/` (host routes file) | changed | T10 |

Deleted files: none (deletions need explicit owner consent). No migration, no new dependency, no new config entry.

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-26 | Windows / WSL | `docker compose run --rm --build backend-format` (Spotless apply) | PASS (ran twice, second run before the final verify; files reformatted) |
| 2026-11-26 | Windows / WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl libs/common-web,services/gateway,services/playmatch-service -am verify` (first run) | FAIL: test compile error in `CallerIdentityArgumentResolverTest` (ambiguous overload `resolve`); renamed helper |
| 2026-11-26 | Windows / WSL | same `mvn ... verify` command (second run, after format) | PASS: BUILD SUCCESS; common-web 28 unit + 8 IT, matching 13, gateway 13 unit + 95 IT (108), playmatch 6 unit + 79 IT (85 total incl. `HostAttendanceIT` 11, `AttendanceStateTest` 3, `SchemaBoundaryTest`); 0 failures, 0 skipped |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend npm run format` (Prettier write) | PASS (files formatted) |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm run build"` | PASS (format check clean; build OK incl. type check, `built in 1.02s`) |
| 2026-11-26 | Windows / WSL | `bash scripts/precommit.sh` (full pipeline: spotless apply, prettier, `mvn -B clean verify` all modules, frontend format:check + build, staged-secret check) | PASS (`PIPELINE OK`) |
| 2026-11-26 | Windows / WSL | `docker compose up -d --build gateway playmatch-service` | PASS (both healthy) |
| 2026-11-26 | Windows / WSL | live script `live-f07.sh` (session scratchpad; `curl` through the gateway on 9090) | PASS: host login 200; guest check-in 201 `PENDING`; guest `/me` `PENDING`; `player1` PATCH → 403 `FORBIDDEN`; anonymous PATCH without CSRF → 403 `CSRF_TOKEN_INVALID`, with CSRF → 401 `UNAUTHENTICATED`; host approve → 200 `READY` (body only `attendanceId`, `state`, `availableSince`); guest `/me` → `READY`; board lists the guest as `READY`; host mark left → 200 `LEFT`; host `LEFT → READY` → 409 `STATE_CONFLICT`; guest `/me` → `LEFT`; guest name in 0 log lines (last 10 min). Leaves one `LEFT` guest row "Live F07 guest" in the local demo database |
| 2026-11-26 | Windows / WSL | Fix round 1: `docker compose run --rm --build backend-format` | PASS |
| 2026-11-26 | Windows / WSL | Fix round 1: `docker compose run --rm --no-deps frontend npm run format` | PASS |
| 2026-11-26 | Windows / WSL | Fix round 1: `bash scripts/precommit.sh` (full pipeline) | PASS: `PIPELINE OK`, BUILD SUCCESS; playmatch 6 unit + 86 total tests incl. `HostAttendanceIT` 12; auth 42, player 23; 0 failures |
| 2026-11-26 | Windows / WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl services/gateway -am verify` (orchestrator, after fix round 1) | PASS: BUILD SUCCESS, gateway integration tests 109 run, 0 failures, 0 skipped (`HostRoutesIT` contains the new `aPathTooLongForTheTokenAnswers404AndNothingIsForwarded`) |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-02, TC-14, TC-15, TC-21 | `PASS` | `HostAttendanceIT` in `mvn verify` + precommit (2026-11-26); live script (approve 200, guest `/me` READY) |
| AC-02 | TC-01, TC-03…TC-05 | `PASS` | `AttendanceStateTest`, `HostAttendanceIT`; live mark left 200 |
| AC-03 | TC-01, TC-06…TC-08 | `PASS` | `HostAttendanceIT` (409 with unchanged rows); live `LEFT → READY` 409 |
| AC-04 | TC-09, TC-10, TC-17, TC-18 | `PASS` | `HostAttendanceIT`, `PlaymatchServiceAccessIT`, `GatewayAccessMatrixIT`, `HostRoutesIT`; live `player1` 403, anonymous 403 CSRF / 401 |
| AC-05 | TC-11…TC-13, TC-18 | `PASS` | `IdentityTokenTest`, `CallerIdentityArgumentResolverTest`, `HostAttendanceIT`, `HostRoutesIT`; live board / me routes still work with v2 |
| AC-06 | TC-19, TC-20 | `NOT VERIFIED` | visual checks wait for the owner; frontend build + format check PASS only |
| AC-07 | TC-16 | `PASS` | `HostAttendanceIT`; live guest `/me` → LEFT |

## Deviations from the design

- `GatewayRoute` keeps a 5-argument constructor (audience `null`) next to the new 6-argument one, so `StubService` and `GatewayAccessMatrixIT` test routes (all `PUBLIC`) compile unchanged; a forwarded `HOST` route without audience is refused at construction.
- Token path is limited to printable ASCII without quote and backslash, max 512 characters; `issue` throws `IllegalArgumentException` otherwise (claims are built without JSON escaping). Token max length raised from 1024 to 2048.
- TC-09 last case (unknown JSON field `extra`) returned 200 as expected (observed in `HostAttendanceIT`). No deviation from the oracle.
- TC-21 (live check) run 2026-11-26: anonymous PATCH answers 403 `CSRF_TOKEN_INVALID` without a CSRF token and 401 with one (the plan allowed either; real answers recorded).
- Also updated `.claude/flow/maps/api-routes.md` and `index.md` (config "docs" step), not listed in Files.
- Frontend (T08, T09): `apiPost` and `apiPatch` share one private `apiWrite` (same CSRF + retry-once). `changeAttendance` takes one object argument `{gameId, attendanceId, state}` because `useMutation` passes a single argument. The mutation lives in `HostBoardPage` (not in `Board`), because a board reload unmounts `Board`. `Saving…` is shown only on the buttons of the row being saved; all action buttons on the board are disabled while saving. A failed change (not 409) shows the existing `ErrorBanner` (its Try again re-reads the board; it does not repeat the write); 401 and 403 of the write go to login and the forbidden view. `API Document/frontend.md` host-board row updated afterwards by the orchestrator.
- Order of checks in the controller: a body that cannot be parsed answers 400 before the 401 of an anonymous caller (Spring reads the body before the controller runs); same as the member join route. The gateway answers anonymous with 401 first, so a browser never sees this.
- Fix round 1 (2026-11-26): confirm dialog title id via `useId`; a write answered 403 with a code other than `FORBIDDEN` (e.g. `CSRF_TOKEN_INVALID`) shows the `ErrorBanner`, not the forbidden view; `useMutation` got `reset()`, called by Refresh / Try again so the banner clears; `AttendanceTarget` moved to `api/types.ts`; type comments restored; `leftIsFinal...` test asserts `STATE_CONFLICT`; `IdentityTokenFilter` maps an unsupported token path to 404 `NOT_FOUND` (new `HostRoutesIT.aPathTooLongForTheTokenAnswers404AndNothingIsForwarded`); `CallerIdentity` now precedes the body parameter in `HostAttendanceController`. Observed (new `HostAttendanceIT.aMissingTokenWithAnInvalidBodyAnswers400AndWithAValidBodyAnswers401`): the order change does not change behaviour: all arguments are resolved before the method body, so no token + bad body is still 400, no token + valid body is 401. Controller comment states this.
