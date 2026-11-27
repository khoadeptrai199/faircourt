# F09 — Match suggestions (read-only)

| Field | Value |
|---|---|
| Sprint | S04 Match loop |
| Status | `IN_REVIEW` |
| Blueprint | §5 (rules v1), §6, §11 ("Suggestion itself writes nothing"), §13 (`POST /host/sessions/{sessionId}/suggestions`, example response), §14 (engine contract), §16 (review route) |
| Depends on | F07 |
| Waiting for | Owner: visual check AC-06 (TC-25..28) and commit |
| Suggested branch | `feature/match-suggestions` |
| Last updated | 2026-11-26 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

The host taps "Suggest matches" and sees one proposal per free court (teams A/B, reason tags, warnings) computed by the pure matcher from live data. Nothing is saved. Works for singles and doubles games (OD-20).

## Scope

- In: snapshot query (READY attendance not in an unreleased match; free courts; `availableSince`, `matchesPlayed`; partners from each player's last three **finished** matches); singles path in the matcher; endpoint returning the §13 shape plus `noSuggestionReason`; review page `/host/games/:gameId/review` with names taken from the host board; reason and warning labels; host board button enabled.
- Out: swapping and confirming (F10); start/finish/cancel (F11).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Seed (8 READY, 2 free courts) → 2 proposals equal to the `MatchSuggesterTest` seeded case; row counts in all tables unchanged | IT |
| AC-02 | Fewer than four READY → no proposals + `WAITING_FOR_PLAYERS`; no free court → `NO_FREE_COURT` | IT |
| AC-03 | Partners from the last three finished matches reach `recentPartners` (cancelled matches ignored) | IT |
| AC-04 | Players in a PLANNED/ACTIVE match and busy courts are excluded | IT |
| AC-05 | Non-host → 403; `CLOSED` game → 409 `STATE_CONFLICT` | `AccessMatrixIT` + IT |
| AC-06 | Review page shows `WIDE_SKILL_GAP` as "This match may have a wide skill gap." (§6) and unknown level visibly | visual |
| AC-07 | Singles game (OD-20) → 1 vs 1 proposals, no attendance id twice, `WAITING_FOR_PLAYERS` below 2 ready | unit + IT |
| AC-08 | player-service down → 503, nothing written | IT |

## Design notes

- See [design.md](design.md): `SuggestionSnapshotQuery`, `SuggestionService`, `HostSuggestionController`, `HostSuggestionResponse` in `playmatch/match`.
- `MatchSuggester` stays one concrete class (AD-10); it gains a singles path because of OD-20. Doubles behavior unchanged.

## Open decisions

| ID | Question | Status |
|---|---|---|
| D-1 | Own review page vs inside the board | Decided 2026-11-26: own page (§16) |
| D-2 | No-suggestion reason field | Decided 2026-11-26: `noSuggestionReason` |
| D-3 | Singles rule | Decided 2026-11-26: pair adjacent bands, no repeat-opponent rule |
| D-4 | Minimum sets (OD-19 b) | Decided 2026-11-26: already met by "fewer matches first" |
| D-5 | Names in response | Decided 2026-11-26: ids only, names from board |
| D-6 | §14 "maximum 24 READY" | Decided 2026-11-26: no cap |
| — | Reason/warning label texts | Approved 2026-11-26 (design §7) |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-26 | Design written (`BACKLOG → WAITING_APPROVAL`); AC-07 (singles), AC-08 (503) added | Claude |
| 2026-11-26 | Gate 1 (spec) approved by the owner: design.md with all recommendations (D-1 own review page, D-2 `noSuggestionReason`, D-3 singles = adjacent level pairs without repeat-opponent rule, D-4 no extra minimum-sets rule, D-5 ids only, D-6 no pool cap) and the copy table; `WAITING_APPROVAL → APPROVED` | Owner, this conversation |
| 2026-11-26 | Gate 2 (plan) approved by the owner: implement.md tasks T01–T08 and test cases TC-01…TC-28; `APPROVED → IN_PROGRESS` | Owner, this conversation |
| 2026-11-26 | Built and reviewed (fresh-session review 86/100, findings fixed); pre-commit pipeline PASS; AC-01…05, 07, 08 PASS, AC-06 NOT VERIFIED (owner visual check); `IN_PROGRESS → IN_REVIEW` | Claude |
