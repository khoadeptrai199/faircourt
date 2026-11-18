# F02 design — Public game page + frontend foundation

Status: `APPROVED` (2026-11-18, by owner: "duyệt hết"). Rewritten 2026-11-18 for the rename "session" → "game" (OD-22, F19); router `react-router` 8.5.1 (OD-01); `gameId` in the response (OD-02); Join disabled with "Coming soon" (OD-13).

Added during implementation, after the approval (marked in the tables): `BackHomeLink` component, the `common.pageNotFoundTitle` string and D-1 (both approved by the owner afterwards, 2026-11-18), gateway property `faircourt.services.playmatch-url`.

## 1. Summary

First vertical slice through every layer: browser → Vite proxy → **gateway** → **playmatch-service** → read query → PostgreSQL (`playmatch` schema) → page. The backend part is one read-only endpoint. The frontend part also builds the pieces later pages reuse: router, API client (GET), typed errors, `useAsync`, time and money formatting, copy module and base components.

New compared to the blueprint page (owner decisions 2026-11-17): the page also shows **singles or doubles** (OD-20), the **minimum sets per player** (OD-19) and the **price per person for men and for women, as a range** (OD-18). No payment.

## 2. References

- §3: "View public session page (time, venue, number of courts, basic status) | Anyone: Yes".
- §11: the public code opens a read-only summary with no roster, bands, matches or guest tokens.
- §13: `GET /public/sessions/{publicCode}` (here `/public/games/{publicCode}`, OD-22) | Anyone | public title, venue, localizable times, court count, status; no names or match IDs.
- §16: `/s/:publicCode` (here `/g/:publicCode`, OD-22) | hero with time/venue, court count, one Join button | centered card ~720px | unknown code → friendly 404; closed → read-only.
- §12: display times in `Asia/Ho_Chi_Minh`, store UTC.
- OD-18 (price per person by gender, range, shown only), OD-19 (minimum sets, default 6), OD-20 (singles/doubles per game).

## 3. API contract

| Item | Value |
|---|---|
| Route | `GET /api/v1/public/games/{publicCode}` |
| Path through | gateway route `GET /api/v1/public/games/*` → `playmatch-service` (same path) |
| Caller | anyone |
| Identity source | none |
| Access check | none (GET only) |
| Request | path `publicCode`: trimmed, upper-cased, 1–20 chars `A–Z0–9`; anything else → 404 without a DB query |
| Response | `200 {"gameId":"…uuid…","title":"…","venue":"…","startsAt":"2026-11-17T11:00:00Z","endsAt":"2026-11-17T14:00:00Z","courtCount":2,"state":"OPEN","matchFormat":"DOUBLES","minSets":6,"pricePerPerson":{"male":{"minVnd":75000,"maxVnd":85000},"female":{"minVnd":65000,"maxVnd":75000}}}` |
| Allowed response keys | Top level: `gameId, title, venue, startsAt, endsAt, courtCount, state, matchFormat, minSets, pricePerPerson`. `pricePerPerson`: `male`, `female`, each `minVnd`, `maxVnd`. `pricePerPerson` is `null` when no price is set; `male` or `female` is `null` when that line is not set; when only one bound of a line is set, it is used as both (D-1) |
| Errors | 404 `NOT_FOUND`; 503 `SERVICE_UNAVAILABLE` if playmatch-service is down (gateway) |

The example price is the seed value chosen by the owner (2026-11-17).

## 4. Data and transactions

Read-only, one row, in `playmatch-service`:

```sql
SELECT id, title, venue, starts_at, ends_at, court_count, state, match_format, min_sets,
       price_male_min_vnd, price_male_max_vnd, price_female_min_vnd, price_female_max_vnd
FROM game WHERE public_code = :code
```

The SQL lists only public columns (AD-02). Columns come from F18; no migration here.

## 5. Domain rules

- `state` `OPEN`/`CLOSED` (`GameState`); closed → page read-only (§16). No close route (OD-10); tests set `CLOSED` directly.
- `pricePerPerson` is what one player pays, by gender, as a range set by the host; shown as information only, no payment in the app (OD-18). When min = max the page shows one number. The page never asks a visitor's gender.
- `minSets` is shown as the game's commitment, e.g. "At least 6 sets per player". It is not enforced here.

