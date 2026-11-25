# F05 implementation — Own attendance status + member join

Design: [design.md](design.md) (`APPROVED` 2026-11-20, Gate 1). Plan approved at **Gate 2** on 2026-11-20. Branch (created when implementation starts): `feature/my-attendance-status`.

## Acceptance criteria used here

README AC-01…AC-06 (draft) are confirmed as written, plus two the design added:

| AC | Criterion |
|---|---|
| AC-01 | Guest reads own `PENDING`; member reads own `READY` |
| AC-02 | Anonymous → 401; guest of another game → 404; signed-in account that has not joined → 404 |
| AC-03 | `/me` JSON keys ⊆ `{attendanceState, assignment}`; join answer keys = `{attendanceState}` |
| AC-04 | Member joins twice → same attendance, state `READY` (201 then 200); parallel double join → one row |
| AC-05 | Join a `CLOSED` game → 409 `STATE_CONFLICT`, row counts unchanged |
| AC-06 | Expired guest access → 401 `GUEST_ACCESS_EXPIRED`; page shows "Check in again and tell the host to remove your old entry." (§16); page states and layout at 320/375/1280px |
| AC-07 | The host (account without a player profile) can join as a member: profile created once, state `READY` |
| AC-08 | A member whose entry is `LEFT` joining again → 409; a service down → 503 and no attendance row |

## Tasks

