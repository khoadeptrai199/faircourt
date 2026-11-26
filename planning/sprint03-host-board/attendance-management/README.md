# F07 — Attendance management (approve, rest, leave)

| Field | Value |
|---|---|
| Sprint | S03 Host board |
| Status | `BACKLOG` |
| Blueprint | §3, §11 (attendance state machine), §13 (`PATCH /host/sessions/{sessionId}/attendance/{attendanceId}`), §15 |
| Depends on | F06 |
| Waiting for | OD-07 (`available_since` rules), OD-15 (reject pending guest), identity token scope (open decisions) |
| Suggested branch | `feature/attendance-management` |
| Last updated | 2026-11-17 |

## Goal

The host approves a pending guest and moves players between Ready, Resting and Left, with the §11 rules enforced on the server.

## Scope

- In: `AttendanceState` transition table (AD-03); PATCH body with the target state; `AttendanceService` with `requireHost`; illegal transitions → 409 `STATE_CONFLICT`; Assigned/Playing cannot go Left (host must cancel or finish first, §11); board actions with saving state.
- Out: approving into a match, swaps (F10).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | `PENDING → READY` (approve) | IT |
| AC-02 | `READY ↔ RESTING`, `READY → LEFT` succeed; every other transition → 409 with no change | unit (enum) + IT |
| AC-03 | `available_since` follows OD-07 | IT |
| AC-04 | Non-host → 403; attendance of another session → 404 | `AccessMatrixIT` |
| AC-05 | Board reflects the change after the call; buttons disabled while saving | visual |

## Design notes

- `players/AttendanceState`, `players/Attendance` (JPA entity, first write entity in `players`), `players/HostAttendanceController`.
- The attendance ID in the path identifies the target, not the caller; identity still comes from the principal (§13).

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-07 | Keep or reset `available_since` on approve / resting → ready | Open |
| OD-15 | Allow `PENDING → LEFT` | Open |
| — | Should marking a guest `LEFT` revoke their `guest_access`? (§12 silent) | Decide in design, ask owner |
| — | Identity token scope before the first host write route (F06 review.md #17, owner 2026-11-26): bind `X-Internal-Identity` to an audience (target service) and method + path, or accept the 30 s replay window inside the network as an AD | Decide in design, ask owner (recommendation from F06 review: add the claims) |
