# F08 implementation — Create game

Design: [design.md](design.md) (`APPROVED` 2026-11-26)

Branch: `feature/create-game` (from `main` at `7e76d8a`, 2026-11-26).

## Tasks

Order: backend first (T01–T05), then frontend (T06–T08), then docs and checks (T09–T10). Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-05 | `PublicCodeGenerator` as a Spring bean (config in `playmatch/game` or `common`, `SecureRandom`); `LocalDemoSeeder` keeps its own instance (not refactored) | `DONE` |
| T02 | AC-01, AC-03 | `CreateGameRequest` (record, Bean Validation: title/venue not blank + max length after trim, times required with offset, `courtCount` 1–4, `matchFormat` enum, `minSets` 1–20, price bounds 0–10,000,000) + nested price records; cross-field rules in `GameService`: `endsAt > startsAt`, `endsAt > now` (clock), `endsAt − startsAt ≤ 12 h` (OD-26), `minVnd ≤ maxVnd`, one bound alone → both | `DONE` |
| T03 | AC-01, AC-05 | `GameService.create(caller, request)`: one `@Transactional` method; `requireAccount`; `UUID.randomUUID()`; `INSERT … ON CONFLICT (public_code) DO NOTHING` via `JdbcClient` with named parameters, up to 5 codes, then `ApiException(INTERNAL_ERROR)`; `created_at` from the `Clock`; `CreateGameResponse` | `DONE` |
| T04 | AC-01, AC-04 | `CreateGameController`: `POST /api/v1/games`, JSON only, `@Valid`, `CallerIdentity`, `201` + `Location: /api/v1/host/games/{id}/board` + `Cache-Control: no-store` | `DONE` |
| T05 | AC-04 | Gateway route row `create-game` (`POST`, `/api/v1/games`, `HOST`, playmatch, `aud` playmatch-service); rows in `GatewayAccessMatrixIT` and `PlaymatchServiceAccessIT`; check `/api/v1/games/*/join` and `/me` still match their own rows | `DONE` |
| T06 | AC-06 | Frontend API: `createGame(...)` in `api/games.ts` (uses `apiPost`), types `CreateGameInput` / `CreatedGame` in `api/types.ts`; approved copy in `copy/en.ts` (`createGame.*`, `home.createGame`) | `DONE` |
| T07 | AC-06 | `features/create-game/CreateGamePage.tsx` (+ small helpers in the same folder: form state, field checks, Vietnam-time → ISO with `+07:00`, next-day end); route `/host/games/new` in `routes/AppRoutes.tsx`; sign-in check via `/me` → redirect `/login?next=/host/games/new`; `useMutation`; preview line with `formatGameTime` + `formatVndRange`; after 201 navigate to `/host/games/{gameId}` | `DONE` |
| T08 | AC-06 | Home page: "Create a game" link-button in the "Games you host" section (also when the list is empty) | `DONE` |
| T09 | — | `API Document/backend.md` (route, body, rules, errors) and `frontend.md` (page, states); pre-commit pipeline `scripts/precommit.sh` | `DONE` (`API Document/backend.md`, `frontend.md`, flow maps; `scripts/precommit.sh` PIPELINE OK) |
| T10 | AC-01, AC-02, AC-06 | Live check in Docker (TC-16) + owner visual check (TC-17, TC-18) | `DONE` (TC-16 PASS; TC-17, TC-18 PASS by the owner 2026-11-26) |

## Test cases

Approved at Gate 2 together with the tasks. Oracle = where the expected value comes from.

