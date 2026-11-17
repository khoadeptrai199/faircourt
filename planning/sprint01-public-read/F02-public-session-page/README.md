# F02 — Public session page + frontend foundation

| Field | Value |
|---|---|
| Sprint | S01 Public read |
| Status | `WAITING_APPROVAL` |
| Blueprint | §3 (permission table row 1), §6 (Public session page), §12 (display in Asia/Ho_Chi_Minh), §13 (`GET /public/sessions/{publicCode}`), §16 (`/s/:publicCode`) |
| Depends on | F01 |
| Waiting for | OD-01 (router), OD-02 (`sessionId` in the public response), OD-13 (Join button before F04), copy approval, AD-08 |
| Suggested branch | `feature/F02-public-session-page` |
| Last updated | 2026-11-17 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

Anyone with the shared link opens `/s/<code>` and sees tonight's title, venue, time window (Vietnam time), number of courts and status, with no names or private data. The first real page also sets up the frontend pieces every later page reuses.

## Scope

- In: `GET /api/v1/public/sessions/{publicCode}`; `/s/:publicCode` page with loading / success / not-found / error / closed states; router; `api/client.ts` (GET), `api/types.ts`, `api/sessions.ts`; `useAsync`; `lib/time.ts`; `copy/en.ts`; components `Button`, `StatusBadge`, `EmptyState`, `ErrorBanner`, `SessionHero`.
- Out: joining (F04/F05), write path of the API client (F04), bottom navigation (F16).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Anonymous `GET /api/v1/public/sessions/{code}` → 200 whose JSON keys are exactly the allowlist; values match the session row | `PublicSessionIT` + `JsonAllowlist` |
| AC-02 | Unknown or malformed code → 404 `NOT_FOUND` error body; the page shows a friendly not-found with a link home | IT + visual |
| AC-03 | The page shows title, venue, date and time window in Asia/Ho_Chi_Minh, court count and status as text; no names | visual |
| AC-04 | A `CLOSED` session renders read-only (no Join action) | IT (state value) + visual |
| AC-05 | Loading and request-failure states render; failure offers Retry | visual (stop backend) |
| AC-06 | Usable at 320px, 375px and desktop | visual (`NOT VERIFIED` if no browser tooling) |
| AC-07 | `AccessMatrixIT` row: anonymous → 200; pipeline green | test run |

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-01 | Router: `react-router` vs. in-house | Waiting for owner |
| OD-02 | Include `sessionId` in the public summary? | Waiting for owner |
| OD-13 | Join button before F04 ships | Waiting for owner |
| — | New copy strings (design §7) | Waiting for owner |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created; design written, waiting for approval | Planning setup |
