# F04 implementation — Guest check-in + guest cookie

Design: [design.md](design.md) (`APPROVED` 2026-11-18, D-1…D-10 as recommended). Plan and test cases below follow the design; Gate 2 was given by the owner's instruction "full feature build và test" (see README decision log).

Branch: `feature/guest-check-in`, `origin/main` (c1157dd) merged in 2026-11-18 before any code.

## Tasks

Order: leaf services first (player, auth), then the orchestrator (playmatch), then the gateway, then the frontend. Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-03, AC-05 | player-service: `GuestAnswers` (value object, validation), `AnswerBand` mapping, `GuestProfileService` (one transaction: `player_profile` GUEST + `skill_assessment` SELF), `GuestProfileController` `POST /internal/v1/players/guests`, security rule | `DONE` |
| T02 | AC-02 | auth-service: `GuestCredentialService` (32 bytes `SecureRandom`, SHA-256 hex, revoke older active credential of the attendance, resolve by hash in constant time), `GuestCredentialController` `POST /internal/v1/auth/guest-credentials` and `/resolve`, security rule | `DONE` |
| T03 | AC-01, AC-04, AC-06, AC-09 | playmatch-service: `GuestCheckInController` `POST /internal/v1/games/by-code/{publicCode}/guest-check-ins`, `GuestCheckInService` (saga of design section 4 with compensation), `PlayerClient`, `AuthClient` (`RestClient`, 2 s / 5 s, no retry of writes), `faircourt.services.player-url` / `auth-url`, security rule | `DONE` |
| T04 | AC-01, AC-04, AC-05, AC-07, AC-10 | gateway: `GuestController` + route line (PUBLIC, POST `/api/v1/public/games/*/guests`), `GuestCookie` (`GUEST_COOKIE_NAME`, flags, Max-Age), `PlaymatchClient`, `AuthClient.resolveGuest`, 4 KB body cap for the route, `no-store`, logout stops clearing the guest cookie (`Clear-Site-Data: "cache"`, expire only the account cookie) | `DONE` |
| T05 | — | Config: `GUEST_COOKIE_NAME` (+ `__Host-fc_guest` note) in `.env.example` and `compose.yaml`; `PLAYER_URL` / `AUTH_URL` for playmatch in `compose.yaml` | `DONE` |
| T06 | AC-08 | Frontend: `api/guests.ts`, `features/join/JoinPage.tsx` + `QuestionStep.tsx`, route `/g/:publicCode/join`, `GameHero` Join button becomes a link for open games, strings in `copy/en.ts` (only the approved table), `api/types.ts` | `DONE` (AC-08 owner visual check pending) |
| T07 | AC-01…AC-10 | Tests of this plan (TC-01…TC-14) incl. rows in `GatewayAccessMatrixIT` and each service's `AccessMatrixIT`-style class, `JsonAllowlist`/exact-body assertion, 409 row-count test | `DONE` (backend tests; frontend tests are T06) |
| T08 | — | Docs: `API Document/` (public route + error codes, internal routes), `.claude/flow/maps/*.md`, `implement.md` evidence, `review.md` | `DONE` (review.md is written in the Gate 3 step) |
| T09 | AC-01…AC-10 | Validators: fast per module, then `scripts/precommit.sh`; live check on the Docker stack (TC-12) | `DONE` |

## Test cases

At least one per AC, negative and edge cases included. Oracle = blueprint § or design section unless noted.