## 6. Backend components

| File | Service | Notes |
|---|---|---|
| `PublicGameController.java` | playmatch-service | Validates/normalizes the code, calls the query, 404 if empty |
| `PublicGameQuery.java` | playmatch-service | `JdbcClient` read projection → `Optional<PublicGameResponse>` |
| `PublicGameResponse.java` | playmatch-service | Public audience only; never reused |
| `MatchFormat.java`, `GameState.java` | playmatch-service | enums `SINGLES`/`DOUBLES` and `OPEN`/`CLOSED` (same values as the DB checks) |
| `PriceRange.java`, `PricePerPerson.java` | playmatch-service | Records for the nested price object (public response only) |
| service security rule | playmatch-service | `GET /api/v1/public/games/*` permitted |
| gateway route + security line | gateway | `GET /api/v1/public/games/*` → playmatch, `permitAll` for GET only; target from new property `faircourt.services.playmatch-url` (default `http://playmatch-service:8080`, added during implementation) |

## 7. Frontend

### Dependency (OD-01)

`react-router` 8.5.1 (decided 2026-11-18; latest on npm that day, published 2026-10-27), pinned exactly in `package.json`, basic declarative use only (`BrowserRouter`, `Routes`, `Route`, `Link`, `useParams`). README stays empty by owner request, so the version lives in `package.json`.

### Files

| File | Purpose |
|---|---|
| `src/routes/AppRoutes.tsx` | `/` → `HomePage`, `/g/:publicCode` → `PublicGamePage`, `*` → not-found |
| `src/App.tsx` (changed) | Renders the routes |
| `src/api/client.ts` | `apiGet<T>(path, signal?)`; throws `ApiError` on non-2xx |
| `src/api/errors.ts` | `ApiError` with `status`, `code`, `requestId` |
| `src/api/types.ts` | `PublicGame`, `GameState`, `MatchFormat = 'SINGLES' \| 'DOUBLES'`, `ApiErrorBody` |
| `src/api/games.ts` | `getPublicGame(code, signal)` |
| `src/hooks/useAsync.ts` | `loading \| empty \| success \| error`, `reload()`, abort on unmount |
| `src/lib/time.ts` | `formatGameWindow` in `Asia/Ho_Chi_Minh` |
| `src/lib/money.ts` | `formatVnd(n)` with `Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })`; `formatVndRange(min, max)` (one number when equal) |
| `src/lib/status.ts` | `GAME_STATUS`, `MATCH_FORMAT` maps (`Record<…>`) |
| `src/copy/en.ts` | Strings below |
| `src/components/Button.tsx`, `StatusBadge.tsx`, `EmptyState.tsx`, `ErrorBanner.tsx` | Base components |
| `src/features/public-game/PublicGamePage.tsx` | `useAsync(getPublicGame)`, exhaustive `switch` |
| `src/features/public-game/GameHero.tsx` | Title, venue, time, courts, format, minimum sets, price, status, Join |

### States

| State | Shows |
|---|---|
| loading | loading text |
| success, `OPEN` | `GameHero` + Join (per OD-13) |
| success, `CLOSED` | `GameHero` + closed note; no Join |
| 404 | "Game not found" + link home |
| other error (incl. 503) | `ErrorBanner` + Retry |

### Layout

Phone (< 768px): full-width card, 16px padding, Join full width; facts (time, courts, format, minimum sets, price) as a two-column list. Desktop: centered card, max 720px (§16, `tokens.css`).

### Copy

