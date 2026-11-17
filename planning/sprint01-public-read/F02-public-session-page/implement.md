# F02 implementation — Public session page + frontend foundation

Design: [design.md](design.md) — status `WAITING_APPROVAL`. No task starts before approval and before F01 is `DONE`.

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01, AC-02 | `PublicSessionResponse`, `PublicSessionQuery`, `PublicSessionController`; route line in `SecurityConfig` | `TODO` |
| T02 | AC-01…AC-04, AC-07 | `PublicSessionIT` (TC-01…TC-04) + `AccessMatrixIT` rows (TC-05) | `TODO` |
| T03 | — | Add router per OD-01 (version from npm registry, pinned; lockfile generated inside the Node container; README version table) | `TODO` |
| T04 | — | `api/client.ts`, `api/errors.ts`, `api/types.ts`, `api/sessions.ts` | `TODO` |
| T05 | AC-05 | `hooks/useAsync.ts` | `TODO` |
| T06 | AC-03 | `lib/time.ts`, `lib/status.ts`, `copy/en.ts` (approved strings only) | `TODO` |
| T07 | AC-03, AC-05 | Components `Button`, `StatusBadge`, `EmptyState`, `ErrorBanner` | `TODO` |
| T08 | AC-02…AC-06 | `routes/AppRoutes.tsx`, `App.tsx`, `features/public-session/` | `TODO` |
| T09 | AC-06 | Visual check at 320 / 375 / 1280px (record or mark `NOT VERIFIED`) | `TODO` |
| T10 | AC-07 | README + `API Document/backend.md` / `frontend.md` status; pipeline run | `TODO` |

## Files

| File | Added / changed | Why |
|---|---|---|

Deleted files: none.

## Commands executed

| Date | Machine | Command | Result |
|---|---|---|---|

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `PublicSessionIT` | | |
| AC-02 | `PublicSessionIT` + visual | | |
| AC-03 | visual | | |
| AC-04 | `PublicSessionIT` + visual | | |
| AC-05 | visual | | |
| AC-06 | visual | | |
| AC-07 | `AccessMatrixIT` + pipeline | | |

## Deviations from the design

- none yet
