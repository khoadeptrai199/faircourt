# F12 review — My game: own assignment + polling

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `flow-reviewer` (fresh session) | 2026-11-27 | `git diff main` + untracked files on `feature/my-match-assignment` | APPROVE, 90/100 (Fidelity 24, Type health 22, Coverage 23, Quality 21); all override gates PASS or N/A |
| `scope-guardian` | 2026-11-27 | same | no scope, privacy or route problem; 1 medium (docs task T07 not done yet at review time) + 4 low |
| `privacy-reviewer` | 2026-11-27 | same | no issues found; 1 low, older and already planned |
| Owner | — | — | browser check TC-11 pending |

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `API Document/*.md`, `.claude/flow/maps/*.md`, `implement.md` | Docs and evidence still described the old `/me` (`assignment` always `null`) | T07 done after the review: API docs, maps, evidence, deviations | `FIXED` |
| 2 | low | `planning/ROADMAP.md` (matchId question) | Roadmap said "decided" while the feature pages said "open" | Roadmap row now says: `matchId` not in `/me` (decided); how the player names the finished match stays open for Match-fit feedback | `FIXED` |
| 3 | low | `frontend/src/features/my-game/StateCard.tsx` | One logic line changed (defensive body "You have a match."), plan said comment only | Listed under Deviations in `implement.md` | `FIXED` |
| 4 | low | planning status of Match suggestions / Match confirm | Moved to `DONE` outside this feature | Owner approved in the conversation on 2026-11-27 ("duyệt hết như đề xuất"); recorded in both decision logs | `FIXED` |
| 5 | low | `PlaymatchClient.myAttendance` | An unreadable playmatch answer would likely give `500`, design says `503` | Catch `RestClientException` → `503`; `"matchNo":"x"` case added to `PlayerAccessIT` | `FIXED` |
| 6 | low | `myCard.ts` `pollingWanted` | Status union written by hand | Derived from `AsyncState<unknown>['status']` | `FIXED` |
| 7 | low | gateway `player/MyAssignment.java` | `matchState` is a `String` (design §6 says so; an enum would turn FINISHED into a parse error) | Kept as designed | `ACCEPTED (design §6)` |
| 8 | low | gateway packages | `guest.PlaymatchClient` now imports `player.MyAssignment` while `player` imports `guest`: a package cycle | Kept; moving a file needs a separate small refactor | `OPEN` (low, follow-up) |
| 9 | low | `PlayerAccessIT` TC-08(b) | Proves the forwarded attendance id, not a per-guest answer body | Per-row privacy proven in playmatch TC-04/05/09; listed in Deviations | `ACCEPTED (deviation listed)` |
| 10 | low | `MyAssignmentIT` | Setup helpers copied from `MatchLifecycleIT`; no `support/TestData` builders in playmatch; constant `ALLOWED_A` used once | Old habit listed in `implement.md` | `OPEN` (low, follow-up) |
| 11 | low | `/internal/v1/games/{gameId}/my-attendance` | Still takes the caller id in the query, not the identity token; the answer now also carries the own match number and court | Already on the Host board backlog item (move older internal routes to the token) | `ACCEPTED (backlog)` |

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | `MyAttendanceQuery` single SQL, `MyAssignment`, gateway pass-through | `MyAssignmentIT` TC-01…TC-05; full-flow script | `PASS` |
| AC-02 | own row + own match columns only; gateway rebuilds the answer from its own records | `MyAssignmentIT` TC-06/TC-09, `PlayerAccessIT` TC-07 (exact body + `JsonAllowlist`) | `PASS` |
| AC-03 | identity unchanged (session / guest cookie); `game_id` pinned in both joins | `PlayerAccessIT` TC-08, `GatewayAccessMatrixIT`, full-flow anonymous 401 / non-joined 404 | `PASS` |
| AC-04 | `MyAssignmentCard`, `myCard.ts`, `usePolling`, `useAsync.refresh` | Vitest TC-10, TC-12 PASS; browser TC-11 not run | `NOT VERIFIED` (owner browser check) |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-27, Windows PC, WSL; run twice, second after the review fixes)
- `API Document` updated: yes (`README.md` stays empty by owner decision)
- Result: `PASSED` (no open high/medium finding)
- Not verified: TC-11 live polling in a browser (15 s update, pause while hidden, no "Loading…" flash, Refresh)