| Key | Text | Source | Approved |
|---|---|---|---|
| `publicGame.join` | Join game | §6 ("Join session"), word changed by OD-22 | yes (2026-11-18) |
| `publicGame.courts` | `{n} courts` / `1 court` | NEW | yes (2026-11-18) |
| `publicGame.formatSingles` | Singles | NEW (OD-20) | yes (2026-11-18) |
| `publicGame.formatDoubles` | Doubles | NEW (OD-20) | yes (2026-11-18) |
| `publicGame.minSets` | At least `{n}` sets per player | NEW (OD-19) | yes (2026-11-18) |
| `publicGame.priceTitle` | Price per person | NEW (OD-18) | yes (2026-11-18) |
| `publicGame.priceMale` | Men: `{range}` | NEW (OD-18) | yes (2026-11-18) |
| `publicGame.priceFemale` | Women: `{range}` | NEW (OD-18) | yes (2026-11-18) |
| `publicGame.priceNote` | Pay the host directly. | NEW (OD-18: payment outside the app) | yes (2026-11-18) |
| `publicGame.statusOpen` | Open | NEW | yes (2026-11-18) |
| `publicGame.statusClosed` | Closed | NEW | yes (2026-11-18) |
| `publicGame.closedNote` | This game is closed. | NEW | yes (2026-11-18) |
| `publicGame.timeZoneNote` | Vietnam time | NEW | yes (2026-11-18) |
| `publicGame.notFoundTitle` | Game not found | NEW (§16 "friendly 404") | yes (2026-11-18) |
| `publicGame.notFoundBody` | Check the link from your host. | NEW | yes (2026-11-18) |
| `common.backHome` | Back to home | NEW | yes (2026-11-18) |
| `common.loading` | Loading… | NEW | yes (2026-11-18) |
| `common.errorTitle` | Something went wrong. | NEW | yes (2026-11-18) |
| `common.retry` | Try again | NEW | yes (2026-11-18) |
| `common.comingSoon` | Coming soon | existing home page copy | yes (2026-11-12) |
| `common.pageNotFoundTitle` | Page not found | NEW (route `*`), added during implementation | yes (2026-11-18, after implementation) |

Time format example (NEW): `Tue 17 Nov · 18:00–21:00`. Price example: `Men: 75.000–85.000 ₫` (exact output of `Intl` checked in the browser).

## 8. Security and privacy checklist

- [x] No identity; the public code never authenticates anyone (§3)
- [x] SQL selects only public columns; allowlist test on the response **as seen through the gateway**
- [x] GET only at gateway and service
- [x] Price, format and minimum sets are game facts, not personal data

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Seeded code → 200; keys ⊆ allowlist (top level and inside `pricePerPerson`); values equal the row (incl. `matchFormat`, `minSets`, price ranges); times end in `Z` | `PublicGameIT` (playmatch) |
| TC-02 | AC-02 | Unknown, 21-char, symbol codes → 404 body | `PublicGameIT` |
| TC-03 | AC-01 | Lowercase code → 200 | `PublicGameIT` |
| TC-04 | AC-04 | `CLOSED` row → `state: "CLOSED"`; all price columns NULL → `pricePerPerson: null`; only the male line set → `female: null` | `PublicGameIT` |
| TC-05 | AC-07 | Gateway: anonymous GET forwarded → 200; anonymous POST same path → 401/403 and not forwarded | `GatewayAccessMatrixIT` with stub |
| TC-06 | AC-08 | Through the running stack: `curl` via gateway port 9090 returns only allowlisted keys | manual command, recorded |
| TC-07 | AC-03, AC-05, AC-06 | Browser 320 / 375 / 1280px: open, closed, not found, playmatch stopped (503 → Retry) | visual or `NOT VERIFIED` |
| TC-08 | AC-07 | Pipeline | pipeline |

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| OD-01 | Router | react-router / in-house | DECIDED 2026-11-18: react-router |
| OD-02 | internal id in this response | a / b / c (ROADMAP §5) | DECIDED 2026-11-18: a (`gameId`) |
| OD-13 | Join before F04 | disabled + "Coming soon" / hidden | DECIDED 2026-11-18: disabled + "Coming soon" |
| — | Copy table incl. format, minimum sets, price lines | approve / edit | APPROVED 2026-11-18 |
| D-1 | Price line with only one bound set (DB allows it) | show that bound as one price / hide the line | Show it as one price. Added during implementation; **approved by owner 2026-11-18** |

## 11. Out of scope

- Join flow → F04, F05
- Payment → later
- Discovery list and map → L-18, L-14
- Bottom navigation → F16