| TC | Covers | Layer | Given | When | Then (status, body, rows, message) | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-03 | unit `GuestAnswersTest` | answers (0,0,0,0), (1,1,1,0), (1,1,1,1), (2,2,2,0), (2,2,2,1), (3,3,3,3) | band mapped | totals 0→1, 3→1, 4→2, 6→2, 7→3, 9→3, 10→4, 12→4: (0,0,0,0)=1, (1,1,1,0)=1, (1,1,1,1)=2, (2,2,2,0)=2, (2,2,2,1)=3, (3,3,3,3)=4 | §13 band mapping |
| TC-02 | AC-03 | unit | all `null`; `answers` `null`; three answers + one `null`; a value 4; a value -1 | validate | all `null` / `null` → band `null`; partial or out of range → refused | §13 "all four scored or all unknown" |
| TC-03 | AC-01, AC-03 | player IT `GuestProfileIT` | valid answers | `POST /internal/v1/players/guests` | 201-style answer `{playerId}`; one `player_profile` (GUEST, `user_id` null, `band_status` SELF_REPORTED, `current_band` = mapped) + one `skill_assessment` (SELF, answers JSON, band); partial answers → 400 and row counts unchanged | design §4, §5 |
| TC-04 | AC-02 | auth IT `GuestCredentialIT` | game + attendance ids | issue | answer has the raw 43-char token; `guest_access.token_hash` = 64-hex SHA-256 of it; raw token found nowhere in the table; second issue for the same attendance revokes the first (one active) | §12 |
| TC-05 | AC-02 | auth IT | issued, unknown, expired, revoked tokens | resolve | 200 `{gameId, attendanceId}` for the valid one; 401 with the identical body for unknown, expired and revoked | design §3, §8 |
| TC-06 | AC-02 | log check (IT with log capture) | issue + resolve run | read captured logs | the raw token, display name and answers appear in no log line | design §8 |
| TC-07 | AC-01, AC-06 | playmatch IT `GuestCheckInIT` (stubs for player and auth) | open game | check-in | `PENDING` attendance (`display_name` copied, `player_id` from player stub); `created=true`; credential returned; unknown code → 404; `CLOSED` game → 409 `STATE_CONFLICT` and attendance count unchanged | §13, design §4 |
| TC-08 | AC-09 | playmatch IT | player stub down / auth stub down | check-in | player down → 503 and zero new rows; auth down → attendance row set to `LEFT` and 503 (compensation); no credential returned | design §4 failure table |
| TC-09 | AC-04 | playmatch IT | `existingAttendanceId` of this game (not `LEFT`) / of another game / `LEFT` / unknown | check-in | live own → `created=false`, no credential, no new rows; the others → treated as a new check-in | §12 |
| TC-10 | AC-01, AC-04, AC-05, AC-07, AC-10 | gateway IT `GuestCheckInIT` (HttpServer stubs) | CSRF token + valid body | `POST /api/v1/public/games/{code}/guests` | 201 body exactly `{"attendanceState":"PENDING","message":"Waiting for host approval"}`; `Set-Cookie` `fc_guest` HttpOnly, SameSite=Lax, `Secure` follows `COOKIE_SECURE`, Max-Age = credential expiry, Path=/; `Cache-Control: no-store`; no id in body | §12, §13 |
| TC-11 | AC-04, AC-05, AC-07 | gateway IT | same browser | (a) valid cookie again; (b) expired/unknown cookie; (c) missing CSRF; (d) form-encoded post; (e) body > 4 KB; (f) name 81 chars / blank / control character / 80 chars / padded with spaces; (g) two guests with the same name; (h) unknown code, closed game, downstream down | (a) 200, same state, no `Set-Cookie`, playmatch told the existing attendance id; (b) new check-in 201 + new cookie; (c) 403 `CSRF_TOKEN_INVALID`; (d) refused; (e) refused; (f) 400 `VALIDATION_FAILED` / trimmed accepted; (g) both 201; (h) 404 / 409 / 503 | design §3, §9 |
| TC-12 | AC-10 | gateway IT | account session + guest cookie in one browser | `POST /api/v1/auth/logout` | account session ended; response expires only the account cookie and sends `Clear-Site-Data: "cache"` (no `"cookies"`); guest cookie not cleared | design D-10 |
| TC-13 | all | access matrix | route table | request the new routes as anonymous / account / guest; internal routes via the gateway path | public route reachable by every caller kind (POST with CSRF); `/internal/**` never routed (401); each service's internal routes require no public mapping and are not reachable except by its own tests; non-host response matches the exact-body allowlist | AD-09, AD-14 |
| TC-14 | AC-08 | visual (owner) | 320 / 375 / 1280 px | open `/g/<code>/join` | name step, four question steps (cards ≥ 56 px, progress label, Back/Next), "I'm not sure about my level" skips to send, in-place validation, pending panel, error and closed states, keyboard and focus | design §7 — `NOT VERIFIED` until the owner looks |
| TC-15 | all | live check on the Docker stack | stack running | real check-in via the frontend proxy with `curl` (CSRF + cookie jar); repeat; read row counts in the three schemas; grep container logs | 201 then 200; one profile, one assessment, one attendance, one `guest_access` (hash only); no raw token in logs; cookie flags as in TC-10 | design §9 TC-10 |
| TC-16 | — | pipeline | — | `scripts/precommit.sh` | `PIPELINE OK` (spotless, `mvn clean verify`, prettier, build, `.env` / `.idea` not staged) | CLAUDE.md 2026-11-16 |

