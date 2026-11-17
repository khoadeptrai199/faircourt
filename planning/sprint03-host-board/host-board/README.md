# F06 — Host board

| Field | Value |
|---|---|
| Sprint | S03 Host board |
| Status | `BACKLOG` |
| Blueprint | §3, §6 (host board composition), §11, §13 (`GET /host/sessions/{sessionId}/board`), §15 (AccessPolicy), §16 (`/host/sessions/:sessionId`) |
| Depends on | F03 |
| Waiting for | OD-04 (how the host reaches the board), OD-14 (wait indicator) |
| Suggested branch | `feature/host-board` |
| Last updated | 2026-11-17 |

## Goal

The host of a session sees everyone (Pending, Ready, Assigned, Playing, Resting, Left), each player's private band and band status, wait time and games played, and every court with its current match or "free". Nobody else can.

## Scope

- In: `AccessPolicy.requireHost`; `HostBoardQuery` + `HostBoardResponse`; host page with session strip, court cards, status lists, `PlayerChip`, `WaitIndicator`, sticky "Suggest matches" button (disabled until F09); route to reach the board per OD-04; 403 page "You don't manage this session" with a link to the public page.
- Out: any action on the board (F07, F09–F11, F14).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Host → 200 with all attendance rows incl. guests; `null` band shown to the host as "Unknown / provisional" (§13); courts 1…`court_count`, each free or with its current match and teams | IT |
| AC-02 | Other account → 403; guest → 403; anonymous → 401; unknown session → 404 | `AccessMatrixIT` |
| AC-03 | `HostBoardResponse` is used only by the host controller | review / grep (ArchUnit if approved) |
| AC-04 | Page states: loading, no ready players, fewer than four ready, request failure, 403 | visual |
| AC-05 | Phone: court carousel above status lists; desktop: courts in 2–4 columns beside the ready pool (§16) | visual |

## Design notes

- `identity/AccessPolicy`, `sessions/HostBoardController`, `sessions/HostBoardQuery` (one SQL per section: attendance + profiles; unfinished matches + players).
- Frontend: `features/host-board/`, `components/CourtCard`, `PlayerChip` (host only), `WaitIndicator` (host only), `lib/status.ts` gains attendance and match states.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-04 | How the host reaches the board | Open |
| OD-14 | Wait indicator format | Open |
