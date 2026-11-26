# F06 review — Host board

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `flow-reviewer` (Opus), round 1 | 2026-11-25 | `git diff main` + untracked on `feature/host-board` (uncommitted) vs design.md, implement.md | `MINOR_REWORK`, 81/100; all override gates PASS |
| `scope-guardian` (Opus), round 1 | 2026-11-25 | same diff vs blueprint + approved design | needs owner decision (403 link); 1 more medium (evidence not filled), 7 low |
| `privacy-reviewer` (Opus), round 1 | 2026-11-25 | same diff; identity token, access order, responses, logs, config | no high or medium; 3 low, 1 uncertain |
| `flow-reviewer` (Opus), round 2 | 2026-11-25 | diff after fix round 1 + docs | `MINOR_REWORK`, 88/100 (93 if the owner accepts the 403 link); all override gates PASS |
| `scope-guardian` (Opus), round 2 | 2026-11-25 | diff after fix round 1 + docs | nothing out of scope; 403 link open for the owner; doc findings (fixed after the round) |
| Live check (orchestrator) | 2026-11-25 | rebuilt gateway, player, playmatch on Docker (WSL); `curl` through the gateway | all cases as expected (implement.md, TC-33) |
| Owner | 2026-11-26 | review findings | all recommendations accepted ("duyệt hết như đề xuất"); visual check TC-30…32 and commit still pending |

The review-round limit of the build loop (2) is reached; the remaining items below are for the owner.

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| 1 | medium | `features/host-board/HostBoardPage.tsx` (403 view) | Links to home ("Back to home") instead of "Open the public page" (§16, design §7): the 403 answer has no public code and the page only knows the game id | (a) accept the home link, or (b) approve a way to learn the public code (new field or route; OD-02 already makes id ↔ code public) | `ACCEPTED (owner 2026-11-26, option a: home link; no new field or route; the public page link stays on the board's game strip)` |
| 2 | medium | `.env.example` | Round 1: placeholder of 53 characters started the stack with a secret that is public in git | Value now empty; `docker compose` stops with a clear message (verified). **Side effect:** §0's `cp .env.example .env && docker compose up --build -d` no longer works without one extra step, unlike the other values (`POSTGRES_PASSWORD`, `DEMO_PASSWORD` ship working local defaults) | `FIXED`; §0 trade-off `ACCEPTED (owner 2026-11-26)`: `.env.example` ships a working local-only value (`local-dev-only-…`, 55 characters) so the §0 first run works again; a real random secret is part of the pre-deployment gate (#19) |
| 3 | medium | `implement.md` | Tasks, evidence and deviations were not filled | Filled (commands, AC evidence, 8 deviations) | `FIXED` |
| 4 | medium | `board/HostBoardResponse`, `HostBoardQuery` | Match state was a `String` | `MatchState` enum in `playmatch.match` (constants only) | `FIXED` |
| 5 | low | `player/level/PlayerLevelsQuery` | `@Transactional` on a read | Removed | `FIXED` |
| 6 | low | `backend/pom.xml` | Failsafe comment moved above surefire | Moved back | `FIXED` |
| 7 | low | `api/types.ts`, `PlayerList.tsx` | `availableSince` typed nullable | `string`; guard removed | `FIXED` |
| 8 | low | `copy/en.ts` | `hostBoard.suggestSoon` duplicated `common.comingSoon` | Uses `common.comingSoon` | `FIXED` |
| 9 | low | `GameStrip.tsx` | Public link used `text-accent` ("not for small text") | `text-highlight` on the ink strip; contrast not measured | `FIXED` (visual check at Gate 3) |
| 10 | low | `PlaymatchServiceAccessIT` | No non-host-account row | Added (403 board, own empty list) | `FIXED` |
| 11 | low | `IdentityTokenTest` | No test for future `iat` or lifetime ≠ 30 s | Added | `FIXED` |
| 12 | low | `API Document/`, `.claude/flow/maps/` | Host routes missing; older Own attendance status rows stale; broken table rows | Updated, stale rows corrected, tables fixed | `FIXED` |
| 13 | low | `AccessPolicy.requireAccount`, `guest/ServiceClients` (public), chunked levels calls | Not in design §6 | Recorded in implement.md Deviations (internal structure) | `ACCEPTED (Claude, internal structure)` |
| 14 | low | `HostBoardResponse.Player`, `PlayerLevelsClient.Level`, `PlayerLevelsResponse.Entry` | `kind` / `bandStatus` are `String` in Java (unions in TS) | Small enums per service, or keep | `ACCEPTED (owner 2026-11-26, later cleanup)` |
| 15 | low | `HostBoardResponse.Teams` | Record components `A` / `B` break Java naming | `a` / `b` with `@JsonProperty`, or keep | `ACCEPTED (owner 2026-11-26, later cleanup)` |
| 16 | low | `IdentityToken.verify` | No clock-skew allowance for `iat` (whole seconds) | Fine on one host; allow 1–2 s before any multi-host deployment | `ACCEPTED (owner 2026-11-26)`: added to the pre-deployment gate (multi-host only) |
| 17 | low | identity token claims | No audience / method / path binding: a token seen on the internal network works for 30 s on any route with `CallerIdentity`; today only two GET routes | Decide before the first host write route (Attendance management): add `aud` + method/path claims, or accept as an AD | `ACCEPTED (owner 2026-11-26)`: decided in the Attendance management design, before its first host write route (listed there as an open decision) |
| 18 | uncertain | `CommonWebAutoConfiguration` | `@ConditionalOnProperty` without `havingValue`: a secret literally `false` turns the beans off (expected 500, not a bypass; not confirmed) | Make the bean required where needed, or accept | `ACCEPTED (owner 2026-11-26, note)` |
| 19 | low | HTTPS deployment gate | A real random `INTERNAL_TOKEN_SECRET` belongs to the pre-deployment gate next to `COOKIE_SECURE=true` and `__Host-` cookies | Add to that gate | `ACCEPTED (owner 2026-11-26)`: recorded in `CLAUDE.md` (owner decisions) and backlog L-01 |