Schema boundary tests (`SchemaBoundaryTest`) already exist per service; they must stay green with the new SQL.

## Files

Planned (the final list is filled in at the end of the build).

| File | Added / changed | Why |
|---|---|---|
| `services/player-service/.../player/guest/*` | added | T01 |
| `services/auth-service/.../auth/guest/*` | added | T02 |
| `services/playmatch-service/.../playmatch/guest/*` | added | T03 |
| `services/gateway/.../gateway/guest/*`, `routing/GatewayRouteTable`, `account/AccountController`, `account/RequestBodyLimitFilter` | added / changed | T04 |
| each service `security/SecurityConfig` and `application.yaml` | changed | internal route rules, URLs |
| `compose.yaml`, `.env.example` | changed | T05 |
| `frontend/src/features/join/*`, `api/guests.ts`, `api/types.ts`, `routes/AppRoutes.tsx`, `copy/en.ts`, `features/public-game/GameHero.tsx` | added / changed | T06 |
| tests listed in the test cases | added | T07 |
| `API Document/*`, `.claude/flow/maps/*` | changed | T08 |

Deleted files: none (deletions need explicit owner consent). No new Flyway migration (the tables exist). No new dependency.

Backend files actually written (T01-T05, T07, backend part of T08; the frontend files of T06 are listed by the frontend task):

| File | Added / changed | Task |
|---|---|---|
| `backend/services/player-service/src/main/java/com/faircourt/player/guest/{AnswerBand,GuestAnswers,CreateGuestProfileRequest,GuestProfileResponse,GuestProfileService,GuestProfileController}.java` | added | T01 |
| `backend/services/player-service/.../player/security/SecurityConfig.java` | changed | T01 |
| `backend/services/auth-service/src/main/java/com/faircourt/auth/guest/{GuestCredentialService,GuestCredentialController}.java` | added | T02 |
| `backend/services/auth-service/.../auth/security/SecurityConfig.java` | changed | T02 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/guest/{GuestCheckInController,GuestCheckInService,GuestCheckInRequest,GuestCheckInResponse,GuestCredential,PlayerClient,AuthClient,ServiceClients}.java` | added | T03 |
| `backend/services/playmatch-service/.../playmatch/attendance/AttendanceState.java` | added (state enum per conventions) | T03 |
| `backend/services/playmatch-service/.../playmatch/security/SecurityConfig.java`, `src/main/resources/application.yaml` | changed (route rule; `faircourt.services.*` URLs and timeouts, `faircourt.guest.cookie-grace`) | T03 |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/guest/{GuestController,GuestCookie,PlaymatchClient,GuestCheckInRequest,GuestCheckInResponse}.java` | added | T04 |
| `backend/services/gateway/.../account/{AccountController,AuthClient,RequestBodyLimitFilter}.java`, `routing/GatewayRouteTable.java`, `security/GatewaySecurityConfig.java`, `src/main/resources/application.yaml` | changed | T04 |
| `compose.yaml`, `.env.example` | changed (`GUEST_COOKIE_NAME`, `PLAYER_URL`, `AUTH_URL`) | T05 |
| `backend/services/player-service/src/test/.../player/guest/{GuestAnswersTest,GuestProfileIT}.java`, `PlayerServiceAccessIT.java` (changed) | added / changed | T07 |
| `backend/services/auth-service/src/test/.../auth/GuestCredentialIT.java`, `AuthServiceAccessIT.java` (changed) | added / changed | T07 |
| `backend/services/playmatch-service/src/test/.../playmatch/GuestCheckInIT.java`, `PlaymatchServiceAccessIT.java` (changed) | added / changed | T07 |
| `backend/services/gateway/src/test/.../gateway/{GuestTestBase,GuestCheckInIT}.java` | added | T07 |
| `backend/services/gateway/src/test/.../gateway/{StubService,GatewayAccessMatrixIT,AccountLoginIT}.java` | changed (stub routes for resolve and check-in; matrix row; logout test now expects `"cache"` only) | T07 |
| `API Document/backend.md`, `.claude/flow/maps/{api-routes,index}.md` | changed | T08 |

