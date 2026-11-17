# F11 — Match start / finish / cancel

| Field | Value |
|---|---|
| Sprint | S04 Match loop |
| Status | `BACKLOG` |
| Blueprint | §11 (match and attendance transitions, finish counters), §12, §13 (`PATCH /host/matches/{matchId}/start\|finish\|cancel`), §15 |
| Depends on | F10 |
| Waiting for | OD-07 (cancel and `available_since`) |
| Suggested branch | `feature/match-lifecycle` |
| Last updated | 2026-11-17 |

## Goal

The host starts a planned match, finishes an active one, or cancels either. Players are released together, counters update, and the next suggestion favors those who waited.

## Scope

- In: start PLANNED → ACTIVE (`started_at`), attendances ASSIGNED → PLAYING; finish ACTIVE → FINISHED (`finished_at`), `released_at` on all four, attendances → READY, `games_played + 1`, `available_since = now`; cancel PLANNED/ACTIVE → CANCELLED, release, attendances → READY (no game counted); each in one transaction with the match row locked; court-card actions with confirmation for finish/cancel.
- Out: scores (§11: none in MVP).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Each transition updates match, 4 `match_player` and 4 attendance rows together; illegal transition → 409 `STATE_CONFLICT`, nothing changed | IT |
| AC-02 | After finish, the next suggestion picks players who sat out first (§17 "Second round accounts for time spent waiting") | IT |
| AC-03 | After finish or cancel, the court and the four players can be assigned again | IT |
| AC-04 | Match in another host's session → 403; unknown match → 404 | `AccessMatrixIT` |
| AC-05 | UI shows Assigned and Playing separately (§11) | visual |

## Design notes

- `MatchService.start/finish/cancel`; session resolved from the match before `AccessPolicy` (§15).
- `MatchState` transition enum (AD-03).

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-07 | `available_since` on cancel | Open |
