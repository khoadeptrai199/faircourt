# F05 review — Own attendance status + member join

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `scope-guardian` (Opus) | 2026-11-20 | uncommitted diff on `feature/my-attendance-status` vs design.md, implement.md, blueprint | No out-of-scope behavior, no new dependency or migration; 3 medium, rest low (below) |
| `privacy-reviewer` (Opus) | 2026-11-20 | same diff, identity, IDOR, allowlists, logs, races | No high or medium; 4 low, 2 uncertain |
| Live check (agent) | 2026-11-20 | Docker stack, curl through the gateway, psql, log grep | All cases as expected, no bug |
| Owner | — | — | pending (Gate 3) |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `JoinButton.tsx` | 409 on join always showed "This game is closed.", wrong for a `LEFT` member (D-4) | Now shows the neutral `common.errorTitle` | `FIXED` (owner approved 2026-11-20) |
| 2 | medium | `MyGamePage.tsx` | 404 state has no link back to the game; 401 "Check in again" goes to `/` (the page does not know the public code) | Pass the public code to the page, or accept | `FIXED` (owner approved 2026-11-20) |
| 3 | medium | `implement.md` | Evidence table still said "not implemented" | Filled with this run's evidence | `FIXED` |
| 4 | low | `GatewayRouteTable` | Route `member-join` uses `RouteAccess.ACCOUNT`, not `PLAYER` (stricter: guest cookie cannot reach the controller) | Keep | `ACCEPTED (owner, 2026-11-20)` |
| 5 | low | `JoinPage.tsx` | The join page fetches the public summary for `gameId` (design assumed it already had it); if that GET fails the "See my status" link does not appear | Keep, or put `gameId` in the check-in answer (changes an approved response) | `ACCEPTED (owner, 2026-11-20)` |
| 6 | low | `copy/en.ts` | "match" in UI copy where conventions say "set" | Approved copy; change only on request | `ACCEPTED (owner, 2026-11-20)` |
| 7 | low | `MemberAttendanceService` | `TransactionTemplate` instead of one `@Transactional` method | Justified: the remote call stays outside the transaction | `ACCEPTED (design, AD-16)` |
| 8 | low | clients | Member calls sit in `guest/` packages (`PlayerClient`, `PlaymatchClient`) | Internal naming; move later | `ACCEPTED (Claude, internal naming)` |
| 9 | low | `copy/en.ts` | `myGame.backToGame` approved but unused | Used when #2 is resolved | `FIXED` (owner approved 2026-11-20) |
| 10 | low | README | Dated rows sat in the "Open decisions" table | Moved under "Decision log" | `FIXED` |
| 11 | low | planning | This branch also carries the status change of Account login, Password reset, Guest check-in to `DONE` | Commit separately if wanted | `OPEN` (owner) |
| 12 | low (privacy) | `MemberAttendanceService` | Attendance name was the email part even for members with a profile name | Uses the profile's own name | `FIXED` (fix round) |
| 13 | low (privacy) | `PlayerAccessController` | Empty or surrogate-split name from the email | Cut by code points; empty → 400 (known limit, no fallback copy) | `FIXED` / known limit |
| 14 | low (privacy) | `MemberAttendanceService` | Game closed during a join could still get a `READY` row | Re-check with `FOR SHARE` inside the insert transaction | `FIXED` |
| 15 | low (privacy) | internal routes | Internal routes take the caller id from the query/body; no signed identity token exists yet (AD-14); same trust model as Guest check-in (AD-15) | Accepted debt until the identity token ships before host routes | `ACCEPTED (design, AD-15)` pending owner acknowledgement |
| 16 | uncertain | `GlobalExceptionHandler` | Fallback logs full exceptions; a database error may carry a user id | Outside this diff; not verified | `OPEN` (note) |
| 17 | uncertain | `RequestIdFilter` | Whether a client-sent `X-Request-Id` is validated (log injection) | Outside this diff; not verified | `OPEN` (note) |

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | gateway `PlayerAccessController`, playmatch `MyAttendanceQuery` | ITs + live | PASS |
| AC-02 | gateway route rules, query `id AND game_id` | access matrix ITs + live | PASS |
| AC-03 | `MyAttendanceResponse`, `MemberJoinResponse` | `JsonAllowlist` + live | PASS |
| AC-04 | `ON CONFLICT DO NOTHING` | parallel ITs + live | PASS |
| AC-05 | closed check + `FOR SHARE` | IT | PASS (tests only) |
| AC-06 | `GUEST_ACCESS_EXPIRED` mapping, `MyGamePage` | IT + live; page | API PASS; page NOT VERIFIED |
| AC-07 | player `MemberProfileService` | IT + live | PASS |
| AC-08 | LEFT → 409, 503 mapping | IT | PASS (tests only) |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-20, Windows PC / WSL Ubuntu; `PIPELINE OK`, again after the fix round)
- README / `API Document` updated: root `README.md` stays empty by owner request; `API Document/` not touched (not verified whether it must list the 2 new public routes)
- Result: `PASSED`; only item 11 (commit the three DONE status changes separately) and the owner visual check remain
- Not verified: page look and states at 320 / 375 / 1280px (TC-21), the join button and the "See my status" link in a real browser, 503 cases on the live stack, the fix round on the live stack (tests only)