Order: backend first, then the frontend (project rule). Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-07 | player-service: `MemberProfileService` (find-or-create `MEMBER` profile by `userId`, idempotent, band `null`), `MemberProfileController` for `POST /internal/v1/players/members` and `GET /internal/v1/players/by-user/{userId}`; `SecurityConfig` matchers | `DONE` |
| T02 | AC-01, 04, 05, 07, 08 | playmatch-service: `PlayerClient` (+2 calls), `MemberAttendanceService` (join orchestration, section 4 of the design), `MemberAttendanceController` for `POST /internal/v1/games/{gameId}/member-attendances`; matchers in `SecurityConfig` | `DONE` |
| T03 | AC-01, 02, 03 | playmatch-service: `MyAttendanceQuery` (read only, `JdbcClient`, caller's own row in that game) and `GET /internal/v1/games/{gameId}/my-attendance?userId|attendanceId` | `DONE` |
| T04 | AC-01…08 | gateway: `RouteAccess.PLAYER`, route-table rows `member-join` and `my-attendance`, `PlaymatchClient` (+2 calls), `PlayerAccessController` (account wins over guest cookie; `GUEST_ACCESS_EXPIRED`; member display name from the email; `no-store`; CSRF on join) | `DONE` |
| T05 | AC-01…08 | Backend tests of TC-01…TC-09 (player, playmatch, gateway), `GatewayAccessMatrixIT` and `AccessMatrixIT` rows, `JsonAllowlist` assertions, schema boundary | `DONE` |
| T06 | AC-01, 06 | Frontend API: `src/api/attendance.ts`, types `MyAttendance`/`AttendanceState`, `lib/status.ts` labels, `copy/en.ts` rows of the design copy table | `DONE` |
| T07 | AC-01, 06 | Frontend page: `features/my-game/MyGamePage.tsx` + `StateCard.tsx`, route `/my/games/:gameId`, all states, Refresh | `DONE` |
| T08 | AC-04, 06 | Frontend: Join button for a signed-in account on the public page; "See my status" link on the guest pending panel | `DONE` |
| T09 | all | Live check on the Docker stack (TC-11), pre-commit pipeline (TC-12), `review.md` via `scope-guardian` and `privacy-reviewer`, docs and evidence in this file | `DONE` |

Dependencies: T01 → T02 → T03 → T04 → T05; T06 → T07 → T08 after T04.

## Test cases

Oracle: design.md (D) section, or blueprint §. "IT" runs in `mvn verify` against `faircourt_test`; services are replaced by a JDK `HttpServer` stub in gateway tests.

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-04 | playmatch IT | OPEN game, member player P (profile exists), no attendance | `POST member-attendances` | `created=true`, state `READY`, 1 row with `display_name` = given name | D §3, §4; §11 |
| TC-02 | AC-04 | playmatch IT | same, attendance already `READY` | join again | `created=false`, state `READY`, still 1 row, `available_since` unchanged | D-3 |
| TC-03 | AC-04 | playmatch IT | same game, two parallel joins (two threads) | both call | exactly 1 row; one `created=true`, other `false` | D §4 |
| TC-04 | AC-05 | playmatch IT | `CLOSED` game | join | 409 `STATE_CONFLICT`; attendance count unchanged | §13; D §3 |
| TC-05 | AC-08 | playmatch IT | member attendance `LEFT` | join | 409 `STATE_CONFLICT`; row still `LEFT` | D-4 |
| TC-06 | AC-02 | playmatch IT | unknown `gameId` | join / my-attendance | 404 `NOT_FOUND`, no row | D §3 |
| TC-07 | AC-08 | playmatch IT (player stub down) | player-service unreachable | join | 503 `SERVICE_UNAVAILABLE`; no attendance row | D §4 |
| TC-08 | AC-07 | player IT | account X has no profile | `POST players/members` twice (also parallel) | 1 `MEMBER` profile (`user_id` = X, band null); same `playerId` both times | D §3; OD-11 |
| TC-09 | AC-07 | player IT | account Y has a profile | `GET by-user/Y` ; `GET by-user/unknown` | `{playerId}` ; 404 | D §3 |
| TC-10 | AC-01 | playmatch IT | member READY + guest PENDING in game G | `my-attendance?userId` / `?attendanceId` | `READY` / `PENDING`; nothing else in the body | §13 |
| TC-11 | AC-02 | playmatch IT | attendance of game G2; ask with game G | `my-attendance?attendanceId` | 404; account that never joined → 404; both params or none → 400 | D §3 |
| TC-12 | AC-03 | gateway IT + `JsonAllowlist` | stubs return states | `GET /games/{id}/me` as member and as guest | 200, keys ⊆ `{attendanceState, assignment}`, `assignment` null, `Cache-Control: no-store`; join keys = `{attendanceState}` | D §3 |
| TC-13 | AC-02 | `GatewayAccessMatrixIT` | no credentials | `POST /games/{id}/join`, `GET /games/{id}/me` | 401 `UNAUTHENTICATED`; join without CSRF → 403 | §3, §13 |
| TC-14 | AC-06 | gateway IT | guest cookie unknown / revoked / expired (auth stub answers 401) | `GET /games/{id}/me` | 401 `GUEST_ACCESS_EXPIRED` | D-5; §16 |
| TC-15 | AC-02 | gateway IT | guest cookie of game A, request for game B | `GET /games/B/me` | 404 `NOT_FOUND` (playmatch stub applies the game check); body gives no data of A | D §3 |
| TC-16 | AC-01 | gateway IT | both a valid account session and a guest cookie | `GET /games/{id}/me` | the account's attendance is requested (stub receives `userId`, not `attendanceId`) | README design note |
| TC-17 | AC-04, 07 | gateway IT | signed in as `host@faircourt.local` | `POST /games/{id}/join` | stub receives `userId` from the session and `displayName` = `host`; answer 201 `{attendanceState:"READY"}`; second call 200 | D-1 |
| TC-18 | all | gateway IT | request headers: `Cookie`, `X-Internal-*` | any call to playmatch | the stub never receives the browser `Cookie` header; identity-like client headers are not accepted as identity | AD-14; privacy rules |
| TC-19 | — | all services `SchemaBoundaryIT` | — | scan SQL and migrations | no mention of another service's schema | conventions |
| TC-20 | — | logs | join and `/me` executed | read service logs | no email, name or token in them | privacy rules |
| TC-21 | AC-06 | frontend (vitest not installed: not part of this feature) → visual | each state of `/my/games/:gameId`: loading, pending, ready, resting, left, in match, not joined (404), expired (401), error (503) | open at 320 / 375 / 1280px | text, status badge (text + colour), Refresh and links per design; buttons ≥ 44px; keyboard focus visible | D §7; §16 (owner looks) |
| TC-22 | AC-04 | live (Docker) | stack up, seeded game | member joins from the public page; guest checks in then opens My game; host joins | pages show `READY` / `PENDING`; DB rows counted before and after; host profile created once | D §1 |
| TC-23 | all | pipeline | — | `scripts/precommit.sh` | `PIPELINE OK` | project rule |

TC-21 is judged by the owner (no screenshot tool has been verified in this project); it stays `NOT VERIFIED` until then.

## Files (expected)

| File | Added / changed | Why |
|---|---|---|
| `backend/services/player-service/.../member/*`, `SecurityConfig` | added / changed | T01 |
| `backend/services/playmatch-service/.../attendance/*`, `guest/PlayerClient` or a shared client, `SecurityConfig` | added / changed | T02, T03 |
| `backend/services/gateway/.../routing/{RouteAccess,GatewayRouteTable}`, `player/PlayerAccessController`, `guest/PlaymatchClient` | added / changed | T04 |
| tests of the three services | added | T05 |
| `frontend/src/api/{attendance,types}.ts`, `lib/status.ts`, `copy/en.ts`, `features/my-game/*`, `features/public-game/*`, `features/join/JoinPage.tsx`, `routes/AppRoutes.tsx` | added / changed | T06–T08 |
| `planning/…/my-attendance-status/{README,implement,review}.md`, board rows | changed | status and evidence |

Deleted files: none. No new dependency, no migration. `README.md` (repository root) stays empty.

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-20 | Windows PC, WSL Ubuntu, Docker 29.5.3 | `docker compose run --rm --build backend-format` (spotless:apply) | PASS (exit 0; formatting applied; run again after the last test edit) |
| 2026-11-20 | Windows PC, WSL Ubuntu | `docker compose --profile test run --rm --build backend-test` (`mvn -B clean verify`), 1st run | FAIL: 1 of 52 playmatch tests, a wrong assertion in my own new test `MemberAttendanceIT` (the read path calls player-service `by-user`, so the stub body list is not empty); test fixed to assert no profile is created; no production code changed |
| 2026-11-20 | Windows PC, WSL Ubuntu | `docker compose --profile test run --rm --build backend-test` (`mvn -B clean verify`, includes `spotless:check`), 2nd run | PASS: BUILD SUCCESS. Failsafe ITs per module: gateway 97 (new `PlayerAccessIT` 20; `GatewayAccessMatrixIT` 13, +2), auth-service 42, player-service 18 (new `MemberProfileIT` 5; `PlayerServiceAccessIT` 4, +1), playmatch-service 52 (new `MemberAttendanceIT` 15; `PlaymatchServiceAccessIT` 6, +2). Unit-test totals: common-web 14, matching 8, gateway 13, auth 8, player 8, playmatch 3 |
| 2026-11-20 | Windows PC, WSL Ubuntu | `docker compose run --rm --no-deps frontend npm run format` | PASS (exit 0; formatted the new files) |
| 2026-11-20 | Windows PC, WSL Ubuntu | `docker compose run --rm --no-deps frontend npm run format:check` | PASS ("All matched files use Prettier code style!") |
| 2026-11-20 | Windows PC, WSL Ubuntu | `bash scripts/precommit.sh` (first run, before the fix round) | `PIPELINE OK` |
| 2026-11-20 | Windows PC, WSL Ubuntu | live check by an agent: `docker compose up -d --build gateway player-service playmatch-service frontend`, then curl cases through the gateway (9090) and psql row counts, log grep | all cases as expected, no bug (TC-22, TC-20 backend side); the stack was rebuilt before the fix round, so the fix round itself was verified by tests only |
| 2026-11-20 | Windows PC | three read-only reviews: `scope-guardian`, `privacy-reviewer` (Opus) | see review.md |
| 2026-11-20 | Windows PC, WSL Ubuntu | `docker compose run --rm --no-deps frontend npm run build` (`tsc --noEmit && vite build`) | first run FAIL (my unfinished JoinPage edit), second run PASS (built, no type errors). Pages were not opened in a browser: TC-21 and TC-22 NOT VERIFIED |
| 2026-11-20 | Windows PC, WSL Ubuntu | `bash scripts/precommit.sh` after the fix round 2026-11-20 (spotless apply, prettier, `mvn -B clean verify` with the new tests, frontend format:check and build, staged-file and migration guards) | PASS: `PIPELINE OK` |
| 2026-11-20 | Windows PC, WSL Ubuntu | one `wsl` call: frontend `npm run format && npm run format:check && npm run build` after "Final fixes 2026-11-20" | PASS (chain exit 0; build "built in 1.11s", no type errors). Pages not opened in a browser: NOT VERIFIED |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | Guest reads own PENDING, member reads own READY | `PASS` | MemberAttendanceIT, PlayerAccessIT (mvn verify, PIPELINE OK 2026-11-20); live: guest cookie → PENDING, player1 and host → READY |
| AC-02 | Anonymous 401; other game's guest cookie 404; account not in the game 404 | `PASS` | GatewayAccessMatrixIT, PlayerAccessIT, AccessMatrixIT; live: anonymous 401, guest cookie on random game 404, host before joining 404 |
| AC-03 | Response keys | `PASS` | JsonAllowlist assertions (gateway, playmatch, player); live: `/me` has only attendanceState and assignment |
| AC-04 | Second join 200 same row; parallel join one row | `PASS` | MemberAttendanceIT (6 threads, 1 row), MemberProfileIT (8 threads); live: host 201 then 200, rows 14 → 14 |
| AC-05 | CLOSED game → 409, rows unchanged | `PASS` (tests only) | MemberAttendanceIT incl. close-during-join; not run live |
| AC-06 | Expired guest cookie → 401 GUEST_ACCESS_EXPIRED; page states | API `PASS`; page `NOT VERIFIED` | PlayerAccessIT, live garbage cookie → 401 GUEST_ACCESS_EXPIRED; page at 320/375/1280px not seen by anyone yet (owner) |
| AC-07 | Host joins, profile created once | `PASS` | MemberProfileIT; live: one host profile, attendance `display_name` = host |
| AC-08 | LEFT → 409; service down → 503, no row | `PASS` (tests only) | MemberAttendanceIT; not run live |

## Deviations from the design

- Backend (T01 to T05, 2026-11-20):
  - The route table row `member-join` (`POST /api/v1/games/*/join`) uses the existing access kind `ACCOUNT`, not the new `PLAYER`: join is for a signed-in account only, so the security chain itself answers 401 for everyone else (a guest cookie holder included). The new `RouteAccess.PLAYER` is used only by `my-attendance` (`GET /api/v1/games/*/me`), where the controller checks account or guest cookie. Design section 3 says `PLAYER` for "the route table", the intent (account wins, guest cookie allowed) is kept for `/me`.
  - Gateway tests: `StubService` (test helper) got the two playmatch internal routes and a `query` field in `Received`; no other test helper changed.
  - Error text of 409 on member join at the gateway is the default `STATE_CONFLICT` message (it covers both a closed game and a removed player; the page shows its own copy per code).
  - TC-19 (schema boundary) is covered by the existing `SchemaBoundaryTest` of each service (they scan all of `src/main`, so the new classes are included); no new boundary test file was needed. TC-20 (logs) has one log-capture test in `MemberAttendanceIT` and one in `PlayerAccessIT`; the live log check on the Docker stack is still T09.
  - Not done on purpose (frontend, T06 to T09): the Docker stack was not rebuilt or checked live, `scripts/precommit.sh` was not run.
  - Frontend (T06 to T08, 2026-11-20):
    - The design says the join page "already holds the public summary"; it does not (it only has the public code). `JoinPage` now loads `getPublicGame(publicCode)` once to get `gameId`; the "See my status" link shows only after it arrives. One extra GET on the join page.
    - "Back to the game" (`myGame.backToGame`) is not shown: `/my/games/:gameId` knows the game id, not the public code. The key stays in `copy/en.ts` unused. "Check in again" (401) links to the home page for the same reason.
    - 404 on the page shows `myGame.notJoined` only (no link, see above).
    - Member Join on the public page (`JoinButton.tsx`, new file, split out of `GameHero`): while the account check loads or fails, the guest link is shown; a 409 shows `publicGame.closedNote`, any other failure shows `common.errorTitle` (the design has no text for these cases; both are existing copy).
    - `StateCard` shows the badge (state label) plus a large line: the body text for PENDING and READY, the label again for the other states.
- Final fixes 2026-11-20 (owner approved "duyệt hết như đề xuất"; frontend only, no new copy, package or route):
  - A failed member join (any 409 or other error) in `JoinButton` shows `common.errorTitle`, no longer "This game is closed." (a closed game is shown by the public page's own closed state, where the Join button is disabled). This replaces the 409 line in the T06 to T08 entry above. The guest join page still shows `publicGame.closedNote` on 409 (unchanged).
  - Public code reaches My game: `JoinButton` (after member join) and the "See my status" link on `JoinPage` open `/my/games/<gameId>?code=<publicCode>`. `MyGamePage` reads only `code` (accepted if it matches `[A-Za-z0-9]{1,20}`, other params ignored): 404/empty shows "Back to the game" (`myGame.backToGame`) linking to `/g/<code>`; 401 "Check in again" links to `/g/<code>/join`. Without a valid code: as before (no back link, `/`). This replaces the "Back to the game is not shown" and "links to the home page" lines above.
- Fix round 2026-11-20 (three review findings; no new copy, no new route, no migration):
  - Member name: the attendance `display_name` is the member profile's own `display_name`; the email-derived name is used only when the profile is created by this call (D-1). Internal route change inside the already approved `POST /internal/v1/players/members`: the answer gets one extra field `displayName` (`{playerId, displayName}`, new record `MemberProfileNamedResponse`). `GET /internal/v1/players/by-user/{userId}` still answers `{playerId}` only. playmatch-service stores the returned name.
  - Email-derived name: control characters removed, trimmed, cut by code points at 80. A cut at 80 code points can be up to 160 UTF-16 units, so the name check in `MemberProfileService.cleanName` (player-service) and `MemberAttendanceService.cleanName` (playmatch-service) now counts code points instead of `length()` (guest check-in code untouched). Known limit: an email whose part before `@` is empty or only control characters gives an empty name; there is no fallback copy, so playmatch-service answers 400 `VALIDATION_FAILED` as before.
  - Race with closing a game: the insert transaction re-reads the game row with `SELECT state ... FOR SHARE` first; a `CLOSED` game gives 409 `STATE_CONFLICT` and rolls back (no row); a concurrent close waits until the insert commits. The remote call stays outside the transaction.
  - Tests added: `MemberProfileIT` (existing profile keeps its name; 80 surrogate pairs accepted, 81 refused), `MemberAttendanceIT` (existing profile name kept in the row; 80 surrogate pairs stored whole, 81 refused; game closed between the first check and the insert gives 409 and no row, simulated by the player stub closing the game inside its answer, so no hook in production code), `PlayerAccessIT` (name cut at a surrogate-pair boundary; empty-name limit; account B's session never receives account A's row, the stub sees B's userId), `GatewayAccessMatrixIT` (`/internal/v1/games/{id}/my-attendance` and `/member-attendances` sent to the gateway are 401 and never forwarded). Test helper `StubService`: emails starting `other-` sign in as a second user id.
