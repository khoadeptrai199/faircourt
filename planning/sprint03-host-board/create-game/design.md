# F08 design — Create game

Status: `APPROVED` (2026-11-26, by owner: all recommendations — OD-05 = b form, OD-10 = a out of MVP, OD-26 = b, D-1 = a `JdbcClient`, D-2 = a day + start + end, D-3 = a, D-4 = a — and the copy table)

## 1. Summary

Any signed-in account posts a new game (title, venue, day and time, 1–4 courts, singles or doubles, minimum sets per player, optional price per person) and becomes its only host. The server picks a random public code; the host lands on the new game's board, where the game strip already links the public page to share.

Today a game exists only through the local demo seed. After this feature a host creates games from the app (§17 checklist line 2: "Host can create a session …").

Reuses: `PublicCodeGenerator` (already in playmatch-service), `AccessPolicy.requireAccount`, identity token v2, `JdbcClient` write style (Attendance management D-1), `TextField`, `Button`, `ErrorBanner`, `useMutation`, `formatGameTime`, `formatVndRange`. Adds: one route, one command service, one form page. No migration.

## 2. Blueprint references

- §3 step 1: "Organizer creates a session with time, venue and number of courts."
- §3 table, role "Court host / session owner": "Create a session, invite people, …".
- §11: "Any signed-in account may create a session; the creator is its sole host. Host authority is checked by `play_session.host_user_id`, not a global `ADMIN` flag."
- §11: "A random public code opens a read-only session summary."
- §12 `play_session`: `public_code VARCHAR(20) NOT NULL UNIQUE`, `title VARCHAR(120)`, `venue VARCHAR(160)`, `starts_at` / `ends_at TIMESTAMPTZ`, `court_count SMALLINT CHECK (court_count BETWEEN 1 AND 4)`, `state DEFAULT 'OPEN'`, `CHECK (ends_at > starts_at)`.
- §13: "`POST /sessions` — Signed-in account — Create session; creator becomes host." "Never accept `hostUserId` … from a client as proof of its identity." "Return ISO-8601 timestamps with timezone." "`400` invalid payload, `401` missing/expired identity".
- §16: the route table has **no** create screen (hence OD-05). "every host mutation can be `idle / saving / success / conflict`." "Buttons disable while saving to avoid duplicate taps; the backend still validates writes."
- §17 checklist: "Host can create a session, approve a guest and see the full board; another account gets 403 on host routes."
- Owner additions already in the schema: match format singles/doubles (OD-20), minimum sets per player 1–20, default 6 (OD-19), optional price per person by gender as a range (OD-18).

Routes renamed by OD-22: `POST /sessions` → `POST /api/v1/games`.

## 3. API contract

### 3.1 `POST /api/v1/games` (§13 `POST /sessions`)

| Item | Value |
|---|---|
| Route | `POST /api/v1/games`; gateway forwards to playmatch-service (same path) |
| Caller | any signed-in account (§11) |
| Identity source | account session at the gateway → identity token v2 (`aud` = playmatch-service, `POST`, `/api/v1/games`) → `CallerIdentity.Account`. The new game's `host_user_id` is the caller's `userId`; the body has no host field and any extra field is ignored |
| Access check | gateway: route kind `HOST` (signed-in account, identity token) + CSRF header `X-CSRF-TOKEN`; playmatch: `AccessPolicy.requireAccount(caller)` (no game exists yet, so no `requireHost`) |
| Request | see below |
| Response | `201`, `Cache-Control: no-store`, `Location: /api/v1/host/games/{gameId}/board`, `CreateGameResponse` `{"gameId":"uuid","publicCode":"FRI7Q2K9"}` |
| Allowed response keys | exactly `gameId`, `publicCode` (the caller is now the host; nothing else is echoed) |
| Errors | anonymous / guest cookie only → `401 UNAUTHENTICATED` (gateway; same rule as every host route, Host board D-3); missing or stale CSRF → `403 CSRF_TOKEN_INVALID`; invalid body → `400 VALIDATION_FAILED`; no free public code after 5 tries → `500 INTERNAL_ERROR` (practically never: 32⁸ ≈ 10¹² codes); playmatch down → `503 SERVICE_UNAVAILABLE` (gateway) |

Request body (`CreateGameRequest`):

