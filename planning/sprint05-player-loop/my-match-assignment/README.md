# F12 — My game: own assignment + polling

| Field | Value |
|---|---|
| Sprint | S05 Player loop |
| Status | `IN_REVIEW` |
| Blueprint | §3, §6 (My game, "Your next match: Court 2"), §11 (privacy), §13 (`GET /sessions/{id}/me` with `assignment`), §16 (`/my/sessions/:sessionId`, 15 s polling, Refresh) |
| Depends on | F05, F11 |
| Waiting for | Gate 3: owner browser check TC-11 (polling) |
| Suggested branch | `feature/my-match-assignment` |
| Last updated | 2026-11-27 |

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
| OD-16 | `matchId` in own assignment | Decided 2026-11-27: not in the assignment; decided in Match-fit feedback (design D-1) |

## Decision log

- 2026-11-27 — Design drafted ([design.md](design.md)); waiting for Gate 1.
- 2026-11-27 — Gate 1 (spec) approved by the owner ("duyệt hết như đề xuất"): whole design as recommended (D-1 no `matchId` now, OD-16 moves to Match-fit feedback; D-2 polling rules; D-3 finished = Ready card, no closed state; D-4 copy table in §7). Status `APPROVED`.
- 2026-11-27 — Plan and test cases written ([implement.md](implement.md): T01–T08, TC-01…TC-12); waiting for Gate 2.
- 2026-11-27 — Gate 2 (plan) approved by the owner: tasks T01–T08 and test cases TC-01…TC-12 as written, including agent-derived TC-07 edge cases; owner asked to build, test the full flow, review, then commit and push if no issue. Status `IN_PROGRESS`.
- 2026-11-27 — Build loop finished: backend + frontend built, `scripts/precommit.sh` PIPELINE OK (twice), full-flow script on the running stack E2E OK, fresh-session review APPROVE 90/100, scope and privacy reviews no open high/medium (findings in [review.md](review.md)). AC-01…AC-03 PASS, AC-04 NOT VERIFIED (owner browser check TC-11). Committed and pushed on the owner's request. Status `IN_REVIEW`.
