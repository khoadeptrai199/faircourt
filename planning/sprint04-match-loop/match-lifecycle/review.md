# F11 review — Match start / finish / cancel

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `flow-reviewer` (Opus, fresh session) round 1 | 2026-11-27 | `git diff main` + untracked, against `design.md` and `implement.md` | 85/100, `MINOR_REWORK`; all override gates passed; 2 medium (docs T08, stale evidence) + 6 low |
| `flow-reviewer` round 2 | 2026-11-27 | same, after fixes | **94/100, `APPROVE`**; round-1 findings resolved; evidence section was still being filled at that moment and was filled right after |
| `scope-guardian` | 2026-11-27 | `git diff main` + untracked | OK: nothing out of scope, unapproved copy, dependency, migration or deletion |
| `privacy-reviewer` | 2026-11-27 | same | No high or medium findings; 2 low (below) |
| Owner | | | waiting (Gate 3) |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `API Document/*.md`, `.claude/flow/maps/*.md` | Docs (T08) not written | wrote the three routes, court actions, new test classes | `FIXED` |
| 2 | medium | `implement.md` | Task statuses, commands, evidence and deviations stale | filled from the real run log | `FIXED` |
| 3 | low | `CourtActions.tsx` | "Saving…" showed on the primary button even when Cancel was saving | `savingAction` | `FIXED` |
| 4 | low | `HostBoardPage.tsx` | `court.match!` non-null assertion; `savingId = ''` used as a flag | local `const m`; explicit `busy` boolean | `FIXED` |
| 5 | low | `MatchService.updateMatch` | SQL built by joining a constant fragment (no value, no injection risk) | three fixed SQL blocks | `FIXED` |
| 6 | low | `MatchLifecycleIT` | unused `others` capture, filler `isNotNull` assertion | removed | `FIXED` |
| 7 | low | feature `README.md` | draft AC table numbered differently from `design.md` | marked superseded | `FIXED` |
| 8 | low | `PlayerList.tsx` | file changed by fix round 1 but not in the plan's Files table | noted under Deviations | `FIXED` |
| 9 | low | `MatchService.loadAndLock` | a signed-in account can tell a real match id (403) from an unknown one (404) | none: designed order (§13, design §3); ids are random UUIDs | `OPEN` (accepted by design; owner may confirm) |
| 10 | low | `frontend/src/api/types.ts`, `HostBoardPage.tsx` | `HostMatchState.state` repeats a union inline; `MATCH_CALLS` not typed as `Record<MatchAction, …>` | optional tidy-up | `OPEN` |
| 11 | low | AC-06 | No UI test for the court buttons, dialogs and 409 text (none planned) | owner's TC-20, TC-21 | `OPEN` (owner check) |

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 start | `MatchService.start`, `markStarted` | TC-03 `MatchLifecycleIT` | `PASS` |
| AC-02 finish | `MatchService.finish`, `markFinished` | TC-04 | `PASS` |
| AC-03 cancel | `MatchService.cancel`, `markCancelled` | TC-05, TC-06 | `PASS` |
| AC-04 409, rows unchanged | transition enums, `loadAndLock` | TC-07…TC-10, `MatchStateTest`, `AttendanceStateTest` | `PASS` |
| AC-05 reuse after release, wait priority | existing confirm + suggestions | TC-11…TC-13 | `PASS` |
| AC-06 board UI | `CourtActions.tsx`, `HostBoardPage.tsx`, `CourtCard.tsx` | TC-20, TC-21 (owner); build and unit tests only | `NOT VERIFIED` |
| AC-07 access | gateway routes, `HostMatchLifecycleController`, `SecurityConfig` | TC-17, TC-18, TC-19 | `PASS` |
| AC-08 singles | same commands | TC-14 | `PASS` |
| AC-09 concurrency | game `FOR SHARE`, match `FOR UPDATE` | TC-15, TC-16 | `PASS` |

## Gate

- Pre-commit pipeline: **`NOT VERIFIED` as one run** (2026-11-27, Windows + WSL): `scripts/precommit.sh` crashed the WSL VM twice, both times in the auth-service tests. The same checks passed piece by piece: backend format; `mvn verify` for playmatch + gateway (166 playmatch tests, gateway 114); player-service 23; auth-service 42; frontend format check, Vitest 11, build. Not run as part of any piece: the `.env` / `.idea/` staging guard and the applied-migration guard of `precommit.sh` (a manual `git status` shows no `.env` or `.idea/` change and no migration was touched).
- README / `API Document` updated: yes (`README.md` stays empty by owner request).
- Result: `PASSED` pending owner Gate 3 (open: findings 9–11, TC-20, TC-21).
- Not verified: `scripts/precommit.sh` as one run; any browser or visual check (layout at 320 / 768 / 1280 px, keyboard, dialogs, two-tab 409); restart-without-volume-removal check (TC-21); the gateway test total after the last fix round (overall `BUILD SUCCESS` seen).
