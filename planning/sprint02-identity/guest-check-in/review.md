# F04 review — Guest check-in + guest cookie

Review of the uncommitted work on `feature/guest-check-in` (HEAD 28c7446 = `origin/main` merged into the design commits). Run 2026-11-18 on Opus 5.5 reviewers; reviewers were read-only and ran no tests (the pipeline and live check are in implement.md).

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `scope-guardian` | 2026-11-18 | `git diff HEAD` + new files vs blueprint and the approved design | OK with minor notes; no new owner decision needed (the implementer's six deviations judged acceptable) |
| `privacy-reviewer` | 2026-11-18 | same, token / cookie / identity / logging focus | No high or medium; 2 low defects, 2 uncertain points |
| `flow-reviewer` round 1 | 2026-11-18 | same, rubric + override gates | 67/100 REWORK (4 medium, ~9 low); no override gate tripped |
| `flow-reviewer` round 2 | 2026-11-18 | same after fix round 1 | **94/100 APPROVE**; no override gate tripped |
| Owner | | | pending (Gate 3) |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `JoinPage.tsx` | A server 400 left the page on question 4 with no message (silent loop) | Back to the name step, shows the approved text | FIXED |
| 2 | medium | gateway `GuestCheckInIT` | The 4 KB cap test could not fail (name rule fired first) | Valid body + 5000 spaces, asserts 400 and zero stub calls | FIXED |
| 3 | medium | gateway tests | `Secure` and the `__Host-fc_guest` name were never tested | `GuestSecureCookieIT` | FIXED |
| 4 | medium | playmatch, gateway | Attendance state passed as String | `AttendanceState` enum in playmatch and its own small copy in the gateway | FIXED |
| 5 | low | auth `GuestCredentialService.resolve` | `Object[]` casts | private record | FIXED |
| 6 | low | playmatch, gateway tests | No log assertion for name / answers | output-capture assertions | FIXED |
| 7 | low | playmatch tests | "Down" only simulated as HTTP 500 | closed-port cases for player and auth | FIXED |
| 8 | low | `QuestionStep.tsx` | Legend not first child of the fieldset | moved | FIXED |
| 9 | low | tests | Inline fully qualified names | imports | FIXED |
| 10 | low | 3 services | Name / answer rule copies could drift | cross-reference comments; shared test vectors (not byte-identical, see 14) | FIXED |
| 11 | low (owner) | gateway `GuestCookie` | One cookie name for all games: a browser holding a guest cookie for game A that checks in to game B loses its access to game A (credential stays valid in the DB but the browser no longer has it). Design §12 says "one active identity per browser per game", the design uses one cookie name | Owner decides: accept (one identity per browser for now) or scope the cookie per game (name or path) in a later feature | ACCEPTED (owner 2026-11-18): one identity per browser for now; revisit when Own attendance status reads the cookie, because a per-game cookie path would hide it from `/games/{id}/me` |
| 12 | low (owner) | `JoinPage.tsx` success panel | Always shows "Waiting for host approval", also for a re-join answered with another state; no approved copy for other states | Owner decides the copy (it matters once the host can approve: Attendance management / Own attendance status) | ACCEPTED (owner 2026-11-18): copy for other states comes with Attendance management |
| 13 | low (owner) | timeouts | Gateway read timeout 5 s is shorter than playmatch's worst case (two calls of up to 2 s + 5 s). With a slow auth-service the gateway can answer 503 while playmatch still finishes and leaves a PENDING entry plus an active credential no browser holds; the guest retries and appears twice. This is the accepted "answer lost" case (D-4), made a little more likely | Accept, or raise the gateway read timeout for this one route | ACCEPTED (owner 2026-11-18): part of the D-4 risk; the host rejects the duplicate |
| 14 | low | name rules, `GuestProfileIT` comment | Test vectors for names differ slightly between gateway and the other two services; a comment says "identical" | Soften the comment or align the values | OPEN |
| 15 | low | `GuestCheckInService.checkIn` | A catch that only logs and rethrows (AD-04 says no) | Remove the catch | OPEN |
| 16 | low | `GuestCheckInService` | 'PENDING' / 'LEFT' written as SQL text, not bound from `AttendanceState` | Bind the enum | OPEN |
| 17 | low | name length | "1-80 characters" counted in UTF-16 units (a name of 41+ emoji can be refused) | Count code points, or accept | OPEN |
| 18 | low | `JoinPage.tsx` | Every 400 shows "Enter your name." even for a too-long name or a control character | Owner judges at the visual check | OPEN |
| 19 | low | `GuestCredentialService.resolve` | `MessageDigest.isEqual` after the DB already matched the hash adds nothing (harmless) | Drop or comment | OPEN |
| 20 | info | name rule | Bidi override characters (U+202E) pass the control-character rule; relevant when the host board shows names | Note for Attendance management | OPEN |
| 21 | info | process | Gate 2 was given by the owner's instruction, not by reading the plan; Password reset README was repaired in this branch (owner asked) | Owner confirms at Gate 3; ideally commit the README repair as its own commit | OPEN |
| 22 | info | misconfiguration trap | `__Host-fc_guest` with `COOKIE_SECURE=false` gives a cookie the browser silently rejects; no startup check | Note; the HTTPS gate already lists both settings | OPEN |

Status: `OPEN` | `FIXED` | `ACCEPTED (owner, reason)`. Items 14-20 and 22 are low and were left open on purpose (round limit and no behaviour risk); none trips an override gate.

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | gateway `GuestController`, `GuestCookie`; playmatch `GuestCheckInService` | gateway `GuestCheckInIT`, `GuestSecureCookieIT`, live TC-15 | PASS |
| AC-02 | auth `GuestCredentialService` | `GuestCredentialIT` (7), live: hash only, token and name in 0 log lines | PASS |
| AC-03 | player `GuestAnswers`, `AnswerBand` | `GuestAnswersTest`, `GuestProfileIT` | PASS |
| AC-04 | playmatch existing-attendance branch; gateway cookie resolve | playmatch and gateway `GuestCheckInIT`, live repeat → 200 | PASS |
| AC-05 | name rules in 3 services | gateway, playmatch, player tests | PASS |
| AC-06 | playmatch 404 / 409 | `GuestCheckInIT` incl. row counts unchanged | PASS |
| AC-07 | gateway CSRF, JSON-only, body cap | `GuestCheckInIT`, `GatewayAccessMatrixIT`, live no-CSRF 403 | PASS |
| AC-08 | `JoinPage`, `QuestionStep` | frontend build + format only | NOT VERIFIED (owner looks at 320 / 375 / 1280 px) |
| AC-09 | compensation to LEFT; 503 mapping | playmatch `GuestCheckInIT` incl. closed-port cases | PASS |
| AC-10 | `AccountController.logout` | gateway `GuestCheckInIT`, `AccountLoginIT` | PASS |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-18, Windows PC + WSL, after fix round 1: `PIPELINE OK`)
- README / `API Document` updated: yes (`API Document/backend.md`, flow maps)
- Result: `PASSED` for the automated gates (flow-reviewer 94/100), waiting for the owner (Gate 3)
- Not verified: AC-08 visual check; `Secure` / `__Host-` over real HTTPS; a real browser; the effect that returns the join page to the name step on a 400 has no automated test (no frontend test runner in the project)