```json
{
  "title": "Friday after-work doubles",
  "venue": "Lan Anh Club, District 10",
  "startsAt": "2026-11-27T18:00:00+07:00",
  "endsAt": "2026-11-27T21:00:00+07:00",
  "courtCount": 2,
  "matchFormat": "DOUBLES",
  "minSets": 6,
  "pricePerPerson": {
    "male": { "minVnd": 75000, "maxVnd": 85000 },
    "female": { "minVnd": 65000, "maxVnd": 75000 }
  }
}
```

| Field | Rule |
|---|---|
| `title` | required; trimmed; 1–120 characters |
| `venue` | required; trimmed; 1–160 characters |
| `startsAt`, `endsAt` | required; ISO-8601 with offset; stored as UTC; `endsAt > startsAt`; time window per OD-26 |
| `courtCount` | required; integer 1–4 |
| `matchFormat` | required; `SINGLES` or `DOUBLES` |
| `minSets` | required; integer 1–20 |
| `pricePerPerson` | optional; `null` or missing = no price shown. Each line (`male`, `female`) optional; inside a line `minVnd` and `maxVnd` are integers 0–10,000,000 and `minVnd ≤ maxVnd`; one bound alone is used as both (existing `PriceRange.of` rule) |

A `400` answer has the standard body `{"code":"VALIDATION_FAILED","message":"The request is invalid.","requestId":…}` with no per-field list (the shared error model has none; adding one would be a change to the API foundation). The form checks every rule itself before sending and shows the message next to the field, so a `400` from the server means a bug or a hand-made request.

### 3.2 Unchanged routes

- `GET /api/v1/host/games` ("Games you host") lists the new game automatically (it reads `game.host_user_id`).
- `GET /api/v1/host/games/{gameId}/board` opens it; another account → 403 (already tested by Host board).
- `GET /api/v1/public/games/{publicCode}` shows it to anyone.

No new internal routes. No change to another service.

## 4. Data and transactions

- **No migration.** Table `playmatch.game` already has every column (`V1`, `V2`): `id`, `public_code`, `host_user_id`, `title`, `venue`, `starts_at`, `ends_at`, `court_count`, `next_match_no` (default 1), `state` (default `OPEN`), `match_format`, `min_sets`, the four `price_*_vnd` columns, `created_at` (default `now()`; written from the injected `Clock` instead, so tests control it).
- One `@Transactional` method `GameService.create(caller, request)`:
  1. `AccessPolicy.requireAccount(caller)` → 401 otherwise.
  2. Validate (§3.1 table) → `400`.
  3. `id = UUID.randomUUID()`; `code = PublicCodeGenerator.next()`.
  4. `INSERT INTO game (...) VALUES (...) ON CONFLICT (public_code) DO NOTHING`; 0 rows → new code, up to 5 tries; then `INTERNAL_ERROR`. Using `ON CONFLICT DO NOTHING` keeps the transaction usable (a plain unique violation would abort the PostgreSQL transaction and every retry inside it).
- No lock needed: a new row, nobody else can see it yet.
- No call to another service, so nothing to compensate (AD-16 not triggered).
- Double submit (two taps, slow network) can create two games; the button is disabled while saving, and there is no idempotency key in MVP (D-3).

## 5. Domain rules and state transitions

- A new game starts `OPEN` (DB default; §11). Closing stays out (OD-10).
- The creator is the only host; there is no co-host (§11).
- No attendance rows are created; the host is not a player of their own game unless they join like anyone else (existing member join).
- `GameService` is the first writer of `game` besides the seed; the seed keeps its own SQL (not refactored, listed in `implement.md`).

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `playmatch/game/CreateGameController.java` | New | thin controller | `POST /api/v1/games`, `@Valid`, `requireAccount`, `201` + `Location` + `no-store` |
| `playmatch/game/CreateGameRequest.java` | New | request record | Bean Validation for sizes and ranges; cross-field rules (end after start, min ≤ max, time window) in the service |
| `playmatch/game/CreateGameResponse.java` | New | response record | `gameId`, `publicCode` |
| `playmatch/game/GameService.java` | New | command service (`@Transactional`, `JdbcClient`) | validation, code retry, insert; injects `Clock`, `PublicCodeGenerator` |
| `playmatch/game/PublicCodeGenerator.java` | Changed | — | becomes a Spring bean (today only `LocalDemoSeeder` creates one with `new`); the seeder keeps its own instance |
| `gateway/routing/GatewayRouteTable.java` | Changed | route row | `create-game`, `POST`, `/api/v1/games`, `HOST`, playmatch, `aud` playmatch-service |
| Tests | New | see §9 | `CreateGameIT`, `GameServiceTest` (code retry with a stub generator), rows in `AccessMatrixIT` and `GatewayAccessMatrixIT` |
| `API Document/backend.md`, `frontend.md` | Changed | docs | new route and page |

