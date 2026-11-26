# F07 review — Attendance management (approve, left)

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `flow-reviewer` (round 1) | 2026-11-26 | `git diff main` + untracked files on `feature/attendance-management` | MINOR_REWORK, 85/100; all override gates PASS |
| `scope-guardian` | 2026-11-26 | same | CHANGES NEEDED (1 medium doc corruption, rest low); nothing out of scope, no unapproved route / dependency / config / migration, no deletion |
| `privacy-reviewer` | 2026-11-26 | same | NO ISSUES FOUND (2 low, 2 uncertain) |
| `flow-reviewer` (round 2, after fix round 1) | 2026-11-26 | same, after fixes | APPROVE, 98/100; all round-1 findings resolved; all override gates PASS |
| Owner | — | — | pending (Gate 3) |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `planning/ROADMAP.md` OD-25 row | An older OD-24 row was glued onto the OD-25 row (broken table, OD-24 twice) | Stray text removed | `FIXED` |
| 2 | medium | `frontend/src/components/ConfirmDialog.tsx` | Fixed `id="confirm-title"` repeated on every row → screen reader may read another player's title before a final action | Title id from `useId()` | `FIXED` |
| 3 | low | `frontend/src/features/host-board/HostBoardPage.tsx` | 403 `CSRF_TOKEN_INVALID` after the retry shown as "You don't manage this game."; error banner stayed after the board re-read | Forbidden view only for code `FORBIDDEN`; `useMutation.reset()` called before reload; design §7 text clarified | `FIXED` |
| 4 | low | `frontend/src/features/host-board/AttendanceActions.tsx`, `api/host.ts` | `'READY' \| 'LEFT'` union defined in a component and repeated | `AttendanceTarget` in `api/types.ts` | `FIXED` |
| 5 | low | `frontend/src/api/types.ts` | New type placed under another type's comment | Comments moved | `FIXED` |
| 6 | low | `HostAttendanceIT.leftIsFinalAndRestingCannotBeChangedByTheHost` | Checked 409 only, not the `STATE_CONFLICT` code | Code asserted for every case | `FIXED` |
| 7 | low | gateway `IdentityTokenFilter` + `IdentityToken.issue` | Path the token cannot carry (> 512 chars) → 500 with stack trace | Mapped to 404 `NOT_FOUND`; test `aPathTooLongForTheTokenAnswers404AndNothingIsForwarded` | `FIXED` |
| 8 | low | `HostAttendanceController` | Comment said identity is checked first; a bad body without a token answers 400 before 401 (Spring reads the body first) | `CallerIdentity` parameter moved first (no behaviour change observed); comment states the real order; test `aMissingTokenWithAnInvalidBodyAnswers400AndWithAValidBodyAnswers401`. Only reachable from inside the network; the gateway answers 401 first | `FIXED` (documented) |
| 9 | low | feature `README.md`, `implement.md`, `.claude/flow/maps/api-routes.md` | Stale "draft" AC heading; wrong file path; frontend column "not yet" | Updated | `FIXED` |
| 10 | low | identity token audience | Every service with the resolver needs `spring.application.name` = gateway audience | Checked: only playmatch verifies tokens; `playmatch-service` = `GatewayRouteTable.PLAYMATCH` | `FIXED` (no mismatch) |
| 11 | low | `HostAttendanceIT` | Missing blank line between two tests | Style only | `OPEN` (low, does not block) |
| 12 | low | `AttendanceStateTest`, `HostRoutesIT` | Fully qualified `java.util.TreeSet` / `HashMap` inline instead of imports | Style only | `OPEN` (low, does not block) |
| 13 | low | local demo database | Live check left one `LEFT` guest row "Live F07 guest" | Ask the owner: keep or remove (no deletion without consent) | `OPEN` (owner) |
| 14 | uncertain | gateway raw path forwarding | Not verified that Spring Cloud Gateway MVC 5.0.4 forwards an encoded path byte for byte; a mismatch fails closed (401) | none | `ACCEPTED` (fail-closed; ids are UUIDs) |

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | `AttendanceService.changeState`, `HostAttendanceController`, `HostAttendanceResponse` | TC-02, TC-14, TC-15, live TC-21 | PASS |
| AC-02 | `AttendanceState.hostMayMoveTo` | TC-01, TC-03…TC-05 | PASS |
| AC-03 | `inMatch()`, game `FOR SHARE` check, transition table | TC-01, TC-06…TC-08, live `LEFT → READY` 409 | PASS |
| AC-04 | gateway route `host-attendance` (`HOST`, CSRF), `requireHost`, `id AND game_id` | TC-09, TC-10, TC-17, TC-18, live 403 / 401 | PASS |
| AC-05 | `IdentityToken` v2, `CallerIdentityArgumentResolver`, `IdentityTokenFilter` | TC-11…TC-13, TC-18 | PASS |
| AC-06 | `AttendanceActions`, `ConfirmDialog`, `HostBoardPage`, `PlayerList` | TC-19, TC-20 (owner visual + keyboard) | NOT VERIFIED (waits for the owner) |
| AC-07 | existing join / my-attendance rules | TC-16, live guest `/me` → LEFT | PASS |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-26, Windows / WSL, `bash scripts/precommit.sh` → `PIPELINE OK`, run before and after fix round 1); gateway module re-run after the fix: 109 IT, 0 failures.
- README / `API Document` updated: `README.md` stays empty (owner 2026-11-17); `API Document/backend.md` and `frontend.md` updated.
- Result: `PASSED` (no open high/medium findings) — waits for the owner's Gate 3 visual check and commit.
- Not verified: AC-06 (TC-19, TC-20 visual and keyboard checks: dialog, focus return, Esc, 44 px targets at 320 / 1280 px); the frontend fixes of round 1 were not seen in a browser; TC-14 (parallel approve) passed, timing not controlled.
