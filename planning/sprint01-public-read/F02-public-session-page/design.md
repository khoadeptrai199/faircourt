# F02 design — Public session page + frontend foundation

Status: `WAITING_APPROVAL` (rewritten 2026-11-17 for microservices and OD-18…OD-20). Blocked by OD-01, OD-02, OD-13 and copy approval.

## 1. Summary

First vertical slice through every layer: browser → Vite proxy → **gateway** → **playmatch-service** → read query → PostgreSQL (`playmatch` schema) → page. The backend part is one read-only endpoint. The frontend part also builds the pieces later pages reuse: router, API client (GET), typed errors, `useAsync`, time and money formatting, copy module and base components.

New compared to the blueprint page (owner decisions 2026-11-17): the page also shows **singles or doubles** (OD-20), the **minimum sets per player** (OD-19) and the **price per person for men and for women, as a range** (OD-18). No payment.

## 2. References

- §3: "View public session page (time, venue, number of courts, basic status) | Anyone: Yes".
- §11: the public code opens a read-only summary with no roster, bands, matches or guest tokens.
- §13: `GET /public/sessions/{publicCode}` | Anyone | public title, venue, localizable times, court count, status; no names or match IDs.
- §16: `/s/:publicCode` | hero with time/venue, court count, one Join button | centered card ~720px | unknown code → friendly 404; closed → read-only.
- §12: display times in `Asia/Ho_Chi_Minh`, store UTC.
- OD-18 (price per person by gender, range, shown only), OD-19 (minimum sets, default 6), OD-20 (singles/doubles per session).

## 3. API contract

| Item | Value |
|---|---|
| Route | `GET /api/v1/public/sessions/{publicCode}` |
| Path through | gateway route `GET /api/v1/public/sessions/*` → `playmatch-service` (same path) |
| Caller | anyone |
| Identity source | none |
| Access check | none (GET only) |
| Request | path `publicCode`: trimmed, upper-cased, 1–20 chars `A–Z0–9`; anything else → 404 without a DB query |
| Response | `200 {"title":"…","venue":"…","startsAt":"2026-11-17T11:00:00Z","endsAt":"2026-11-17T14:00:00Z","courtCount":2,"state":"OPEN","matchFormat":"DOUBLES","minSets":6,"pricePerPerson":{"male":{"minVnd":75000,"maxVnd":85000},"female":{"minVnd":65000,"maxVnd":75000}}}` |
| Allowed response keys | Top level: `title, venue, startsAt, endsAt, courtCount, state, matchFormat, minSets, pricePerPerson` (+ `sessionId` only if OD-02 option a is approved). `pricePerPerson`: `male`, `female`, each `minVnd`, `maxVnd`. `pricePerPerson` is `null` when no price is set; `male` or `female` is `null` when that line is not set |
| Errors | 404 `NOT_FOUND`; 503 `SERVICE_UNAVAILABLE` if playmatch-service is down (gateway) |

The example price is the seed value chosen by the owner (2026-11-17).

## 4. Data and transactions

Read-only, one row, in `playmatch-service`:

```sql
SELECT title, venue, starts_at, ends_at, court_count, state, match_format, min_sets,
       price_male_min_vnd, price_male_max_vnd, price_female_min_vnd, price_female_max_vnd
FROM play_session WHERE public_code = :code
```

The SQL lists only public columns (AD-02). Columns come from F18; no migration here.

## 5. Domain rules

- `state` `OPEN`/`CLOSED`; closed → page read-only (§16). No close route (OD-10); tests set `CLOSED` directly.
- `pricePerPerson` is what one player pays, by gender, as a range set by the host; shown as information only, no payment in the app (OD-18). When min = max the page shows one number. The page never asks a visitor's gender.
- `minSets` is shown as the session's commitment, e.g. "At least 6 sets per player". It is not enforced here.

## 6. Backend components

| File | Service | Notes |
|---|---|---|
| `PublicSessionController.java` | playmatch-service | Validates/normalizes the code, calls the query, 404 if empty |
| `PublicSessionQuery.java` | playmatch-service | `JdbcClient` read projection → `Optional<PublicSessionResponse>` |
| `PublicSessionResponse.java` | playmatch-service | Public audience only; never reused |
| `MatchFormat.java` | playmatch-service | enum `SINGLES`, `DOUBLES` (same values as the DB check) |
| `PriceRange.java`, `PricePerPerson.java` | playmatch-service | Records for the nested price object (public response only) |
| service security rule | playmatch-service | `GET /api/v1/public/sessions/*` permitted |
| gateway route + security line | gateway | `GET /api/v1/public/sessions/*` → playmatch, `permitAll` for GET only |

## 7. Frontend

### Dependency (OD-01)

Recommended `react-router`, exact version from the npm registry at implementation time, pinned, README version table. If OD-01 chooses in-house, `routes/` holds a small matcher and nothing else changes.

### Files

