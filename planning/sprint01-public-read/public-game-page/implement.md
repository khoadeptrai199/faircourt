# F02 implementation — Public game page + frontend foundation

Design: [design.md](design.md), approved by the owner 2026-11-18 ("duyệt hết, triển khai implement đi"). Branch `feature/public-game-page` (created from `main` on 2026-11-18). Built together with F19 [Game rename](../game-rename/README.md) on the same branch.

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01, AC-02 | playmatch-service: `PublicGameResponse`, `PublicGameQuery`, `PublicGameController`, `MatchFormat`, `GameState`, `PriceRange`, `PricePerPerson`; service security rule; gateway route + `faircourt.services.playmatch-url` | `DONE` |
| T02 | AC-01…AC-04, AC-07 | `PublicGameIT` (TC-01…TC-04), `PlaymatchServiceAccessIT` row, `GatewayAccessMatrixIT` rows (TC-05) | `DONE` |
| T03 | — | `react-router` 8.5.1, `--save-exact`, lockfile generated inside the Node container | `DONE` (README version table not written: README stays empty by owner request; version is in `package.json`) |
| T04 | — | `api/client.ts`, `api/errors.ts`, `api/types.ts`, `api/games.ts` | `DONE` |
| T05 | AC-05 | `hooks/useAsync.ts` | `DONE` |
| T06 | AC-03 | `lib/time.ts`, `lib/money.ts`, `lib/status.ts`, `copy/en.ts` | `DONE` |
| T07 | AC-03, AC-05 | Components `Button`, `StatusBadge`, `EmptyState`, `ErrorBanner`, `BackHomeLink` | `DONE` |
| T08 | AC-02…AC-06 | `routes/AppRoutes.tsx`, `App.tsx`, `features/public-game/` | `DONE` |
| T09 | AC-06 | Visual check at 320 / 375 / 1280px | `NOT VERIFIED` (no browser was driven in this session; owner to check) |
| T10 | AC-07, AC-08 | `curl` through the gateway (TC-06); `API Document/backend.md` / `frontend.md`; pipeline | `DONE` |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/game/{PublicGameController,PublicGameQuery,PublicGameResponse,MatchFormat,GameState,PriceRange,PricePerPerson}.java` | added | Endpoint (design §6) |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/security/SecurityConfig.java` | changed | `GET /api/v1/public/games/*` permitted |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/PublicGameIT.java` | added | TC-01…TC-04 + writes refused |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/PlaymatchServiceAccessIT.java` | changed | Access-matrix row for the new route |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/routing/{GatewayRouteTable,GatewayRoutes}.java` | changed | Route `public-game` (GET only, anyone) → playmatch-service |
| `backend/services/gateway/src/main/resources/application.yaml` | changed | `faircourt.services.playmatch-url: http://playmatch-service:8080` |
| `backend/services/gateway/src/test/java/com/faircourt/gateway/{GatewayAccessMatrixIT,GatewayHealthIT,StubService}.java` | changed | Rows for the new route; stub receives the production service routes |
| `frontend/package.json`, `frontend/package-lock.json` | changed | `react-router` 8.5.1 (exact) |
| `frontend/src/api/{client,errors,types,games}.ts` | added | API layer (AD-08) |
| `frontend/src/hooks/useAsync.ts`, `frontend/src/lib/{time,money,status}.ts`, `frontend/src/copy/en.ts` | added | Shared pieces |
| `frontend/src/components/{Button,StatusBadge,EmptyState,ErrorBanner,BackHomeLink}.tsx` | added | Base components |
| `frontend/src/features/public-game/{PublicGamePage,GameHero}.tsx`, `frontend/src/routes/AppRoutes.tsx` | added | Page and routes |
| `frontend/src/App.tsx` | changed | `BrowserRouter` + routes |
| `frontend/src/features/home/HomePage.tsx` | changed | Copy "session" → "game", contest "game" → "match" (F19) |
| `API Document/backend.md`, `API Document/frontend.md` | changed | Endpoint contract, status, naming |

Deleted files: none.

## Commands executed

All on the Windows PC in WSL Ubuntu 24.04 (Docker Engine 29.5.3), 2026-11-18.

