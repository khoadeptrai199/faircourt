# F13 — Match-fit feedback

| Field | Value |
|---|---|
| Sprint | S05 Player loop |
| Status | `BACKLOG` |
| Blueprint | §4 ("Too easy / About right / Too hard", feedback on the match, not on a person), §12 (`match_feedback`), §13 (`POST /sessions/{sessionId}/me/feedback`), §16 (not a public statistic) |
| Depends on | F12 |
| Waiting for | OD-16 |
| Suggested branch | `feature/F13-match-feedback` |
| Last updated | 2026-11-17 |

## Goal

After a finished match, a participant answers one tap: Too easy / About right / Too hard. One answer per player per match.

## Scope

- In: POST with `matchId` (or `matchNo` per OD-16) and `fit`; check the caller took part in that FINISHED match of this session; insert one row (primary key prevents a second); "How was the match?" prompt on My game.
- Out: showing feedback statistics (pilot metrics are read by the owner from the DB, §8).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Participant of a finished match → 201 | IT |
| AC-02 | Non-participant → 403/404 (decide in design); anonymous → 401 | `AccessMatrixIT` |
| AC-03 | Match not finished → 409; second answer → 409 `STATE_CONFLICT` | IT |
| AC-04 | Invalid `fit` → 400 | IT |
| AC-05 | Prompt appears once after finish and disappears after answering | visual |

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-16 | Identify the match by `matchId` or `matchNo` | Open |
