# F06 implementation — Host board

Design: [design.md](design.md) — `APPROVED` 2026-11-25 (Gate 1). Plan and test cases approved 2026-11-25 (Gate 2, owner: "duyệt plan").

## Before the first task

- Fetch `main` from GitHub (WSL SSH), fast-forward local `main`, branch `feature/host-board` from it.
- **Owner action on both machines (Mac, Windows/WSL):** add one line to the local `.env`: `INTERNAL_TOKEN_SECRET=<random, at least 32 characters>` (e.g. the output of `openssl rand -base64 48`). Without it the stack refuses to start after this feature (T02). Claude adds the documented line to `.env.example` but never writes the real `.env`. (Done on the Windows PC on 2026-11-25 by Claude with the owner's approval. Since 2026-11-26 `.env.example` carries a working local-only value, so a fresh `cp .env.example .env` also starts; an existing `.env` without the line still needs it.)

## Tasks

Order: backend first (T01–T06), then frontend (T07–T08), then docs and checks (T09). Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-02 | `common-web` package `com.faircourt.common.identity`: `CallerIdentity` (sealed: `Anonymous`, `Account(UUID userId)`), `IdentityToken` (issue + verify, HMAC-SHA256 via `javax.crypto.Mac`, constant-time compare, `Clock`, 30 s life, version `v1`, secret ≥ 32 bytes else startup error), `IdentityTokenHeader` constant `X-Internal-Identity`, `CallerIdentityArgumentResolver` (missing header → `Anonymous`; any invalid token → `ApiException(UNAUTHENTICATED)`), registration in `CommonWebAutoConfiguration` only when `faircourt.internal-token.secret` is set. Unit tests TC-01…TC-06 | `DONE` |
| T02 | AC-02 | Configuration: `faircourt.internal-token.secret: "${INTERNAL_TOKEN_SECRET}"` in gateway and playmatch `application.yaml`; `compose.yaml` passes `INTERNAL_TOKEN_SECRET: ${INTERNAL_TOKEN_SECRET:?…}` to gateway and playmatch-service (fails fast with a message when missing); `.env.example` documents it; `backend/pom.xml` surefire + failsafe set a fixed **test-only** value so `mvn verify` runs anywhere (backend-test container, IntelliJ, later CI) | `DONE` |
| T03 | AC-02 | Gateway: `RouteAccess.HOST` (security rule `.authenticated()`); routes `host-board` `GET /api/v1/host/games/*/board` and `host-games` `GET /api/v1/host/games`, both → playmatch; `IdentityTokenFilter` (before-filter, runs after `HeaderSanitizer`) adds `X-Internal-Identity` for the signed-in account on `HOST` routes only. `GatewayAccessMatrixIT` rows + `HostRoutesIT` (TC-07…TC-11) | `DONE` |
| T04 | AC-06 | player-service package `com.faircourt.player.level`: `PlayerLevelsRequest` (1–200 UUIDs), `PlayerLevelsQuery` (`JdbcClient`, `id = ANY(:ids)`), `PlayerLevelsController` `POST /internal/v1/players/levels`, `PlayerLevelsResponse`; security rule (POST only). `PlayerLevelsIT`, `PlayerServiceAccessIT` row (TC-12…TC-14) | `DONE` |
| T05 | AC-01, AC-02, AC-03, AC-06 | playmatch: `access/AccessPolicy.requireHost(CallerIdentity, UUID gameId)`; `player/PlayerLevelsClient` (via `ServiceClients`, 503 on failure); `board/HostBoardQuery` (game, attendance, current matches + unreleased players; one read-only transaction), `board/HostBoardService`, `board/HostBoardController` (`Cache-Control: no-store`), `board/HostBoardResponse`; security rule `GET /api/v1/host/games/*/board`. `HostBoardIT`, `PlaymatchServiceAccessIT` rows (TC-15…TC-26) | `DONE` |
| T06 | AC-07 | playmatch: `board/HostGamesQuery`, `HostGamesController` `GET /api/v1/host/games`, `HostGamesResponse`; security rule. `HostGamesIT` (TC-27…TC-29) | `DONE` |
| T07 | AC-04, AC-05 | Frontend: `api/host.ts`, types in `api/types.ts`, approved copy in `copy/en.ts`, host labels in `lib/status.ts`; shared `components/CourtCard.tsx`, `PlayerChip.tsx`, `WaitIndicator.tsx`; `features/host-board/HostBoardPage.tsx`, `GameStrip.tsx`, `PlayerList.tsx`; route `/host/games/:gameId`; all page states (TC-30, TC-31) | `DONE` |
| T08 | AC-07 | Frontend: "Games you host" section on the home page for a signed-in account with a Manage link per game; hidden for anonymous (TC-32) | `DONE` |
| T09 | all | `API Document/`: two host routes + error table; internal header described for developers. Pre-commit pipeline (`scripts/precommit.sh`), live check on Docker (TC-33), `scope-guardian` + `privacy-reviewer`, `review.md` | `DONE` (2026-11-25; visual TC-30…32 left for the owner) |

## Test cases

Oracle = where the expected value comes from. "design" = [design.md](design.md) approved 2026-11-25.

### Identity token (common-web, unit, `IdentityTokenTest`, `CallerIdentityArgumentResolverTest`)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-02 | unit | secret S, fixed clock t0, account `userId` U | issue a token, verify it at t0 | `CallerIdentity.Account(U)` | design §3.4 |
| TC-02 | AC-02 | unit | token issued at t0 | verify at t0 + 30 s, and at t0 + 31 s | t0 + 30 s accepted; t0 + 31 s refused (expired) | design §3.4 (30 s life) |
| TC-03 | AC-02 | unit | token issued with secret S | verify with secret S′ | refused | design §3.4 |
| TC-04 | AC-02 | unit | valid token | change one character of the claims part, or of the signature part; replace `v1.` by `v2.`; empty string; no dots; 10 kB of junk | each refused, no exception other than the "invalid token" result | design §3.4; privacy rules |
| TC-05 | AC-02 | unit | secret of 31 bytes, or missing | build the token component | startup error that names the property, not the value | design §3.4 |
| TC-06 | AC-02 | unit | request without header / with a valid header / with an invalid header | resolve a `CallerIdentity` argument | `Anonymous` / `Account(U)` / `ApiException(UNAUTHENTICATED)` | design §3.4 |

### Gateway (IT with JDK `HttpServer` stub; `GatewayAccessMatrixIT` rows + `HostRoutesIT`)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-07 | AC-02 | gateway IT | anonymous | `GET /api/v1/host/games/{id}/board` and `GET /api/v1/host/games` | 401 `UNAUTHENTICATED`; stub received nothing | §13; design §3.1 |
| TC-08 | AC-02 | gateway IT | only a guest cookie, no account | same two calls | 401 `UNAUTHENTICATED`; stub received nothing; auth-service not called | design D-3 |
| TC-09 | AC-02 | gateway IT | signed-in account U | `GET …/board` | forwarded to playmatch stub with exactly one `X-Internal-Identity`; stub verifies it with the test secret → `Account(U)`; no `Cookie` header; answer body passed through | design §3.4; AD-14 |
| TC-10 | AC-02 | gateway IT | signed-in account U; browser also sends `X-Internal-Identity: <token for another user V>` | `GET …/board` | stub receives only the gateway's token for U, never V's | design §8; `HeaderSanitizer` |
| TC-11 | AC-02 | gateway IT | signed-in account | `POST`, `PUT`, `DELETE` on both host paths; `GET /api/v1/host/games/{id}/board/extra` | not forwarded (401/403/405 from the gateway, never reaches the stub); a forwarded **public** route (`GET /api/v1/public/games/X`) carries no `X-Internal-Identity` | design §3; token only on `HOST` routes |

### player-service (IT against `faircourt_test`)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-12 | AC-06 | player IT | a member profile (band 3, `CONFIRMED`) and a guest profile (band null, `SELF_REPORTED`) | `POST /internal/v1/players/levels` with both ids + one unknown id | 200; exactly 2 entries with `playerId`, `kind`, `band`, `bandStatus` equal to the rows; unknown id absent; JSON keys exactly `players[].playerId/kind/band/bandStatus`; `no-store` | design §3.3 |
| TC-13 | AC-06 | player IT | — | body with 0 ids, 201 ids, a non-UUID, no body | 400 `VALIDATION_FAILED`; no SQL error text in the body | design §3.3 |
| TC-14 | AC-06 | player access IT | — | `GET`, `PUT` on the route; `POST /api/v1/players/levels` | 401 (route not opened for them) | AD-15 |

### playmatch-service (IT against `faircourt_test`; player-service replaced by a JDK `HttpServer` stub; identity given as a test token)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-15 | AC-01 | playmatch IT | doubles game of host H, 2 courts; 1 `PENDING` guest (band null), 5 `READY` members, 1 `RESTING`, 1 `LEFT`; an `ACTIVE` match on court 2 with 4 `PLAYING` attendees (2 per team); stub returns levels for all | host H reads the board | 200; `players` has all 12 rows with `state`, `displayName`, `kind`, `band`, `bandStatus`, `checkedInAt`, `availableSince`, `matchesPlayed` equal to the rows/stub; `courts` = `[{1, null}, {2, {matchId, matchNo, state ACTIVE, teams A[2], B[2]}}]` with the right attendance ids | §3, §11, §13; design §3.1 |
| TC-16 | AC-01 | playmatch IT | singles game with a `PLANNED` match on court 1 | host reads the board | court 1 `state: PLANNED`, teams A and B with one id each; attendees `ASSIGNED` | OD-20; §11 |
| TC-17 | AC-01 | playmatch IT | a `FINISHED` and a `CANCELLED` match on court 1, nothing current | host reads the board | court 1 `match: null`; finished/cancelled matches not shown | design §3.1 |
| TC-18 | AC-01 | playmatch IT | READY players with different `matchesPlayed` / `availableSince`, plus other states | host reads the board | order: state group (Pending, Ready, Assigned, Playing, Resting, Left), then `matchesPlayed ASC`, `availableSince ASC`, `attendanceId` | §14 tie-breakers; design §3.1 |
| TC-19 | AC-01 | playmatch IT | fixed `Clock` at T | host reads the board | `asOf` = T (ISO-8601 UTC); timestamps ISO-8601 with offset | design D-6 |
| TC-20 | AC-01 | playmatch IT | a `CLOSED` game of H | host reads the board | 200 (read-only history is allowed) | §11 |
| TC-21 | AC-02 | playmatch IT | game of H | account V (not H) reads it | 403 `FORBIDDEN`; body has only `code`, `message`, `requestId`; stub not called | §13, §15 |
| TC-22 | AC-02 | playmatch IT | — | H reads an unknown game id; a malformed id | 404 `NOT_FOUND` both | §13 |
| TC-23 | AC-02 | playmatch IT | — | no token (unknown and existing game); expired token; token signed with another secret | 401 `UNAUTHENTICATED` each — also for an unknown game (identity checked before existence) | design §3.4, §5 |
| TC-24 | AC-03 | playmatch IT | board of TC-15 | host reads it | JSON field paths **equal** the documented set: `asOf`, `game.{id,publicCode,title,venue,startsAt,endsAt,courtCount,state,matchFormat,minSets}`, `players[].{attendanceId,displayName,kind,state,band,bandStatus,checkedInAt,availableSince,matchesPlayed}`, `courts[].{courtNo,match}`, `courts[].match.{matchId,matchNo,state,teams}`, `courts[].match.teams.{A,B}`; body does not contain H's `userId` or any `player_id` | design §3.1; AD-09 |
| TC-25 | AC-06 | playmatch IT | stub answers 500, or does not answer within the read timeout | host reads the board | 503 `SERVICE_UNAVAILABLE`; no partial board | system-design §7; AD-15 |
| TC-26 | AC-01 | playmatch IT | stub leaves out one attendance's `playerId` | host reads the board | 200; that player has `kind`, `band`, `bandStatus` = null; one warning log line with the attendance id only (no name) | design §5 |
| TC-27 | AC-07 | playmatch IT | H hosts games G1 (starts later) and G2; V hosts G3 | H calls `GET /api/v1/host/games` | 200; `games` = [G1, G2] in `startsAt DESC`; keys exactly `games[].{id,publicCode,title,venue,startsAt,endsAt,courtCount,state}` | design §3.2 |
| TC-28 | AC-07 | playmatch IT | account W hosts nothing | W calls it | 200 `{"games":[]}` | design §3.2 |
| TC-29 | AC-07 | playmatch IT | H hosts 51 games | H calls it | 50 newest returned | design §3.2 |
| — | — | existing | — | `SchemaBoundaryTest` in playmatch and player | still green (no cross-schema SQL) | AD-12 |

### Frontend and live (owner looks; vitest is not installed in this project)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-30 | AC-04 | visual | seeded stack; host signed in | open `/host/games/<seed id>`; then as `player1`; then signed out; then stop player-service; then a game with 0 and with 3 Ready players | success board; 403 page "You don't manage this game." + "Back to home" (owner 2026-11-26; was "Open the public page"); redirect to `/login?next=…`; error banner with "Try again"; "No players ready yet." / "Fewer than four players ready."; Suggest button visible, disabled, "Coming soon"; Refresh re-reads | design §7; §16 |
| TC-31 | AC-05 | visual | same board | view at 320, 375, 768, 1280 px | phone: court cards scroll sideways above the status lists, Suggest button sticky; desktop: courts in columns beside the Ready pool, max width 1200 px; every status has text + colour; targets ≥ 44 px; keyboard focus visible | §6, §16 |
| TC-32 | AC-07 | visual | home page | signed out; signed in as host; signed in as `player1` (hosts nothing) | no section; "Games you host" with the seed game and a Manage link to its board; "You are not hosting any games yet." | design §7, OD-04 |
| TC-33 | AC-01, AC-02 | live (Docker, WSL) | stack rebuilt with `INTERNAL_TOKEN_SECRET` set | `curl` through the gateway: host login + board; `player1` login + board; anonymous board; `docker compose logs` grep for the token value and player names | host 200 with 8 READY, 2 free courts; `player1` 403; anonymous 401; token and names in 0 log lines | §0, §13; privacy rules |

## Files

Planned (exact names may change only for internal naming; recorded under "Deviations").

| File | Added / changed | Why |
|---|---|---|
| `backend/libs/common-web/src/main/java/com/faircourt/common/identity/{CallerIdentity,IdentityToken,IdentityTokenHeader,CallerIdentityArgumentResolver}.java` | added | T01 |
| `backend/libs/common-web/src/main/java/com/faircourt/common/web/CommonWebAutoConfiguration.java` | changed | T01 register resolver |
| `backend/libs/common-web/src/test/java/com/faircourt/common/identity/*Test.java` | added | TC-01…TC-06 |
| `backend/pom.xml`, `compose.yaml`, `.env.example`, gateway + playmatch `application.yaml` | changed | T02 |
| `backend/services/gateway/.../routing/{RouteAccess,GatewayRouteTable,GatewayRoutes,IdentityTokenFilter}.java`, `.../security/GatewaySecurityConfig.java` | changed / added | T03 |
| `backend/services/gateway/src/test/.../{GatewayAccessMatrixIT,HostRoutesIT,StubService}.java` | changed / added | TC-07…TC-11 |
| `backend/services/player-service/.../level/*`, `.../security/SecurityConfig.java` | added / changed | T04 |
| `backend/services/player-service/src/test/.../{level/PlayerLevelsIT,PlayerServiceAccessIT}.java` | added / changed | TC-12…TC-14 |
| `backend/services/playmatch-service/.../{access,board,player}/*`, `.../security/SecurityConfig.java` | added / changed | T05, T06 |
| `backend/services/playmatch-service/src/test/.../{HostBoardIT,HostGamesIT,PlaymatchServiceAccessIT}.java` | added / changed | TC-15…TC-29 |
| `frontend/src/api/{host.ts,types.ts}`, `copy/en.ts`, `lib/status.ts`, `components/{CourtCard,PlayerChip,WaitIndicator}.tsx`, `features/host-board/*`, `features/home/HomePage.tsx`, `routes/AppRoutes.tsx` | added / changed | T07, T08 |
| `API Document/` (host routes) | changed | T09 |

Deleted files: none (deletions need explicit owner consent).

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-25 | Windows PC, WSL Ubuntu | `git fetch origin` (SSH); `git rev-list --left-right --count main...origin/main` | PASS; `0 0` (local `main` equal to GitHub); branch `feature/host-board` cut from it |
| 2026-11-25 | Windows PC, WSL | appended `INTERNAL_TOKEN_SECRET=<openssl rand -base64 48>` to the local `.env` (owner approved in the conversation; value never printed) | PASS; one line, 64 characters |
| 2026-11-25 | Windows PC, WSL | `docker compose config -q` | PASS |
| 2026-11-25 | Windows PC, WSL | `docker compose run --rm --build backend-format` (after T01–T03, T04–T06, fix round) | PASS each time |
| 2026-11-25 | Windows PC, WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl libs/common-web,services/gateway,services/playmatch-service -am verify` (T01–T03) | PASS: common-web 22 unit + 8 IT; gateway 13 unit + 107 IT (`HostRoutesIT` 5, `GatewayAccessMatrixIT` 15); playmatch 3 unit + 55 IT |
| 2026-11-25 | Windows PC, WSL | same with `-pl services/player-service,services/playmatch-service -am` (T04–T06) | PASS: player 23 tests (`PlayerLevelsIT` 2, `PlayerServiceAccessIT` 5); playmatch 72 (`HostBoardIT` 12, `HostGamesIT` 4, `PlaymatchServiceAccessIT` 7) |
| 2026-11-25 | Windows PC, WSL | `docker compose run --rm --no-deps frontend npm run format`; `… sh -c "npm run format:check && npm run build"` (T07–T08, fix round) | PASS each time (tsc + vite build) |
| 2026-11-25 | Windows PC, WSL | `scripts/precommit.sh` (before review) | `PIPELINE OK` in 104 s, 7 modules `SUCCESS` |
| 2026-11-25 | Windows PC, WSL | `docker compose up -d --build gateway player-service playmatch-service` | PASS; 3 services `healthy` |
| 2026-11-25 | Windows PC, WSL | live check TC-33 (`curl` through the gateway on 9090, script in the session scratchpad) | first run: script bug (wrong field name for the game id, host got 403 on an empty id path); second run PASS — see AC evidence |
| 2026-11-25 | Windows PC, WSL | fix round: `mvn … -pl libs/common-web,services/player-service,services/playmatch-service -am verify` | first run FAIL (new access-IT assertion expected `[]`, real body `{"games":[]}`; test assertion fixed, no product change); second run PASS: common-web 24 unit + 8 IT, player 8 unit + 23 IT, playmatch 3 unit + 73 IT |
| 2026-11-25 | Windows PC, WSL | `INTERNAL_TOKEN_SECRET= docker compose config -q` | exit 1: "required variable INTERNAL_TOKEN_SECRET is missing a value: Set INTERNAL_TOKEN_SECRET in .env (at least 32 characters, see .env.example)" — fail-fast works |
| 2026-11-26 | Windows PC, WSL | after the owner's `.env.example` decision: `docker compose --env-file .env.example config -q` (shell variable unset; real `.env` not read); then the same with `INTERNAL_TOKEN_SECRET=` | PASS: exit 0 with the shipped value (55 characters); empty value still exit 1 with the same message. Stack not restarted (no code change) |
| 2026-11-25 | Windows PC, WSL | `scripts/precommit.sh` (after fix round) | `PIPELINE OK` in 109 s; all modules `SUCCESS`, 0 failures (common-web 24 + 8, matching 13, gateway 13 + 107, auth 8 + 42, player 8 + 23, playmatch 3 + 73) |
| 2026-11-25 | Windows PC, WSL | rebuild of the 3 services + live check TC-33 again | PASS, same results as below |
| 2026-11-25 | Windows PC, WSL | `psql … select state, count(*) from playmatch.attendance group by state` | `READY 9`, `PENDING 6`: the local database holds the 8 seeded players plus rows from earlier live checks (2026-11-18…20) |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-15…TC-20, TC-26 (`HostBoardIT`), TC-33 live | `PASS` | ITs green; live: host board 200, `Cache-Control: no-store`, 9 READY (8 seeded + 1 member from an earlier live check), 2 courts with `match: null`, keys exactly as design §3.1 (no user id, player id, email) |
| AC-02 | TC-01…TC-11, TC-21…TC-23, access ITs, TC-33 live | `PASS` | unit + ITs green; live: `player1` board 403 `FORBIDDEN`, anonymous 401, anonymous with a forged `X-Internal-Identity` 401; guest cookie only → 401 (`HostRoutesIT`) |
| AC-03 | TC-24 (exact field-path equality), review grep | `PASS` | `HostBoardResponse` used only for the host audience, inside playmatch `board/` (controller, service, query); checked by scope-guardian and privacy-reviewer, see review.md |
| AC-04 | TC-30 visual | `NOT VERIFIED` | page builds; not opened in a browser (owner looks at Gate 3) |
| AC-05 | TC-31 visual | `NOT VERIFIED` | as above |
| AC-06 | TC-25 (stub 500 / timeout → 503), TC-12…TC-14 | `PASS` | ITs green; 503 not tried on the live stack |
| AC-07 | TC-27…TC-29, TC-32 visual, live | API `PASS`; page `NOT VERIFIED` | ITs green; live: host `GET /host/games` 200 with 1 game, `player1` 200 `{"games":[]}`; home section not opened in a browser |
| Logs | TC-33 | `PASS` | gateway, playmatch, player logs: 0 lines with the secret, 0 with a token, 0 with the first player's quoted name |

## Deviations from the design

- Clarification (not a behavior change for any caller): the gateway adds the identity token only on `HOST` routes, not on every forwarded route, so public routes such as the public game page never carry it (TC-11). Later route kinds opt in when a feature needs the caller.
- The test-only token secret lives in `backend/pom.xml` (surefire/failsafe), so tests do not depend on `.env`.
- `AccessPolicy.requireAccount` added next to `requireHost`: the controllers check for an account before parsing the path id, so an anonymous caller gets 401 even for a malformed id (design §5, TC-23). Internal structure.
- `playmatch.guest.ServiceClients` made `public` so `playmatch.player.PlayerLevelsClient` can reuse the timeouts; the class stays in `guest` (moving it is a file move; later cleanup). Internal structure.
- `PlayerLevelsClient` sends ids in chunks of 200 (the internal route's limit); a game with ≤ 200 players is still one call. No behavior change for callers.
- `MatchState` enum added in `playmatch.match` (constants only; transitions come with the match features) so the board does not carry the match state as a string (review fix).
- `.env.example`: review round 1 left `INTERNAL_TOKEN_SECRET` empty (compose then stops with a clear message; verified). Owner decision 2026-11-26: ship a working local-only value instead (`local-dev-only-identity-token-secret-not-for-deployment`, 55 characters, public in git) so §0's `cp .env.example .env && docker compose up --build -d` works unchanged; a real random secret is part of the pre-deployment gate (`CLAUDE.md`, backlog L-01). The `:?` check in `compose.yaml` still stops the stack when the value is empty.
- **403 page (owner decision 2026-11-26, review.md #1 option a):** links to home ("Back to home") instead of "Open the public page" (§16, design §7), because the 403 answer has no public code and the page only knows the game id. No new field or route. The "Open the public page" link stays on the board's game strip.
- **Token audience (owner decision 2026-11-26, review.md #17):** binding the identity token to audience / method / path is decided in the Attendance management design, before its first host write route.
