# F05 — Own attendance status + member join

| Field | Value |
|---|---|
| Sprint | S02 Identity |
| Status | `BACKLOG` |
| Blueprint | §3, §11 (regular player joins once, enters READY), §13 (`POST /sessions/{sessionId}/join`, `GET /sessions/{sessionId}/me`), §16 (`/my/sessions/:sessionId`) |
| Depends on | F03, F04 |
| Waiting for | Nothing blocking (OD-02 and OD-11 decided 2026-11-18); owner has not asked to start it yet |
| Suggested branch | `feature/my-attendance-status` |
| Last updated | 2026-11-17 |

## Goal

A signed-in member joins a session from its public page; a member or guest opens "My game" and sees only their own attendance state. The assignment part stays `null` until F12.

## Scope

- In: `CallerIdentity` argument resolver (account wins if both cookies exist); member join (idempotent: joining again returns the existing attendance); `GET /me` returning `{"attendanceState": …, "assignment": null}`; `/my/sessions/:sessionId` page states: pending, ready/no match, expired guest access, not joined; public page Join for a signed-in member.
- Out: assignment content (F12), feedback (F13).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Guest reads own `PENDING`; member reads own `READY` | IT |
| AC-02 | Anonymous → 401; guest of another session → 404; signed-in account that has not joined → 404 | `AccessMatrixIT` |
| AC-03 | `/me` JSON keys ⊆ `{attendanceState, assignment}` | `JsonAllowlist` |
| AC-04 | Member joins twice → same attendance, state `READY` | IT |
| AC-05 | Join a `CLOSED` session → 409 `STATE_CONFLICT` | IT |
| AC-06 | Expired guest cookie → 401 `GUEST_ACCESS_EXPIRED`; page shows "Check in again and tell the host to remove your old entry." (§16) | IT + visual |

## Design notes

- `players/AttendanceController` (`/sessions/{id}/join`, `/sessions/{id}/me`), `players/MemberJoinService`, `players/MyAttendanceQuery` (selects only the caller's own row).
- Member identity → `player_profile.user_id`; guest identity → `guest_access.attendance_id` with session check.
- Second join answer code: 200 with the existing attendance (vs. 201 the first time) — decide in design.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-02 | How the page learns `sessionId` | DECIDED 2026-11-18: public summary returns the opaque id (a), confirmed again by the owner |
| OD-11 | Can the host join as a player? | DECIDED 2026-11-18: yes, the host can play in their own game (owner) |
| 2026-11-18 | Owner answered the open questions: host plays as a player in their own game (OD-11 = b, not the recommended a); `gameId` in the public summary confirmed (OD-02). Owner: do not start this feature yet | Owner, conversation 2026-11-18 |
