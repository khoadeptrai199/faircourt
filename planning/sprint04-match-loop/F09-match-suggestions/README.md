# F09 — Match suggestions (read-only)

| Field | Value |
|---|---|
| Sprint | S04 Match loop |
| Status | `BACKLOG` |
| Blueprint | §5 (rules v1), §11 ("Suggestion itself writes nothing"), §13 (`POST /host/sessions/{sessionId}/suggestions`, example response), §14 (engine contract) |
| Depends on | F07 |
| Waiting for | — |
| Suggested branch | `feature/F09-match-suggestions` |
| Last updated | 2026-11-17 |

## Goal

The host taps "Suggest matches" and sees one proposal per free court (teams A/B, reason tags, warnings) computed by the existing pure matcher from live data. Nothing is saved.

## Scope

- In: `BoardSnapshotAssembler` (READY attendance not in an unreleased match; free courts; `availableSince`, `gamesPlayed`; partners from each player's last three **finished** matches); endpoint returning the §13 shape; review page that renders proposals with names taken from the host board; reason and warning labels.
- Out: swapping and confirming (F10).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Seed (8 READY, 2 free courts) → 2 proposals equal to the `MatchSuggesterTest` seeded case; row counts in all tables unchanged | IT |
| AC-02 | Fewer than four READY → no proposals + `WAITING_FOR_PLAYERS`; no free court → `NO_FREE_COURT` | IT |
| AC-03 | Partners from the last three finished matches reach `recentPartners` (cancelled matches ignored) | IT |
| AC-04 | Players in a PLANNED/ACTIVE match and busy courts are excluded | IT |
| AC-05 | Non-host → 403; `CLOSED` session → 409 `STATE_CONFLICT` | `AccessMatrixIT` + IT |
| AC-06 | Review page shows `WIDE_SKILL_GAP` as "This match may have a wide skill gap." (§6) and unknown level visibly | visual |

## Design notes

- `matches/BoardSnapshotAssembler` (anti-corruption layer, AD-01), `matches/HostSuggestionController`, `matches/SuggestionResponse`.
- `MatchSuggester` is reused unchanged (AD-10). The response field for the "no suggestion" reason is not in §13's example → decide in design (e.g. `"reason": "WAITING_FOR_PLAYERS"`).
- Labels for `SIMILAR_LEVEL`, `BALANCED_TEAMS`, `LONG_WAIT`, `UNKNOWN_LEVEL` beyond the §6 examples are NEW copy.

## Open decisions

| ID | Question | Status |
|---|---|---|
| — | Response field name for the no-suggestion reason | Decide in design |
| — | Reason/warning label texts | Copy approval |
