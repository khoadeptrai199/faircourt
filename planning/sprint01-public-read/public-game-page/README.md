# F02 — Public game page + frontend foundation

| Field | Value |
|---|---|
| Sprint | S01 Services + public read |
| Status | `DONE` |
| Blueprint | §3 (permission table row 1), §6 (Public session page), §12 (Asia/Ho_Chi_Minh), §13 (`GET /public/sessions/{publicCode}`, here `/public/games/...`), §16 (`/s/:publicCode`, here `/g/:publicCode`); OD-01, OD-02, OD-13, OD-18…OD-20, OD-22 |
| Depends on | F01, F19 (Game rename) |
| Waiting for | Visual check at 320/375/1280px by the owner (AC-06 `NOT VERIFIED`) |
| Branch | `feature/public-game-page` |
| Last updated | 2026-11-18 |

Docs: [design.md](design.md) · [implement.md](implement.md) · [review.md](review.md)

## Goal

Anyone with the shared link opens `/g/<code>` and sees the game's title, venue, time (Vietnam time), courts, singles or doubles, minimum sets per player, the price per person for men and for women (as a range) and the status, with no names or private data. The first real page also sets up the frontend pieces every later page reuses.

## Scope

- In: `GET /api/v1/public/games/{publicCode}` in `playmatch-service` with a gateway route; `/g/:publicCode` page with loading / success / not-found / error / closed states; router; `api/client.ts` (GET), `api/errors.ts`, `api/types.ts`, `api/games.ts`; `useAsync`; `lib/time.ts`, `lib/money.ts`, `lib/status.ts`; `copy/en.ts`; components `Button`, `StatusBadge`, `EmptyState`, `ErrorBanner`, `BackHomeLink`, `GameHero`.
- Out: joining (F04/F05), payment (later), discovery list/map (L-18, L-14), bottom navigation (F16).

## Acceptance criteria

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Anonymous `GET /api/v1/public/games/{code}` → 200 whose JSON keys are exactly the allowlist (incl. `gameId`, `matchFormat`, `minSets`, `pricePerPerson`); values match the row | `PublicGameIT` + `JsonAllowlist` |
| AC-02 | Unknown or malformed code → 404 `NOT_FOUND`; the page shows a friendly not-found with a link home | IT + visual |
| AC-03 | The page shows title, venue, date and time in Asia/Ho_Chi_Minh, courts, singles/doubles, minimum sets, price (when set) and status as text; no names | visual |
| AC-04 | A `CLOSED` game renders read-only (no Join) | IT + visual |
| AC-05 | Loading and failure states render; failure (incl. 503) offers Retry | visual (stop playmatch-service) |
| AC-06 | Usable at 320px, 375px and desktop | visual (`NOT VERIFIED` if no browser tooling) |
| AC-07 | Gateway forwards only GET for this path; access matrix rows pass; pipeline green | gateway IT + pipeline |
| AC-08 | Through the running stack, the gateway response contains only allowlisted keys | `curl` via port 9090, recorded |

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-01 | Router: `react-router` vs. in-house | DECIDED 2026-11-18: `react-router` 8.5.1 |
| OD-02 | Include the internal id in the public summary? | DECIDED 2026-11-18: yes (`gameId`) |
| OD-13 | Join button before F04 ships | DECIDED 2026-11-18: visible, disabled, "Coming soon" |
| OD-22 | Word that replaces "session" | DECIDED 2026-11-18: "game" (done in F19) |
| — | New copy strings (design §7) | APPROVED 2026-11-18 ("duyệt hết"), with "session" → "game" |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created; design written, waiting for approval | Planning setup |
| 2026-11-17 | Design rewritten: endpoint in `playmatch-service` behind the gateway; response and page add `matchFormat`, `minSets`, `pricePerPerson` (OD-18…OD-20; price per person by gender as a range, revised 2026-11-17) | Owner-approved rewrite, conversation 2026-11-17 |
| 2026-11-18 | Join button before guest check-in: visible but disabled with "Coming soon". Owner asked to replace the word "session" (API URLs, class names, copy) with a better word | Owner, conversation 2026-11-18 |
| 2026-11-18 | Router = `react-router` (OD-01); public summary returns the internal id (OD-02); replacement word = "game" (OD-22), a 21-point contest stays "match" in code and "set" in copy | Owner, conversation 2026-11-18 |
| 2026-11-18 | **APPROVED**: whole design incl. copy table and the rename plan; implement on a new branch ("duyệt hết, triển khai implement đi. nhớ checkout branch mới đừng làm trên main"). Folder renamed `public-session-page` → `public-game-page` | Owner, conversation 2026-11-18 |
| 2026-11-18 | Implemented; status `IN_REVIEW` (review agents not run yet) | implement.md |
| 2026-11-18 | Review: no open high/medium finding (low findings fixed; "Page not found" and one-bound price approved by the owner). Pipeline green. Owner: "mọi thứ ok thì build + test + commit + push" → `DONE`, committed and pushed on `feature/public-game-page` | review.md, owner |