`POST /api/v1/games` vs the existing `POST /api/v1/games/*/join`: different paths, no clash in the route table (`*` matches one segment).

## 7. Frontend

- Route: `/host/games/new` → `features/create-game/CreateGamePage.tsx` (OD-05 = b). React Router prefers the fixed segment `new` over `/host/games/:gameId`.
- Entry: a "Create a game" link-button in the "Games you host" section on the home page, shown to signed-in accounts (also when the list is empty).
- Not signed in → `/login?next=/host/games/new` (existing same-site `next` rule).
- Data request: `GET /api/v1/me` for the sign-in check (existing); write `POST /api/v1/games` through `api/games.ts` → `createGame(...)` with `useMutation`.
- After `201`: navigate to `/host/games/{gameId}` (the board; its game strip shows the public link). No extra success screen.

Form fields, top to bottom (one column on every width, max width ~720 px like the public page):

| Field | Control | Default |
|---|---|---|
| Title | `TextField`, max 120 | empty |
| Venue | `TextField`, max 160 | empty |
| Day | `<input type="date">` | today (Vietnam time) |
| Starts / Ends | two `<input type="time">` side by side | 18:00 / 21:00 (the seed's after-work slot) |
| Courts | 4 large buttons `1 2 3 4` (radio group) | 1 |
| Format | 2 large buttons `Doubles` / `Singles` (radio group) | Doubles (DB default) |
| Minimum sets per player | number input 1–20 | 6 (OD-19) |
| Price per person (optional) | "Men" from / to, "Women" from / to, number inputs in VND, `inputmode="numeric"` | empty |
| Preview line | read-only text from `formatGameTime` + `formatVndRange`, e.g. "Fri 27 Nov · 18:00–21:00 · 2 courts · Doubles" | — |
| Submit | `Button` "Create game" | — |

Times: the host types Vietnam time (the app shows every time in `Asia/Ho_Chi_Minh`, `lib/time.ts`); the page sends `<day>T<HH:mm>:00+07:00`. When Ends is earlier than or equal to Starts, the end is on the next day (a game past midnight, e.g. 20:00–00:30); the preview line then shows the end day, so the host sees it (D-2).

States:

- Page: `loading` (sign-in check) → `success` (form); not signed in → redirect; check failed → `ErrorBanner` + retry.
- Mutation: `idle` → `saving` (every input and the button disabled, button text "Creating…") → `success` (navigate) / `error` (`ErrorBanner` with the code's text; form keeps the input). `conflict` cannot happen here (no 409).
- Field errors appear under the field after the first submit attempt and on blur, with `aria-describedby`; focus moves to the first invalid field.

Layout: 320–767 px: one column, time inputs side by side, buttons ≥ 44 px. 768 px+: same column, centered.

Accessibility: real `<form>`, `<label>` per input, radio groups as `<fieldset>` + `<legend>`, keyboard only works, visible focus.

### Copy

All rows are NEW (blueprint has no create screen); each needs approval.

| Key | Text | Source | Approved |
|---|---|---|---|
| `home.createGame` | Create a game | NEW | yes (2026-11-26) |
| `createGame.title` | Create a game | NEW | yes (2026-11-26) |
| `createGame.intro` | Players see the title, venue, time, courts, format and price on the public page. | NEW | yes (2026-11-26) |
| `createGame.titleLabel` | Title | NEW | yes (2026-11-26) |
| `createGame.titleHelp` | For example: Friday after-work doubles | NEW | yes (2026-11-26) |
| `createGame.venueLabel` | Venue | NEW | yes (2026-11-26) |
| `createGame.dayLabel` | Day | NEW | yes (2026-11-26) |
| `createGame.startsLabel` | Starts | NEW | yes (2026-11-26) |
| `createGame.endsLabel` | Ends | NEW | yes (2026-11-26) |
| `createGame.endsNextDay` | Ends the next day | NEW | yes (2026-11-26) |
| `createGame.courtsLabel` | Courts | NEW | yes (2026-11-26) |
| `createGame.formatLabel` | Format | NEW | yes (2026-11-26) |
| (reused) `publicGame.formatDoubles` | Doubles | existing, approved with Public game page | yes |
| (reused) `publicGame.formatSingles` | Singles | existing, approved with Public game page | yes |
| `createGame.minSetsLabel` | Minimum sets per player | NEW | yes (2026-11-26) |
| `createGame.minSetsHelp` | One set is one game to 21 points. | NEW | yes (2026-11-26) |
| `createGame.priceLabel` | Price per person (optional) | NEW | yes (2026-11-26) |
| `createGame.priceHelp` | Shown on the public page only. Players pay you directly. | NEW | yes (2026-11-26) |
| `createGame.priceMen` | Men | NEW | yes (2026-11-26) |
| `createGame.priceWomen` | Women | NEW | yes (2026-11-26) |
| `createGame.priceFrom` | From (VND) | NEW | yes (2026-11-26) |
| `createGame.priceTo` | To (VND) | NEW | yes (2026-11-26) |
| `createGame.submit` | Create game | NEW | yes (2026-11-26) |
| `createGame.saving` | Creating… | NEW | yes (2026-11-26) |
| `createGame.errorRequired` | Fill in this field. | NEW | yes (2026-11-26) |
| `createGame.errorTooLong` | Too long (at most {max} characters). | NEW | yes (2026-11-26) |
| `createGame.errorTimeWindow` | Pick a time that has not ended yet, at most 12 hours long. | NEW (wording follows OD-26) | yes (2026-11-26) |
| `createGame.errorMinSets` | Enter a number from 1 to 20. | NEW | yes (2026-11-26) |
| `createGame.errorPrice` | Enter whole VND from 0 to 10,000,000; "From" is not more than "To". | NEW | yes (2026-11-26) |
| `createGame.errorFailed` | The game was not created. Try again. | NEW | yes (2026-11-26) |

## 8. Security and privacy checklist

- [x] Identity derived from the account session only; `host_user_id` = token `userId`; no host field in the body (§13).
- [x] Guest cookie alone cannot create a game (401), so a guest cannot become a host.
- [x] No ownership check needed (no existing resource); `requireAccount` in the service besides the route rule.
- [x] Response holds only `gameId` and `publicCode` for the new host; no other game's data.
- [x] CSRF required (gateway); no CORS change.
- [x] Logs: the one request log line (method, path, status, ms); title and venue are not logged (they are not personal data, but the rule "no body in logs" stays).
- [x] Public code from `SecureRandom` (existing generator); it opens only the public summary (§11).
- [ ] Abuse: any pilot account can create any number of games. Accepted for a closed pilot (accounts are provisioned); a per-account limit goes to the later backlog if wanted (D-4).

## 9. Test plan

Draft acceptance criteria (replace the backlog draft in `README.md` when approved):

| ID | Criterion |
|---|---|
| AC-01 | A signed-in account creates a game → 201 with `gameId`, `publicCode`; row has `host_user_id` = caller, `state` `OPEN`, all fields as sent, times in UTC |
| AC-02 | The creator opens the new board and sees it in "Games you host"; another account gets 403 on its board; anyone reads its public page |
| AC-03 | Each invalid input → 400 `VALIDATION_FAILED`, no row written |
| AC-04 | Anonymous → 401; guest cookie only → 401; no CSRF → 403; token for another route → 401 |
| AC-05 | A public-code collision retries and still succeeds; 5 collisions → 500, no row |
| AC-06 | Form: defaults, field checks, next-day end, preview line, saving state, error banner, redirect to the board; works at 320 px and 1280 px with keyboard only |

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | host account, full valid body with price → 201, body keys exactly `gameId`, `publicCode`; DB row matches, `state = OPEN`, `next_match_no = 1`, times stored as UTC instants | IT |
| TC-02 | AC-01 | body without `pricePerPerson` → 201, four price columns `NULL`; public page has `pricePerPerson: null` | IT |
| TC-03 | AC-01 | price line with only `minVnd` → stored as min = max | IT |
| TC-04 | AC-01 | body with `hostUserId` of another account → ignored, row host = caller | IT |
| TC-05 | AC-02 | after create: `GET /host/games` lists it; board 200 for creator; board 403 for `player1`; public page 200 | IT (playmatch) |
| TC-06 | AC-03 | one case each: blank title, title 121 chars, blank venue, venue 161, missing `startsAt`, time without offset, `endsAt = startsAt`, `endsAt < startsAt`, window rule (OD-26), `courtCount` 0 and 5, `matchFormat` `MIXED`, `minSets` 0 and 21, price −1, price 10,000,001, `minVnd > maxVnd`, non-JSON body → 400 and `game` row count unchanged | IT |
| TC-07 | AC-04 | `AccessMatrixIT`: no token → 401; guest token → 401; account → 201 | IT (playmatch) |
| TC-08 | AC-04 | `GatewayAccessMatrixIT`: anonymous → 401; guest cookie → 401; account without CSRF → 403 `CSRF_TOKEN_INVALID`; account with CSRF → forwarded with token v2 for `POST /api/v1/games` | IT (gateway) |
| TC-09 | AC-04 | token issued for `POST /api/v1/games/{id}/join` sent to `POST /api/v1/games` → 401 | IT (playmatch) |
| TC-10 | AC-05 | stub generator returns an existing code twice, then a new one → 201 with the third code | IT |
| TC-11 | AC-05 | stub generator always returns an existing code → 500 `INTERNAL_ERROR`, row count unchanged | IT |
| TC-12 | AC-01 | `CreateGameRequest` cross-field rules: next-day end accepted when sent with the right date; window rule edges (OD-26) | unit |
| TC-13 | AC-06 | form helper: day + `20:00` / `00:30` → `endsAt` next day `+07:00`; day + `18:00` / `21:00` → same day | unit (if a frontend test runner exists; else covered by TC-15) |
| TC-14 | AC-01, AC-02 | live in Docker through the gateway on 9090: login as host → CSRF → create → 201; public page by code; board; "Games you host" | live |
| TC-15 | AC-06 | owner: home → Create a game → defaults visible; submit empty → field errors, focus on Title; fill → preview line; 20:00–00:30 shows "Ends the next day"; submit → "Creating…", inputs disabled → lands on the new board; at 320 px and 1280 px; keyboard only | visual (owner) |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| OD-05 | Create screen or API only (§16 has no screen) | (a) API + tests only · (b) small form at `/host/games/new` | **(b)**: §17 says "Host can create a session"; without a form the pilot host needs a tool to post JSON |
| OD-10 | Closing a game (`OPEN → CLOSED`), no §13 route | (a) out of MVP; only seed/DB sets `CLOSED`, services stay read-only for closed games · (b) add a host route now | **(a)** (unchanged from the roadmap); this feature does not add closing |
| OD-26 | Which times may the host pick? Blueprint only says `ends_at > starts_at` | (a) only `endsAt > startsAt` · (b) also: the game has not ended yet (`endsAt > now`) and lasts at most 12 hours · (c) (b) plus start at most 60 days ahead | **(b)**: stops typing mistakes (wrong year, game already over, 30-hour game) without blocking real use |
| D-1 | Write style | (a) `JdbcClient` like the other writes · (b) first JPA entity `Game` (the backlog draft said "JPA entity") | **(a)**: one write style in the service; JPA can come when a feature needs it |
| D-2 | Time input | (a) day + start time + end time; end ≤ start means next day · (b) two date-and-time inputs | **(a)**: fewer taps on a phone; the preview line shows the end day |
| D-3 | Double submit | (a) button disabled while saving, no idempotency key · (b) idempotency key header | **(a)**: a duplicate is rare and visible in "Games you host"; no delete route exists, so a stray duplicate stays (closing/deleting = OD-10 / later) |
| D-4 | Limit on games per account | (a) none in pilot · (b) a daily limit | **(a)**; add to later backlog only if the owner wants it |

Assumptions (correct me if wrong):

- The form defaults (today, 18:00–21:00, 1 court, Doubles, 6 sets) are UI conveniences only; the server has no defaults except `state` and `next_match_no`.
- The host does not join their own game automatically.
- Error texts on the page come from `copy/en.ts`; the API messages stay the approved `ErrorCode` texts.

## 11. Out of scope

- Editing a game, closing it, deleting it → OD-10 / L-10.
- Recurring or copied games → module 2 / later.
- Copy-link or share button on the board → not in the blueprint; the public link is already on the game strip.
- Venue booking, payment → out of MVP (§2).
- Per-field error list in the API error body → would change the API foundation; not proposed.
