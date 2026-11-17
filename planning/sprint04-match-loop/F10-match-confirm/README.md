# F10 — Match confirm + tap-to-swap review

| Field | Value |
|---|---|
| Sprint | S04 Match loop |
| Status | `BACKLOG` |
| Blueprint | §5, §11 (match PLANNED), §12 (application invariants, partial unique indexes), §13 (`POST /host/sessions/{sessionId}/matches`, `409 MATCH_CONFLICT`), §15 (transaction, lock, no partial success), §16 (`/host/sessions/:sessionId/review`, tap-to-swap) |
| Depends on | F09 |
| Waiting for | OD-08 (Vitest for the swap reducer, optional) |
| Suggested branch | `feature/F10-match-confirm` |
| Last updated | 2026-11-17 |

## Goal

The host swaps players across courts or replaces anyone with another Ready player, then confirms a court. The server re-checks everything and either saves exactly one four-player PLANNED match or returns a useful 409 with nothing written. This is the hardest feature of the MVP.

## Scope

- In: `MatchLineup` value object (4 distinct IDs, 2 per team); `MatchService.confirm` in one transaction (AD-07): lock the session row, allocate `next_match_no` through `SessionService`, check host, session `OPEN`, `courtNo ≤ court_count`, court free, four READY attendances of this session; insert `play_match` + 4 `match_player`; attendances → ASSIGNED through `AttendanceService`; unique-index violation → rollback → 409. Review page: `SwapSelector` reducer, `MatchProposal`, Confirm per court, 409 message keeps the host's state.
- Out: start/finish/cancel (F11).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Confirm → 201 `{matchId, matchNo, courtNo, state: "PLANNED"}`; 1 match, 4 `match_player` (2 per team), 4 attendances ASSIGNED, `next_match_no` + 1 | IT |
| AC-02 | Any player not READY / not in this session / court busy / court > `court_count` → 409 `MATCH_CONFLICT`; row counts unchanged | IT |
| AC-03 | Duplicate IDs or not 2 + 2 → 400 | unit (`MatchLineupTest`) + IT |
| AC-04 | Two concurrent confirms sharing a player → exactly one 201, one 409 | IT with two threads |
| AC-05 | Non-host → 403 | `AccessMatrixIT` |
| AC-06 | Tap one player then another swaps them (also across courts and with the Ready pool), works by keyboard; 409 shows "This lineup changed. Refresh suggestions" without losing the page | visual (+ reducer unit test if OD-08 b) |

## Design notes

- `matches/MatchService`, `matches/MatchLineup`, `matches/PlayMatch`, `matches/MatchPlayer`, `matches/HostMatchController`; calls `SessionService.allocateMatchNo` (locks and increments, owning module per AD-01) and `AttendanceService.assign`.
- Frontend: `features/match-review/`, `components/SwapSelector` (pure reducer), `hooks/useMutation` (`idle / saving / success / conflict / error`), `ConfirmDialog` if needed.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-08 | Vitest for the reducer | Open |