| File | Purpose |
|---|---|
| `src/routes/AppRoutes.tsx` | `/` → `HomePage`, `/s/:publicCode` → `PublicSessionPage`, `*` → not-found |
| `src/App.tsx` (changed) | Renders the routes |
| `src/api/client.ts` | `apiGet<T>(path, signal?)`; throws `ApiError` on non-2xx |
| `src/api/errors.ts` | `ApiError` with `status`, `code`, `requestId` |
| `src/api/types.ts` | `PublicSession`, `SessionState`, `MatchFormat = 'SINGLES' \| 'DOUBLES'`, `ApiErrorBody` |
| `src/api/sessions.ts` | `getPublicSession(code, signal)` |
| `src/hooks/useAsync.ts` | `loading \| empty \| success \| error`, `reload()`, abort on unmount |
| `src/lib/time.ts` | `formatSessionWindow` in `Asia/Ho_Chi_Minh` |
| `src/lib/money.ts` | `formatVnd(n)` with `Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })`; `formatVndRange(min, max)` (one number when equal) |
| `src/lib/status.ts` | `SESSION_STATUS`, `MATCH_FORMAT` maps (`Record<…>`) |
| `src/copy/en.ts` | Strings below |
| `src/components/Button.tsx`, `StatusBadge.tsx`, `EmptyState.tsx`, `ErrorBanner.tsx` | Base components |
| `src/features/public-session/PublicSessionPage.tsx` | `useAsync(getPublicSession)`, exhaustive `switch` |
| `src/features/public-session/SessionHero.tsx` | Title, venue, time, courts, format, minimum sets, price, status, Join |

### States

| State | Shows |
|---|---|
| loading | loading text |
| success, `OPEN` | `SessionHero` + Join (per OD-13) |
| success, `CLOSED` | `SessionHero` + closed note; no Join |
| 404 | "Session not found" + link home |
| other error (incl. 503) | `ErrorBanner` + Retry |

### Layout

Phone (< 768px): full-width card, 16px padding, Join full width; facts (time, courts, format, minimum sets, price) as a two-column list. Desktop: centered card, max 720px (§16, `tokens.css`).

### Copy

| Key | Text | Source | Approved |
|---|---|---|---|
| `publicSession.join` | Join session | §6 | yes (blueprint) |
| `publicSession.courts` | `{n} courts` / `1 court` | NEW | |
| `publicSession.formatSingles` | Singles | NEW (OD-20) | |
| `publicSession.formatDoubles` | Doubles | NEW (OD-20) | |
| `publicSession.minSets` | At least `{n}` sets per player | NEW (OD-19) | |
| `publicSession.priceTitle` | Price per person | NEW (OD-18) | |
| `publicSession.priceMale` | Men: `{range}` | NEW (OD-18) | |
| `publicSession.priceFemale` | Women: `{range}` | NEW (OD-18) | |
| `publicSession.priceNote` | Pay the host directly. | NEW (OD-18: payment outside the app) — wording to confirm | |
| `publicSession.statusOpen` | Open | NEW | |
| `publicSession.statusClosed` | Closed | NEW | |
| `publicSession.closedNote` | This session is closed. | NEW | |
| `publicSession.timeZoneNote` | Vietnam time | NEW | |
| `publicSession.notFoundTitle` | Session not found | NEW (§16 "friendly 404") | |
| `publicSession.notFoundBody` | Check the link from your host. | NEW | |
| `common.backHome` | Back to home | NEW | |
| `common.loading` | Loading… | NEW | |
| `common.errorTitle` | Something went wrong. | NEW | |
| `common.retry` | Try again | NEW | |
| `common.comingSoon` | Coming soon | existing home page copy | yes (2026-11-12) |

Time format example (NEW): `Tue 17 Nov · 18:00–21:00`. Price example: `Men: 75.000–85.000 ₫` (exact output of `Intl` checked in the browser).

## 8. Security and privacy checklist

- [x] No identity; the public code never authenticates anyone (§3)
- [x] SQL selects only public columns; allowlist test on the response **as seen through the gateway**
- [x] GET only at gateway and service
- [x] Price, format and minimum sets are session facts, not personal data

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Seeded code → 200; keys ⊆ allowlist (top level and inside `pricePerPerson`); values equal the row (incl. `matchFormat`, `minSets`, price ranges); times end in `Z` | `PublicSessionIT` (playmatch) |
| TC-02 | AC-02 | Unknown, 21-char, symbol codes → 404 body | `PublicSessionIT` |
| TC-03 | AC-01 | Lowercase code → 200 | `PublicSessionIT` |
| TC-04 | AC-04 | `CLOSED` row → `state: "CLOSED"`; all price columns NULL → `pricePerPerson: null`; only the male line set → `female: null` | `PublicSessionIT` |
| TC-05 | AC-07 | Gateway: anonymous GET forwarded → 200; anonymous POST same path → 401/403 and not forwarded | `GatewayAccessMatrixIT` with stub |
| TC-06 | AC-08 | Through the running stack: `curl` via gateway port 9090 returns only allowlisted keys | manual command, recorded |
| TC-07 | AC-03, AC-05, AC-06 | Browser 320 / 375 / 1280px: open, closed, not found, playmatch stopped (503 → Retry) | visual or `NOT VERIFIED` |
| TC-08 | AC-07 | Pipeline | pipeline |

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| OD-01 | Router | react-router / in-house | react-router |
| OD-02 | `sessionId` in this response | a / b / c (ROADMAP §5) | a |
| OD-13 | Join before F04 | disabled + "Coming soon" / hidden | disabled + "Coming soon" |
| — | Copy table incl. format, minimum sets, price lines | approve / edit | — |

## 11. Out of scope

- Join flow → F04, F05
- Payment → later
- Discovery list and map → L-18, L-14
- Bottom navigation → F16