| TC | Covers | Layer | Given | When | Then (status, body, rows, message) | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-01 | IT | host token; full valid body with both price lines | `POST /api/v1/games` | 201; body keys exactly `gameId`, `publicCode` (8 chars from the generator alphabet); `Location` = board path; `no-store`; one new row: `host_user_id` = token user, `state` `OPEN`, `next_match_no` 1, title/venue trimmed, times equal the sent instants in UTC, format, min sets, four price columns as sent | design §3.1, §4; §11, §12 |
| TC-02 | AC-01 | IT | as TC-01 without `pricePerPerson` | create | 201; four price columns `NULL`; `GET /public/games/{code}` → `pricePerPerson: null` | design §3.1; OD-18 |
| TC-03 | AC-01 | IT | price line `male` with only `minVnd: 80000` | create | 201; `price_male_min_vnd` = `price_male_max_vnd` = 80000; female columns `NULL` | design §3.1 (`PriceRange.of` rule) |
| TC-04 | AC-01 | IT | body also has `"hostUserId": <player1 id>` | create | 201; row host = caller, not `player1` | §13 |
| TC-05 | AC-01 | IT | game 20:00 → next day 00:30 (+07:00), 4.5 h | create | 201; `ends_at` on the next day | design D-2, OD-26 |
| TC-06 | AC-02 | IT | after TC-01 | host `GET /host/games`; host board; `player1` board; anonymous public page | list contains the new game; board 200; `player1` 403 `FORBIDDEN`; public 200 with the sent title | §11, §17; Host board design |
| TC-07 | AC-03 | IT (parameterized) | host token | one invalid field each: blank title, title 121 chars after trim, blank venue, venue 161, missing `startsAt`, time without offset, `endsAt = startsAt`, `endsAt < startsAt`, `endsAt` ≤ now, length 12 h 1 min, `courtCount` 0 and 5, `matchFormat` `MIXED`, `minSets` 0 and 21, price −1, price 10,000,001, `minVnd > maxVnd`, non-JSON body | 400 `VALIDATION_FAILED`; `game` row count unchanged | design §3.1; OD-26 |
| TC-08 | AC-03 | IT | host token | length exactly 12 h; `endsAt` = now + 1 min with `startsAt` in the past | 201 both (edges allowed) | OD-26 |
| TC-09 | AC-04 | IT (`PlaymatchServiceAccessIT`) | no token / guest token / account token | `POST /api/v1/games` | 401 / 401 / 201 | §3, §13; Host board D-3 |
| TC-10 | AC-04 | IT (playmatch) | account token issued for `POST /api/v1/games/{id}/join` | sent to `POST /api/v1/games` | 401; no row | identity token v2 (Attendance management D-4) |
| TC-11 | AC-04 | IT (`GatewayAccessMatrixIT`, stub service) | anonymous; guest cookie only; account without CSRF; account with CSRF | `POST /api/v1/games` | 401; 401; 403 `CSRF_TOKEN_INVALID`; forwarded to playmatch with token v2 (`aud` playmatch-service, `POST`, `/api/v1/games`) and the stub's 201 passed through | AD-14; API foundation |
| TC-12 | AC-04 | IT (gateway) | account with CSRF | `POST /api/v1/games/{id}/join` and `GET /api/v1/games/{id}/me` | still answered by their own routes (no regression from the new row) | design §6 |
| TC-13 | AC-05 | IT | stub generator: existing code, existing code, new code | create | 201 with the third code; one row | design §4 |
| TC-14 | AC-05 | IT | stub generator always returns an existing code | create | 500 `INTERNAL_ERROR`; row count unchanged | design §4 |
| TC-15 | AC-03 | unit (`GameServiceTest` or a rules class test, no Spring) | fixed clock | cross-field rules: end after start, not ended, ≤ 12 h, min ≤ max, one bound → both | accept / reject as in TC-07, TC-08 | OD-26; design §3.1 |
| TC-16 | AC-01, AC-02 | live | Docker stack on 9090 | host login → CSRF → create (curl) → public page by code → board → `GET /host/games` | 201; 200; 200; list has it | §17 checklist |
| TC-17 | AC-06 | visual (owner) | signed in as host | home → "Create a game"; submit empty; fill; 20:00 / 00:30; submit | defaults (today, 18:00–21:00, 1 court, Doubles, 6); field errors under fields, focus on Title; preview line; "Ends the next day"; "Creating…" with inputs disabled; lands on the new board; signed out → login and back to the form | design §7 |
| TC-18 | AC-06 | visual (owner) | same | 320 px and 1280 px, keyboard only | one column; time inputs side by side; buttons ≥ 44 px; tab order top to bottom; radio groups work with arrow keys; visible focus | §16; conventions |

