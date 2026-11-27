# F11 — Match start / finish / cancel

| Field | Value |
|---|---|
| Sprint | S04 Match loop |
| Status | `IN_REVIEW` |
| Blueprint | §11 (match and attendance transitions, finish counters), §12, §13 (`PATCH /host/matches/{matchId}/start\|finish\|cancel`), §15 |
| Depends on | F10 |
| Waiting for | Gate 3: owner reviews the result ([review.md](review.md)), runs TC-20 and TC-21, and commits |
| Suggested branch | `feature/match-lifecycle` |
| Last updated | 2026-11-27 |

## Goal

The host starts a planned match, finishes an active one, or cancels either. Players are released together, counters update, and the next suggestion favors those who waited.

## Scope

- In: start PLANNED → ACTIVE (`started_at`), attendances ASSIGNED → PLAYING; finish ACTIVE → FINISHED (`finished_at`), `released_at` on all four, attendances → READY, `games_played + 1`, `available_since = now`; cancel PLANNED/ACTIVE → CANCELLED, release, attendances → READY (no game counted); each in one transaction with the match row locked; court-card actions with confirmation for finish/cancel.
- Out: scores (§11: none in MVP).

## Acceptance criteria (draft, superseded)

> The approved criteria are AC-01…AC-09 in [design.md](design.md) §9. The table below is the earlier draft; its numbers do not match the design.

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
| OD-07 | `available_since` on cancel | **Decided 2026-11-27: keep** (owner, design D-1) |

## Decision log

- 2026-11-27 — Gate 1 (spec) approved by the owner: whole design as recommended (D-1 keep `available_since` on cancel = OD-07 cancel part; D-2 Finish and Cancel ask, Start does not; D-3 closed game 409 + buttons hidden; D-4 cancel keeps `started_at`; D-5 repeat → 409; copy table). Status `APPROVED`.
- 2026-11-27 — Build loop finished: backend and frontend built, fresh-session review 94/100 after one fix round, scope and privacy reviews clean. `scripts/precommit.sh` as one run NOT VERIFIED (WSL VM crash); checks run piece by piece, all PASS. Status `IN_REVIEW`; waiting for Gate 3.
- 2026-11-27 — Gate 2 (plan) approved by the owner: tasks T00–T09 and test cases TC-01…TC-21 as written, including agent-derived TC-10 and TC-16. Status `IN_PROGRESS`.
- 2026-11-27 — Plan and test cases written ([implement.md](implement.md): T00–T09, TC-01…TC-21); waiting for Gate 2.
- 2026-11-27 — Design written (`design.md`), status `WAITING_APPROVAL`; open: D-1 (OD-07 cancel part), D-2 confirm dialogs, D-3 closed game, D-4 `started_at` on cancel, D-5 repeated action, NEW copy.