Fix round 1 (2026-11-18) also changed: `frontend/src/features/join/{JoinPage,QuestionStep}.tsx`; gateway `guest/{AttendanceState (added),GuestCheckInResponse,PlaymatchClient,GuestController}.java`; playmatch `guest/{GuestCheckInResponse,GuestCheckInService}.java`; auth `guest/GuestCredentialService.java`; player `guest/GuestProfileService.java`; tests gateway `{GuestCheckInIT,StubService}.java` and `GuestSecureCookieIT.java` (added), playmatch `GuestCheckInIT.java`, player `GuestProfileIT.java`.

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-18 | Windows PC + WSL | `git fetch origin --prune` (via WSL SSH), `git switch main`, `git merge --ff-only origin/main` | OK: main at c1157dd |
| 2026-11-18 | Windows PC + WSL | `git switch feature/guest-check-in`, `git merge --no-edit origin/main` | OK, no conflicts, merge commit 28c7446 |
| 2026-11-18 | Windows PC + WSL | `docker compose run --rm --no-deps frontend npm run format` (Prettier write; reformatted `AppRoutes.tsx` after a typo fix) | PASS |
| 2026-11-18 | Windows PC + WSL | `docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm run build"` | PASS (format check clean; `vite build` built in 937 ms) |
| 2026-11-18 | Windows PC + WSL | `docker compose run --rm --build backend-format` (Spotless apply, run twice: before the first and the last module run) | PASS (exit 0) |
| 2026-11-18 | Windows PC + WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl libs/common-web,services/player-service,services/auth-service -am verify` | PASS: common-web 14 unit + 8 IT; auth-service 8 unit + 42 IT (incl. `GuestCredentialIT` 7); player-service 8 unit (incl. `GuestAnswersTest` 6) + 11 IT (incl. `GuestProfileIT` 4) |
| 2026-11-18 | Windows PC + WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl services/playmatch-service,services/gateway -am verify` (first run) | FAIL: playmatch `PlaymatchServiceApplicationTests` could not start, no `RestClient.Builder` bean (the service has no rest-client starter; no new dependency allowed) |
| 2026-11-18 | Windows PC + WSL | fix: new `ServiceClients` builds the `RestClient` with a JDK request factory and the 2 s / 5 s timeouts; `docker compose --profile test run --rm --build backend-test mvn -B -pl services/playmatch-service -am verify` | PASS: playmatch-service 3 unit + 32 IT (incl. `GuestCheckInIT` 13); gateway had passed in the first run (13 unit + 73 IT, incl. `GuestCheckInIT` 19, `GatewayAccessMatrixIT` 11, `AccountLoginIT` 26) |
| 2026-11-18 | Windows PC + WSL | `docker compose --profile test run --rm --build backend-test mvn -B clean verify` (whole backend, includes `spotless:check`) | PASS: BUILD SUCCESS, all 7 modules (IT counts: gateway 73, auth-service 42, player-service 11, playmatch-service 32, common-web 8; unit: common-web 14, matching 13, gateway 13, auth 8, player 8, playmatch 3) |
| 2026-11-18 | Windows PC + WSL | Fix round 1: `docker compose run --rm --build backend-format`; `docker compose run --rm --no-deps frontend npm run format` | PASS (exit 0 both) |
| 2026-11-18 | Windows PC + WSL | Fix round 1: `docker compose --profile test run --rm --build backend-test mvn -B clean verify` (whole backend) | PASS: BUILD SUCCESS; gateway 75 IT (`GuestCheckInIT` 20, new `GuestSecureCookieIT` 1), auth 8 unit + 42 IT, player 8 unit + 12 IT (`GuestProfileIT` 5), playmatch 3 unit + 35 IT (`GuestCheckInIT` 16) |
| 2026-11-18 | Windows PC + WSL | Fix round 1: `docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm run build"` | PASS (vite build built in 2.45 s) |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-07, TC-10, TC-15 | `PASS` | TC-07, TC-10 (playmatch `GuestCheckInIT` 16, gateway `GuestCheckInIT` 20, `GuestSecureCookieIT` 1) and live check TC-15 on the rebuilt Docker stack: first POST 201, body exactly `{"attendanceState":"PENDING","message":"Waiting for host approval"}`, `Cache-Control: no-store`, cookie `fc_guest` HttpOnly, SameSite=Lax, Max-Age 55020, no `Secure` (COOKIE_SECURE=false locally); rows 8→9 attendance, 8→9 profile, 0→1 guest_access; stored name trimmed, GUEST, band 2, SELF_REPORTED |
| AC-02 | TC-04, TC-05, TC-06, TC-15 | `PASS` | TC-04, TC-05, TC-06 (auth `GuestCredentialIT` 7) and live check TC-15: `token_hash` is 64 lowercase hex, the raw 43-char token is not in the DB column, the raw token and the typed name appear in 0 container log lines |
| AC-03 | TC-01, TC-02, TC-03 | `PASS` | `GuestAnswersTest` 6 tests, player `GuestProfileIT` 4 tests (mvn verify player-service, 2026-11-18) |
| AC-04 | TC-09, TC-11 | `PASS` | playmatch `GuestCheckInIT` (existing attendance returned without writes; other game, LEFT and unknown ignored) and gateway `GuestCheckInIT` (200, no new cookie, existing id passed on; unknown cookie ignored) |
| AC-05 | TC-11 | `PASS` | gateway and playmatch `GuestCheckInIT` (trim, 1-80, control characters, partial and out-of-range answers, same name twice) |
| AC-06 | TC-07, TC-11 | `PASS` | playmatch `GuestCheckInIT` (404, 409 with attendance count unchanged) and gateway `GuestCheckInIT` (404, 409, 503 mapping, no cookie) |
| AC-07 | TC-11 | `PASS` | gateway `GuestCheckInIT` and `GatewayAccessMatrixIT` (CSRF 403, form post refused, 4 KB cap, POST only, cookie flags, no-store, exact two-field body) |
| AC-08 | TC-14 | `NOT VERIFIED` | frontend built, format and build PASS 2026-11-18; owner has not looked at 320 / 375 / 1280 px (TC-14) |
| AC-09 | TC-08 | `PASS` | playmatch `GuestCheckInIT` (player down: 503 and zero rows; auth failure: attendance set to LEFT and 503, no credential) |
| AC-10 | TC-12 | `PASS` | gateway `GuestCheckInIT.logoutEndsTheAccountSessionButKeepsTheGuestCookie` and `AccountLoginIT` (Clear-Site-Data "cache" only, only fc_sid expired, guest cookie still recognised) |

