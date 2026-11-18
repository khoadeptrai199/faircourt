# F12 — My game: own assignment + polling

| Field | Value |
|---|---|
| Sprint | S05 Player loop |
| Status | `BACKLOG` |
| Blueprint | §3, §6 (My game, "Your next match: Court 2"), §11 (privacy), §13 (`GET /sessions/{id}/me` with `assignment`), §16 (`/my/sessions/:sessionId`, 15 s polling, Refresh) |
| Depends on | F05, F11 |
| Waiting for | OD-16 (`matchId` for feedback) |
| Suggested branch | `feature/my-match-assignment` |
| Last updated | 2026-11-17 |

## Goal

Each player sees one large card: their match number, court number and status (Assigned vs. Playing), refreshed every 15 seconds while the page is visible, with a Refresh button. Nobody sees anyone else's assignment.

## Scope

- In: `MyAttendanceQuery` returns `assignment: {matchNo, courtNo, matchState}` from the caller's unreleased match (+ `matchId` if OD-16 a), else `null`; `MyAssignmentCard`; `usePolling` (visible-only); states pending, ready/no match, assigned, playing, finished/closed, expired guest access.
- Out: opponents, partners, bands — never (§11).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Four assigned players each read their own `matchNo`, `courtNo`, `PLANNED`; a fifth reads `assignment: null` (§17 step 4 gate) | IT |
| AC-02 | Response keys ⊆ allowlist; no other player's data | `JsonAllowlist` |
| AC-03 | Anonymous / other player cannot read the guest's assignment | `AccessMatrixIT` (§0 step 5) |
| AC-04 | Page updates within 15 s after start/finish while visible; polling pauses when hidden; Refresh works | visual |

## Design notes

- Extends the F05 query and DTO additively (new nested field values, same keys).
- `hooks/usePolling` is the single transport seam for a future WebSocket (system-design §13).

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-16 | `matchId` in own assignment | Open |