Status: `OPEN` | `FIXED` | `ACCEPTED (owner, reason)`.

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | playmatch `board/HostBoardQuery`, `HostBoardService`, `HostBoardController`; player `level/*` | `HostBoardIT` (TC-15…20, 26) + live | PASS |
| AC-02 | common-web `identity/*`; gateway `IdentityTokenFilter`, `RouteAccess.HOST`; playmatch `access/AccessPolicy` | `IdentityTokenTest`, resolver test, `HostRoutesIT`, `GatewayAccessMatrixIT`, `PlaymatchServiceAccessIT`, `HostBoardIT` + live | PASS |
| AC-03 | `HostBoardResponse` (host audience only) | exact field-path equality (TC-24); reviewers' grep | PASS |
| AC-04 | `features/host-board/HostBoardPage.tsx` states | build only; TC-30 visual | NOT VERIFIED (owner) |
| AC-05 | host board layout | TC-31 visual | NOT VERIFIED (owner) |
| AC-06 | `PlayerLevelsClient` → 503 | `HostBoardIT` TC-25, `PlayerLevelsIT` | PASS (tests only; not tried live) |
| AC-07 | `HostGamesController`; home "Games you host" | `HostGamesIT` + live; TC-32 visual | API PASS; page NOT VERIFIED |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-25, Windows PC / WSL Ubuntu; `PIPELINE OK` in 109 s after the fix round)
- README / `API Document` updated: root `README.md` stays empty by owner request; `API Document/backend.md` and `frontend.md` updated; flow maps updated
- Result: `PASSED` for the automated gates (all override gates PASS; flow-reviewer 88/100, 93 with finding 1 accepted by the owner on 2026-11-26); no finding left `OPEN`; waiting for the owner's visual check and commit (Gate 3)
- Not verified: page look and states (TC-30…32) in a real browser; 503 on the live stack; the Mac (needs its own `INTERNAL_TOKEN_SECRET` in `.env` first); clock skew across hosts
