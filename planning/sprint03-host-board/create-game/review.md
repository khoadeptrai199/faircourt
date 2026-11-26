# F08 review — Create game

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `privacy-reviewer` (Opus, fresh session) | 2026-11-26 | `git diff 7e76d8a` + untracked files (backend, frontend) | No high/medium findings; 2 low (test strength) |
| `scope-guardian` (Opus, fresh session) | 2026-11-26 | same | Changes needed: 2 medium (evidence, API docs), 6 low |
| `flow-reviewer` round 1 (Opus, fresh session) | 2026-11-26 | same, against design.md + implement.md | APPROVE 91/100; all override gates PASS; 2 medium (evidence, docs), 6 low |
| `flow-reviewer` round 2 (Opus, fresh session) | 2026-11-26 | same, after fix round 1 | APPROVE 93/100; all override gates PASS; round-1 findings resolved; 8 low |
| Owner | 2026-11-26 | Gate 3: UI in the browser, finding 12 | UI ok; one error text kept; DONE |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `implement.md` | Tasks still `TODO`, no commands or evidence recorded | Filled tasks, commands with real results, per-AC evidence | `FIXED` |
| 2 | medium | `API Document/backend.md`, `frontend.md`, flow maps | New route and page not documented | Route section, reachable-routes list, page row, map rows | `FIXED` |
| 3 | low | `CreateGamePage.tsx` | Error showed two lines (approved text + generic banner) and a Retry that could re-send after a gateway timeout | One alert with the approved text, no Retry; Create game button is the retry | `FIXED` |
| 4 | low | `CreateGamePage.tsx` | 401 on submit redirected to login (input lost); not in design | 401 is the normal error state | `FIXED` |
| 5 | low | `CreateGamePage.tsx` | Mutation state not rendered with an exhaustive `switch` | `SubmitResult` with exhaustive switch | `FIXED` |
| 6 | low | `CreateGamePage.tsx` | Price inputs not `type="number"` | `type="number"` + `inputMode="numeric"` | `FIXED` |
| 7 | low | `gameForm.ts` | Repeated `'SINGLES' \| 'DOUBLES'` literal | Uses `MatchFormat` | `FIXED` |
| 8 | low | `GatewayAccessMatrixIT` | Other methods accepted `401/403/405` | Pinned observed 403 + no forward per method | `FIXED` |
| 9 | low | `PlaymatchServiceAccessIT` | "Guest" row sends an invalid token | Renamed to invalid/foreign token; real guest row is in the gateway test | `FIXED` |
| 10 | low | `.claude/flow/maps/index.md` | `/host/games/new` missing in routes row | Added | `FIXED` |
| 11 | low | README decision log | `SecurityConfig` approval only in implement.md | Decision-log line added | `FIXED` |
| 12 | low | `CreateGamePage.tsx` | One error text for every failure (also 503); design said "the code's text" | Owner kept the one text for every failure (2026-11-26) | `ACCEPTED (owner, one clear message is enough; no per-code copy)` |
| 13 | low | `CreateGamePage.tsx` | `SubmitResult` status prop repeats the mutation status union | Later cleanup: use `MutationState<…>['status']` | `OPEN` (later cleanup) |
| 14 | low | `CreateGameController.java` | `requireAccount` in controller and again in service | Later cleanup: keep the service check only (same pattern as `HostAttendanceController`) | `OPEN` (later cleanup) |
| 15 | low | `GatewayAccessMatrixIT` `theCreateGameRowDoesNotTakeOverJoinAndMyGame` | `isIn(200, 201)` loose; no-forward check pins behavior | Later cleanup | `OPEN` (later cleanup) |
| 16 | low | `CreateGameIT` | Fully qualified `java.time` types inline; raw SQL inserts (no `TestData` builder exists yet) | Later cleanup | `OPEN` (later cleanup) |
| 17 | low | `PublicCodeGenerator.java` | No longer `final` so the test can subclass it | Recorded in Deviations; optional later: inject a small interface | `OPEN` (note) |

Status: `OPEN` | `FIXED` | `ACCEPTED (owner, reason)`.

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | `GameService.create`, `CreateGameController` | `CreateGameIT` TC-01…TC-05; live TC-16 | `PASS` |
| AC-02 | existing board / list / public queries read the new row | `CreateGameIT` TC-06; live TC-16 | `PASS` |
| AC-03 | `CreateGameRequest` validation + `GameService.validateRules` | `CreateGameIT` TC-07, TC-08; `GameServiceTest` TC-15 | `PASS` |
| AC-04 | gateway route `create-game` (`HOST`, CSRF, token v2), `SecurityConfig` POST only, `requireAccount` | `PlaymatchServiceAccessIT` TC-09, `CreateGameIT` TC-10, `GatewayAccessMatrixIT` TC-11, TC-12; live CSRF 403 | `PASS` |
| AC-05 | `INSERT … ON CONFLICT (public_code) DO NOTHING`, 5 tries | `CreateGameIT` TC-13, TC-14 | `PASS` |
| AC-06 | `features/create-game/`, route, home button | owner TC-17, TC-18 (2026-11-26) | `PASS` |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-26, Windows / WSL, `bash scripts/precommit.sh` → `PIPELINE OK`, before review and after fix round 1).
- README / `API Document` updated: `README.md` stays empty (owner 2026-11-17); `API Document/backend.md` and `frontend.md` updated; flow maps updated.
- Result: `PASSED`; owner Gate 3 approved 2026-11-26 (UI ok, finding 12 accepted) → `DONE`.
- Not verified: anonymous create *with* a CSRF token on the live stack (covered by `GatewayAccessMatrixIT`); the Mac.
