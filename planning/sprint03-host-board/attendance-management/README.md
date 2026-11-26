# F07 — Attendance management (approve, left)

| Field | Value |
|---|---|
| Sprint | S03 Host board |
| Status | `IN_REVIEW` |
| Blueprint | §0 step 3, §3, §11 (attendance state machine), §12, §13 (`PATCH /host/sessions/{sessionId}/attendance/{attendanceId}`), §15, §16 |
| Depends on | F06 |
| Waiting for | Gate 3: owner visual check (TC-19, TC-20) + commit; keep or remove the live-test guest row |
| Suggested branch | `feature/attendance-management` |
| Last updated | 2026-11-26 |

Docs: [design.md](design.md) · [implement.md](implement.md) · [review.md](review.md)

## Goal

The host approves a pending guest and marks players as left, with the state rules enforced on the server. Players never change their own status.

## Scope

- In: `AttendanceState` host transition table (AD-03); `PATCH /api/v1/host/games/{gameId}/attendance/{attendanceId}` with target `READY` or `LEFT`; `AttendanceService` with `requireHost` and row locks; illegal transitions → 409 `STATE_CONFLICT`; Assigned/Playing cannot change here (host must cancel or finish first, §11); identity token v2 bound to service + method + path; board buttons Approve / Reject / Mark as left with confirm dialog and saving state.
- Out: Rest button (`READY ↔ RESTING`, owner 2026-11-26); approving into a match, swaps (F10); cancel/finish (F11); level changes (F14); closing a game (L-10); undo of left.

## Acceptance criteria (approved with the design, 2026-11-26)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Host approves a `PENDING` guest → `READY`; `available_since` kept; answer has only `attendanceId`, `state`, `availableSince` | IT + allowlist + live |
| AC-02 | `PENDING → LEFT` and `READY → LEFT` succeed; same state → 200 no change | unit (enum) + IT |
| AC-03 | Every other transition (incl. `LEFT → READY`), a player in a match, or a closed game → 409 with no row change | IT |
| AC-04 | Non-host → 403; attendance of another game → 404; anonymous / guest only → 401; no CSRF → 403; invalid body (incl. `RESTING`) → 400 | `AccessMatrixIT`, `GatewayAccessMatrixIT` |
| AC-05 | An identity token issued for another route, method or service is refused (401); existing routes keep working with token v2 | unit + IT |
| AC-06 | Board shows actions per state, confirm before Reject / Mark as left, all buttons disabled while saving, board re-reads after success or 409 | visual (owner) |
| AC-07 | A guest marked `LEFT` sees "You left this game."; a member marked `LEFT` cannot join again | IT |

## Design notes

See [design.md](design.md). Write style `JdbcClient` + enum rule (D-1); the attendance ID in the path identifies the target, not the caller (§13).

## Open decisions

| ID | Question | Status |
|---|---|---|
| — | Manual host buttons | Decided 2026-11-26: Approve + Left (no Rest) |
| OD-07 | `available_since` on approve (cancel part: F11) | Decided 2026-11-26: keep |
| OD-15 | Allow `PENDING → LEFT` (reject) | Decided 2026-11-26: allow |
| OD-23 | Allow `RESTING → LEFT` | Moot (no Rest button) |
| OD-24 | Allow undo `LEFT → READY` | Decided 2026-11-26: no, final |
| OD-25 | Drop the `ASSIGNED` step in the match flow | Recorded for Match confirm / Match lifecycle |
| D-1 | `JdbcClient` vs first JPA entity | Decided 2026-11-26: `JdbcClient` |
| D-2 | Same state → 200 no change vs 409 | Decided 2026-11-26: 200 no change |
| D-3 | Revoke guest cookie when marked `LEFT` | Decided 2026-11-26: keep cookie |
| D-4 | Identity token scope (Host board review #17) | Decided 2026-11-26: token v2 with `aud` + method + path |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-26 | Design written (`DESIGN → WAITING_APPROVAL`); OD-23, OD-24 added | Claude |
| 2026-11-26 | Owner answers: players never change their own status; host buttons Approve + Left only, no Rest (deviation from §11 `READY ↔ RESTING`); `available_since` kept on approve; no undo of left; guest cookie kept when left; identity token bound to service + method + path; "no Assigned step" question recorded as OD-25 for the match features | Owner, this conversation |
| 2026-11-26 | Gate 1 (spec) approved by the owner: design.md with all recommendations (OD-15 allow, D-1 JdbcClient, D-2 same state → 200) and the copy table; `WAITING_APPROVAL → APPROVED` | Owner ("duyệt hết như đề xuất") |
| 2026-11-26 | Gate 2 (plan) approved by the owner: tasks T01–T11 and test cases TC-01–TC-22 in implement.md; `APPROVED → IN_PROGRESS` | Owner ("duyệt gate 2, làm tiếp đi") |
| 2026-11-26 | Build loop done: tasks T01–T10 done, T11 live check PASS (visual pending); pre-commit PIPELINE OK; reviews: scope-guardian, privacy-reviewer, flow-reviewer 85 → fix round 1 → 98 APPROVE; `IN_PROGRESS → IN_REVIEW` | Claude |