No frontend test runner exists in the repo, so the time conversion helper (design TC-13) is covered by TC-17 and TC-05; adding a runner would need approval (not proposed).

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/game/CreateGameController.java` | Added | route |
| `…/game/CreateGameRequest.java` (+ nested price records) | Added | request |
| `…/game/CreateGameResponse.java` | Added | response |
| `…/game/GameService.java` | Added | command |
| `…/game/PublicCodeGenerator.java` or a config class | Changed / added | bean |
| `backend/services/playmatch-service/src/test/java/com/faircourt/playmatch/CreateGameIT.java` | Added | TC-01…TC-08, TC-10, TC-13, TC-14 |
| `…/playmatch/PlaymatchServiceAccessIT.java` | Changed | TC-09 |
| `…/game/GameServiceTest.java` | Added | TC-15 |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/routing/GatewayRouteTable.java` | Changed | route row |
| `backend/services/gateway/src/test/java/com/faircourt/gateway/GatewayAccessMatrixIT.java` | Changed | TC-11, TC-12 |
| `frontend/src/api/games.ts`, `api/types.ts`, `copy/en.ts` | Changed | API call, types, copy |
| `frontend/src/features/create-game/CreateGamePage.tsx` (+ helpers) | Added | page |
| `frontend/src/routes/AppRoutes.tsx`, `features/home/HomePage.tsx` | Changed | route, entry button |
| `API Document/backend.md`, `API Document/frontend.md` | Changed | docs |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/security/SecurityConfig.java` | Changed | open `POST /api/v1/games` (owner approved 2026-11-26, see Deviations) |
| `.claude/flow/maps/api-routes.md`, `.claude/flow/maps/index.md` | Changed | discovery maps |

Deleted files: none (deletions need explicit owner consent).

Not refactored (conventions): `LocalDemoSeeder` keeps its own insert SQL and generator instance.

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-26 | Windows / WSL | `docker compose run --rm --build backend-format` (several times, by the implementer) | PASS |
| 2026-11-26 | Windows / WSL | `docker compose --profile test run --rm --build backend-test mvn -B -pl services/playmatch-service,services/gateway -am verify` | first runs FAIL: `CreateGameIT` 401 without the `SecurityConfig` line; then "too many clients already" (its own Spring context from `@MockitoSpyBean`; fixed by reusing the shared context). Final run after review fix round 1: BUILD SUCCESS — gateway 13 unit + 111 IT, playmatch 12 unit (`GameServiceTest` 6) + 117 IT (`CreateGameIT` 30, `PlaymatchServiceAccessIT` 10), 0 failures |
| 2026-11-26 | Windows / WSL | `docker compose run --rm --no-deps frontend npm run format`, then `sh -c "npm run format:check && npm run build"` | PASS (before and after review fix round 1) |
| 2026-11-26 | Windows / WSL | `bash scripts/precommit.sh` | PIPELINE OK (before review; again after fix round 1: every module 0 failures, playmatch 117 IT, gateway 111 IT) |
| 2026-11-26 | Windows / WSL | `docker compose up -d --build playmatch-service gateway` | PASS (both healthy) |
| 2026-11-26 | Windows / WSL | live script `live-f08.sh` (session scratchpad; `curl` through the gateway on 9090) | PASS: host login 200; anonymous create without CSRF → 403; signed in without CSRF → 403 `CSRF_TOKEN_INVALID`; create → 201 `{"gameId","publicCode"}`, `Cache-Control: no-store`, `Location` = board path; public page 200 with `SINGLES`, 5 sets, men 80,000–80,000 (one bound used as both), women `null`, times stored as UTC (18:00+07:00 → 11:00Z); board 200; "Games you host" lists it. Leaves one game "Live F08 game" in the local demo database |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-01…TC-05, TC-16 | `PASS` | `CreateGameIT` green (final run above); live 201 + public page |
| AC-02 | TC-06, TC-16 | `PASS` | `CreateGameIT` (list, board 200, `player1` 403, public 200); live board 200 + list |
| AC-03 | TC-07, TC-08, TC-15 | `PASS` | `CreateGameIT` 18 parameterized cases + non-JSON, row count unchanged; edges; `GameServiceTest` 6 |
| AC-04 | TC-09…TC-12 | `PASS` | `PlaymatchServiceAccessIT`, `GatewayAccessMatrixIT` (other methods → 403 with no forward, pinned after review), `CreateGameIT` token for join path → 401; live CSRF 403 |
| AC-05 | TC-13, TC-14 | `PASS` | `CreateGameIT` (scripted generator: third code used; 5 collisions → `INTERNAL_ERROR`, count unchanged) |
| AC-06 | TC-17, TC-18 | `PASS` | owner visual and keyboard check 2026-11-26 ("UI ok"); frontend build + format check PASS |

## Deviations from the design

- Plan file list missed `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/security/SecurityConfig.java`: the service chain denies every route not opened there, so `POST /api/v1/games` needs one `permitAll` line (identity is still checked by `AccessPolicy.requireAccount`). Owner approved 2026-11-26 in the conversation.
- Also changed (in the plan's spirit, no new file): `.claude/flow/maps/api-routes.md` and `maps/index.md` (config `docs` asks for a map row per new route and test class); `frontend/src/features/create-game/gameForm.ts` is the "(+ helpers)" of T07.
- TC-11: the gateway test stub answers `200` for this path, so the test asserts the pass-through of `200` (not `201`); the real `201` is covered by `CreateGameIT` and the live check.
- TC-09: the service only knows account tokens; its "guest" row sends an invalid token (→ 401). The real guest-cookie row is in `GatewayAccessMatrixIT`.
- TC-05 sends the literal 20:00+07:00 → next day 00:30+07:00.
- Error state on the form: the shared `ErrorBanner` has a fixed title and a required Retry button, so the page renders its own small alert (same tokens, `role="alert"`) with the approved text "The game was not created. Try again." and no Retry button; the Create game button is the retry (review fix round 1). A 401 on submit is this error state too (no redirect, the input is kept); the login redirect is only for the page-load check.
- Reused approved strings not listed in the copy table: `publicGame.courtWord`, `publicGame.priceMale` / `priceFemale` (preview line), `common.loading`.
- Price inputs are `type="number"` with `inputMode="numeric"`, plus a digits-only check.
- `PublicCodeGenerator` is no longer `final` (a `@Component` with a no-arg constructor using `SecureRandom`), so `CreateGameIT` can script codes with a subclass.