## Orchestrator checks (2026-11-18, Windows PC + WSL)

| Command | Result |
|---|---|
| `FLOW_BASE=main bash scripts/precommit.sh` | PASS: `PIPELINE OK` (backend format, frontend prettier, `mvn -B clean verify`, frontend format:check + build, no .env/.idea staged, no applied migration changed) |
| `docker compose up -d --build gateway auth-service player-service playmatch-service`, then gateway `/actuator/health` | 200 |
| TC-15 live script (curl with cookie jar and CSRF token against `localhost:9090`): first check-in, same-browser repeat, row counts in 3 schemas, token and name grep in all container logs, no CSRF, unknown code, partial answers | 201, then 200 with no new cookie and no new rows (9/9/1); no CSRF 403; unknown code 404 `NOT_FOUND`; partial answers 400 `VALIDATION_FAILED` |

After fix round 1 (final code): `scripts/precommit.sh` again `PIPELINE OK` (backend 75 gateway IT, 42 auth IT, 12 player IT, 35 playmatch IT; frontend format:check + build); stack rebuilt and TC-15 repeated: a first request sent while playmatch/player were still starting answered 503 and wrote no rows; the next one 201 + cookie (rows 9→10), and the same browser again 200 with no new cookie and no new rows (10/10/2). Token and typed name found in 0 container log lines.

Left in the local database by the live check: two guests "LiveCheck Mai" and "LiveCheck Lan" (attendance, profile, assessment, guest_access) in the demo game. Not deleted, because deletion needs the owner's consent.
Not checked live: `Secure` / `__Host-fc_guest` over HTTPS, a real browser.

## Deviations from the design

- `playmatch-service` has no auto-configured `RestClient.Builder` (the rest-client starter is not a dependency and no new dependency is allowed), so `ServiceClients` builds its `RestClient` with a JDK request factory and the approved 2 s / 5 s timeouts, read from `faircourt.services.connect-timeout` / `read-timeout`. The gateway keeps using `spring.http.clients` through the Cloud Gateway starter.
- The design lists `AuthClient.resolveGuest` in `gateway.guest`; it was added to the existing `gateway.account.AuthClient` (a second class with the same name would clash as a Spring bean). Same internal route, same behavior.
- The gateway test `AccountLoginIT` (logout) was edited because its expectation (`Clear-Site-Data` contains "cookies") is exactly what design D-10 changes.
- Small additions inside the approved design: `AttendanceState` enum (conventions: state enums), `Cache-Control: no-store` on the internal answers that can carry the token, a 400 from player-service for a bad name as a second line of defence (the same rules run in gateway, playmatch and player).
- A 400 from `resolve` (a value that cannot be a token) is treated by the gateway as "no cookie", same as 401.