| Command | Result |
|---|---|
| `curl https://registry.npmjs.org/react-router/latest` and the package's `time` / `8.5.1` metadata | latest 8.5.1, published 2026-10-27; peer `react >=19.2.7`, engine `node >=22.22.0` |
| `docker compose run --rm --no-deps frontend npm install --save-exact --no-audit --no-fund react-router@8.5.1` | added 3 packages; `package.json` + lockfile updated |
| Inspected `node_modules/react-router/dist/production/index.d.ts` in the container | exports `BrowserRouter`, `Routes`, `Route`, `Link`, `useParams` (used) |
| `docker compose run --rm --build backend-format` + `backend-test mvn -B clean verify` (1st run) | `BUILD SUCCESS`, 95 tests, 0 failures |
| `docker compose build frontend`; `npm run format`, `format:check`, `npm run build` | all OK; `tsc` clean; 110 modules |
| `docker compose up -d --build` | 6 containers healthy; playmatch Flyway "Migrating schema "playmatch" to version "2 - rename session to game"" → applied |
| `curl` through gateway 9090 and Vite 9091 (see evidence) | as below |
| `docker compose stop playmatch-service`; `curl`; `docker compose start playmatch-service` | 503 `SERVICE_UNAVAILABLE` while stopped; 200 after restart |
| Full pipeline (guide §10.1: backend-format → frontend format → `mvn -B clean verify` → `format:check` + `build` → staged-files check) | `PIPELINE OK` in 73 s; 95 tests; `spotless:check` clean; no `.env` / `.idea/` staged |
| Review by `scope-guardian` + `privacy-reviewer` (read-only agents) | no high/medium after owner decisions; low findings fixed (see review.md) |
| Full pipeline again after the review fixes | `PIPELINE OK` in 70 s; 95 tests, 0 failures; `GatewayAccessMatrixIT` 7/7 |

Tests by module (pipeline run): `common-web` 22, `matching` 13, `gateway` 23, `auth-service` 8, `player-service` 8, `playmatch-service` 21 (`PublicGameIT` 6, `PlaymatchServiceAccessIT` 3, `PlaymatchSchemaIT` 9, `SchemaBoundaryTest` 2, context 1).

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `PublicGameIT.anyoneReadsTheAllowlistedSummaryOfAGame` | `PASS` | allowlist incl. nested price paths; values equal the row; times `…Z`; attendance name, host id and public code absent |
| AC-02 | `PublicGameIT` + live `curl` | `PASS` (API) / `NOT VERIFIED` (page visual) | unknown, 21-char, `FRI-7Q2K9`, `' OR 1=1` → 404 `NOT_FOUND`; live `NOPE1234` → 404. Page not-found state only checked by code + `tsc`, not in a browser |
| AC-03 | visual | `NOT VERIFIED` | page `/g/7RQGQ484` answers 200 through Vite; rendering not looked at in a browser |
| AC-04 | `PublicGameIT.aClosedGameIsReturnedWithItsState` | `PASS` (API) / `NOT VERIFIED` (page visual) | `state: "CLOSED"` |
| AC-05 | live 503 + code | `PASS` (API) / `NOT VERIFIED` (page visual) | playmatch stopped → gateway 503 `SERVICE_UNAVAILABLE`; page shows `ErrorBanner` + Try again for any non-404 error (code path, not seen in a browser) |
| AC-06 | visual 320 / 375 / 1280px | `NOT VERIFIED` | no browser tooling used |
| AC-07 | `GatewayAccessMatrixIT`, `PlaymatchServiceAccessIT`, `PublicGameIT`, pipeline | `PASS` | gateway: anonymous GET forwarded as GET; POST/PUT/PATCH/DELETE → 401/403, stub never reached; `/public/games/X/guests` → 401. Service: GET open (404 for unknown), writes 401, no `Set-Cookie`; pipeline green |
| AC-08 | `curl http://localhost:9090/api/v1/public/games/7RQGQ484` | `PASS` | `{"gameId","title","venue","startsAt","endsAt","courtCount":2,"state":"OPEN","matchFormat":"DOUBLES","minSets":6,"pricePerPerson":{"male":{"minVnd":75000,"maxVnd":85000},"female":{"minVnd":65000,"maxVnd":75000}}}` — only allowlisted keys. Lowercase code via 9091 → 200; POST → 403 `CSRF_TOKEN_INVALID`; old `/public/sessions/...` → 401 |

Also checked (API foundation AC-05, deferred to this feature): a real gateway → service hop keeps the request ID: `4de7f83b-…` appears in the gateway log line and the playmatch-service log line for `GET /api/v1/public/games/NOPE1234`, and in the error body.

## Deviations from the design

- D-1 (added here, approved by the owner 2026-11-18 after review): a price line with only one bound set is shown as one price (both bounds = the set one). The DB allows it; the design did not say.
- `PublicGameIT` uses `@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "spring.flyway.clean-disabled=false")` instead of `@IntegrationTest`, because the shared annotation has no web server; same `TestDatabase.reset` guard.
- Gateway gets a new property `faircourt.services.playmatch-url` (default `http://playmatch-service:8080`) so the route table knows where playmatch-service is.
- Added `BackHomeLink` component (used by not-found and the `*` route) and copy `common.pageNotFoundTitle` "Page not found" for the `*` route (approved by the owner 2026-11-18 after review).
- Home page strings stay inline in `HomePage.tsx` (older code; not moved to `copy/en.ts` unasked).
- Seeded demo title in the existing WSL database is still "FairCourt demo session" (see F19 notes).
