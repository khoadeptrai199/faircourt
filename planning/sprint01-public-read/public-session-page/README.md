# F02 — Public session page + frontend foundation

| Field | Value |
|---|---|
| Sprint | S01 Services + public read |
| Status | `WAITING_APPROVAL` |
| Blueprint | §3 (permission table row 1), §6 (Public session page), §12 (Asia/Ho_Chi_Minh), §13 (`GET /public/sessions/{publicCode}`), §16 (`/s/:publicCode`); OD-18, OD-19, OD-20 |
| Depends on | F01 |
| Waiting for | OD-01 (router), OD-02 (`sessionId` in the public response), OD-13 (Join button before F04), copy approval, AD-08 |
| Suggested branch | `feature/public-session-page` |
| Last updated | 2026-11-17 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

Anyone with the shared link opens `/s/<code>` and sees the play session's title, venue, time (Vietnam time), courts, singles or doubles, minimum sets per player, the price per person for men and for women (as a range) and the status, with no names or private data. The first real page also sets up the frontend pieces every later page reuses.

## Scope

- In: `GET /api/v1/public/sessions/{publicCode}` in `playmatch-service` with a gateway route; `/s/:publicCode` page with loading / success / not-found / error / closed states; router; `api/client.ts` (GET), `api/types.ts`, `api/sessions.ts`; `useAsync`; `lib/time.ts`, `lib/money.ts`, `lib/status.ts`; `copy/en.ts`; components `Button`, `StatusBadge`, `EmptyState`, `ErrorBanner`, `SessionHero`.
- Out: joining (F04/F05), payment (later), discovery list/map (L-18, L-14), bottom navigation (F16).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Anonymous `GET /api/v1/public/sessions/{code}` → 200 whose JSON keys are exactly the allowlist (incl. `matchFormat`, `minSets`, `pricePerPerson`); values match the row | `PublicSessionIT` + `JsonAllowlist` |
| AC-02 | Unknown or malformed code → 404 `NOT_FOUND`; the page shows a friendly not-found with a link home | IT + visual |
| AC-03 | The page shows title, venue, date and time in Asia/Ho_Chi_Minh, courts, singles/doubles, minimum sets, price (when set) and status as text; no names | visual |
| AC-04 | A `CLOSED` session renders read-only (no Join) | IT + visual |
| AC-05 | Loading and failure states render; failure (incl. 503) offers Retry | visual (stop playmatch-service) |
| AC-06 | Usable at 320px, 375px and desktop | visual (`NOT VERIFIED` if no browser tooling) |
| AC-07 | Gateway forwards only GET for this path; access matrix rows pass; pipeline green | gateway IT + pipeline |
| AC-08 | Through the running stack, the gateway response contains only allowlisted keys | `curl` via port 9090, recorded |

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-01 | Router: `react-router` vs. in-house | Waiting for owner |
| OD-02 | Include `sessionId` in the public summary? | Waiting for owner |
| OD-13 | Join button before F04 ships | Waiting for owner |
| — | New copy strings (design §7), incl. format, minimum sets, price | Waiting for owner |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created; design written, waiting for approval | Planning setup |
| 2026-11-17 | Design rewritten: endpoint in `playmatch-service` behind the gateway; response and page add `matchFormat`, `minSets`, `pricePerPerson` (OD-18…OD-20; price per person by gender as a range, revised 2026-11-17) | Owner-approved rewrite, conversation 2026-11-17 |
