# F10 review — Match confirm + tap-to-swap review

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `flow-reviewer` (round 1) | 2026-11-26 | `git diff HEAD` + untracked on `feature/match-confirm` (base `feature/match-suggestions`, 600aadf) | MINOR_REWORK, 77/100 |
| `flow-reviewer` (round 2, after fix round 1) | 2026-11-26 | same | APPROVE, 94/100 (Fidelity 25, Type health 25, Coverage 25, Quality 19); all override gates PASS |
| `scope-guardian` | 2026-11-26 | same (before fix round 1) | CHANGES NEEDED only for open docs/evidence tasks (T09, T10); no scope violation, no unapproved addition, no deletion |
| `privacy-reviewer` | 2026-11-26 | same (before fix round 1) | NO ISSUES FOUND (two low notes) |
| Owner | 2026-11-27 | Gate 3 decisions (file move, Refresh disabled while saving, validation in service) | "duyệt hết như đề xuất"; browser checks and commit pending |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `HostMatchController` | `@Valid` body checked before the account/host check: bad body gave 400 instead of 401/404/403 (design §3.1 order) | raw body, parsed and validated in `MatchService` after `requireHost`; strict TC-21 back; new bad-body × caller test | `FIXED` |
| 2 | medium | `MatchReviewPage`, `lineup.ts` | player buttons active while a court saves → saved card could show a lineup different from the server's | `saving` state, taps ignored and buttons disabled while saving; Vitest cases | `FIXED` |
| 3 | medium | `ConfirmMatchResponse`, `MatchService` | `state` was a `String` literal | `MatchState`, SQL binds `MatchState.PLANNED.name()` | `FIXED` |
| 4 | low | `MatchProposal.tsx` | exports two components (`PlayerSlotButton`), convention says one per file | owner approved the move 2026-11-27: own file `PlayerSlotButton.tsx` | `FIXED` |
| 5 | low | `lineup.ts` | `reset` unused | dispatched by an effect on new data (redundant with the remount, harmless) | `FIXED` |
| 6 | low | `lineup.test.ts` | TC-30 reset used the same fixture | different fixture | `FIXED` |
| 7 | low | service tests | no service-level guest row | services have no guest caller kind; wrong-audience token → 401 test added; guest-only 401 proven at the gateway | `FIXED` (substitute) |
| 8 | low | `lineup.test.ts` | `as unknown as HostBoard` | typed builder | `FIXED` |
| 9 | low | docs | T09/T10 open | API Document, maps, implement.md evidence, this file | `FIXED` |
| 10 | low | `MatchService` | `DuplicateKeyException` → 409 path untested | hard to reach under the game row lock; no approved case | `OPEN` — noted |
| 11 | low | `MatchService` | validation moved from controller to service (convention) | recorded under implement.md "Deviations"; owner accepted 2026-11-27 | `FIXED` (accepted) |
| 12 | low | `MatchReviewPage` | "Refresh suggestions" clickable while a court saves (drops the in-flight result on screen; server state correct) | owner approved 2026-11-27: Refresh disabled while a court saves | `FIXED` |
| 13 | low | `MatchReviewPage` `CourtConfirm` | on 409 the "Refresh suggestions" button is in the sticky bar, not next to the message (design §7) | check in TC-33 | `OPEN` — owner visual check |
| 14 | low | `MatchService` | non-duplicate DB errors → 500 | checked: shared handler answers a generic `INTERNAL_ERROR` body, no SQL text | `FIXED` (no change needed) |

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | `MatchService.confirm` inserts match + players, `ASSIGNED`, `next_match_no + 1` | TC-05, TC-06, TC-10 | `PASS` |
| AC-02 | re-check court, ownership of ids, `canBeAssigned`, unreleased rows → 409 | TC-01, TC-07…TC-12 (rows unchanged) | `PASS` |
| AC-03 | `MatchLineup.of`, body parse + `Validator` | TC-02, TC-03, TC-13, bad-body test | `PASS` |
| AC-04 | game row `FOR UPDATE`, attendances `FOR UPDATE` by id | TC-14, TC-15 (3 runs) | `PASS` |
| AC-05 | gateway `host-matches` (HOST + CSRF), `requireAccount` / `requireHost` | TC-16, TC-17, TC-20, TC-21, wrong-audience test | `PASS` |
| AC-06 | `lineup.ts`, `MatchProposal`, `BenchList`, `MatchReviewPage` | Vitest 11 (logic) `PASS`; TC-31…TC-34 | `NOT VERIFIED` (browser, owner) |
| AC-07 | `MatchLineup` singles 1 + 1 | TC-04, TC-19 | `PASS` |
| AC-08 | board / suggestions read the new match | TC-18 | `PASS` |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-26, Windows / WSL, `PIPELINE OK` after fix round 1)
- `API Document` updated: yes (`backend.md`, `frontend.md`); maps updated; `README.md` stays empty (owner 2026-11-17)
- Result: `PASSED` (waiting for the owner's Gate 3 review and commit)
- Not verified: TC-31…TC-34 (browser: swap by tap and keyboard, Confirm, 409 flow, layout 320–1280px); `DuplicateKeyException` path; race tests' flakiness beyond 3 runs
