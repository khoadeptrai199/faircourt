# F05 — Own attendance status + member join

| Field | Value |
|---|---|
| Sprint | S02 Identity |
| Status | `DONE` |
| Blueprint | §3, §11 (regular player joins once, enters READY), §13 (`POST /sessions/{sessionId}/join`, `GET /sessions/{sessionId}/me`), §16 (`/my/sessions/:sessionId`) |
| Depends on | F03, F04 |
| Waiting for | — |
| Suggested branch | `feature/my-attendance-status` |
| Last updated | 2026-11-25 |

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

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-18 | Owner answered the open questions: host plays as a player in their own game (OD-11 = b, not the recommended a); `gameId` in the public summary confirmed (OD-02). Owner: do not start this feature yet | Owner, conversation 2026-11-18 |
| 2026-11-20 | Owner asked to design it; `design.md` written (2 public + 4 internal routes, no migration, D-1…D-9 open). Status `WAITING_APPROVAL`. Design phase is meant for Opus 5.5 Medium; this draft was written on Sonnet 5.5 | Claude, conversation 2026-11-20 |
| 2026-11-20 | Gate 1 (spec) approved by the owner: "duyệt hết như đề xuất" — design.md D-1…D-9 as recommended (member name = part of email before `@`; LEFT member joining again → 409; 2 public + 4 internal routes; join again → 200; GUEST_ACCESS_EXPIRED only when a cookie was sent; signed-in Join joins directly; Refresh button, no polling; "See my status" link; copy table). Status `APPROVED` | Owner, conversation 2026-11-20 |
| 2026-11-20 | Gate 2 (plan) approved by the owner: "duyệt plan" — tasks T01–T09 and test cases TC-01…TC-23 in implement.md. Status `IN_PROGRESS`; branch `feature/my-attendance-status` | Owner, conversation 2026-11-20 |
| 2026-11-20 | Built on `feature/my-attendance-status`: backend T01–T05, frontend T06–T08, one fix round after review, `PIPELINE OK`, live check on Docker. Status `IN_REVIEW`; open for the owner (Gate 3): visual check of `/my/games/:gameId`, approved copy for a failed join (409) and for the "Back to the game" / "Check in again" links, acceptance of the deviations in implement.md and review.md | Claude, review.md |
| 2026-11-20 | Owner approved the review points "as proposed": failed join (409) shows the neutral error text; My game links carry `?code=<publicCode>` (Back to the game, Check in again); route `member-join` stays `ACCOUNT`; JoinPage fetches the public summary; copy "match" kept; missing identity token accepted as debt (AD-15). Frontend fixed, build passes | Owner, conversation 2026-11-20 |
| 2026-11-25 | Owner confirmed `DONE` ("mark F05 … done"); merged to `main` in PR #4. TC-21 (visual check of `/my/games/:gameId`) accepted by the owner as `NOT VERIFIED` by Claude; review item 11 closed by the merge | Owner, conversation 2026-11-25 |
