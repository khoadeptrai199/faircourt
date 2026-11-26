# F06 — Host board

| Field | Value |
|---|---|
| Sprint | S03 Host board |
| Status | `IN_REVIEW` |
| Blueprint | §3, §6 (host board composition), §11, §13 (`GET /host/sessions/{sessionId}/board`), §15 (AccessPolicy), §16 (`/host/sessions/:sessionId`) |
| Depends on | F03 |
| Waiting for | Gate 3: owner looks at the pages (TC-30…32) and commits (review findings decided 2026-11-26) |
| Suggested branch | `feature/host-board` |
| Last updated | 2026-11-26 |

## Goal

The host of a game sees everyone (Pending, Ready, Assigned, Playing, Resting, Left), each player's private level and level status, wait time and sets played, and every court with its current match or "free". Nobody else can.

## Scope

- In: identity token from the gateway to services (`CallerIdentity`, AD-14; deferred here by the Account login design D-4); `AccessPolicy.requireHost`; `HostBoardQuery` + `HostBoardResponse`; internal batch route for private levels in player-service; host page with game strip, court cards, status lists, `PlayerChip`, `WaitIndicator`, sticky "Suggest matches" button (disabled until F09); route to reach the board per OD-04; 403 page "You don't manage this game" with a link home (owner 2026-11-26; the public page link is on the game strip).
- Out: any action on the board (F07, F09–F11, F14).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Host → 200 with all attendance rows incl. guests and `LEFT`; `null` level shown to the host as "Unknown / provisional" (§13), self-reported level marked provisional (§3); courts 1…`court_count`, each free or with its current match and teams | IT |
| AC-02 | Other account → 403; guest cookie only → 401 (D-3); anonymous → 401; unknown game → 404; missing, expired or forged identity token at the service → 401 | `AccessMatrixIT`, `GatewayAccessMatrixIT`, unit |
| AC-03 | `HostBoardResponse` is used only by the host controller and has exactly the documented keys | `JsonAllowlist` + review / grep |
| AC-04 | Page states: loading, no ready players, fewer than four ready, request failure, 403, 401 → login | visual |
| AC-05 | Phone: court carousel above status lists; desktop: courts in 2–4 columns beside the ready pool (§16) | visual |
| AC-06 | player-service down → 503, no partial board | IT |
| AC-07 | (only if OD-04 = a) a signed-in account sees exactly the games it hosts and opens the board from there | IT + visual |

## Design notes

See [design.md](design.md). Packages: playmatch `access`, `board`, `player`; player `level`; `common-web` `identity`; gateway `routing`. Frontend: `features/host-board/`, `components/CourtCard`, `PlayerChip`, `WaitIndicator`; `lib/status.ts` gains host labels for attendance and match states.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-04 | How the host reaches the board | DECIDED 2026-11-25: (a) `GET /api/v1/host/games` + "Games you host" on the home page |
| OD-14 | Wait indicator format | DECIDED 2026-11-25: minutes only |
| D-1…D-7 | Token format, token in this feature, guest on host route, refresh, Suggest button, server time, old internal routes | DECIDED 2026-11-25: all as recommended (HMAC token, in this feature, 401, Refresh button, disabled Suggest, asOf, old routes later) |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-25 | Owner asked to continue with sprint 3 and design its next feature; `design.md` written (2 public + 1 internal route, identity token, no migration; OD-04, OD-14, D-1…D-7 open) and task list in `implement.md`. Status `WAITING_APPROVAL` | Claude, conversation 2026-11-25 |
| 2026-11-25 | Gate 1 (spec) approved by the owner: OD-04 = (a) "Games you host" + `GET /api/v1/host/games`; D-1 = (a) own HMAC-SHA256 identity token, secret `INTERNAL_TOKEN_SECRET`; D-3 = (a) guest cookie only → 401; OD-14 minutes only, D-2 token built here, D-4 Refresh button, D-5 Suggest disabled "Coming soon", D-6 `asOf`, D-7 old internal routes later; copy table approved; new internal route `POST /internal/v1/players/levels` approved with the design. Status `APPROVED` | Owner, conversation 2026-11-25 |
| 2026-11-25 | Plan written in `implement.md`: tasks T01–T09, test cases TC-01…TC-33; clarification: identity token only on `HOST` routes; test-only secret in `backend/pom.xml`. Waiting for Gate 2 | Claude, conversation 2026-11-25 |
| 2026-11-25 | Gate 2 (plan) approved by the owner: "duyệt plan" — tasks T01–T09 and test cases TC-01…TC-33 in implement.md, incl. the two clarifications (token only on `HOST` routes; test secret in `backend/pom.xml`). Status `IN_PROGRESS`; branch `feature/host-board` from `main` (0 behind `origin/main`) | Owner, conversation 2026-11-25 |
| 2026-11-25 | Build loop done: T01–T09, one fix round, 2 review rounds (flow-reviewer 81 → 88/100, all override gates PASS; scope-guardian; privacy-reviewer no high/medium), `PIPELINE OK`, live check on Docker. Status `IN_REVIEW`; open for the owner in review.md: 403 page link, empty `.env.example` secret vs §0 first-run, token audience before host writes, deployment gate | Claude, review.md |
| 2026-11-26 | Owner accepted all review recommendations ("duyệt hết như đề xuất"): 403 page links home (no new field or route); `.env.example` ships a working local-only `INTERNAL_TOKEN_SECRET` (§0 first run works) and a real random secret joins the pre-deployment gate, with clock-skew allowance for multi-host; token audience / method / path binding decided in the Attendance management design; findings 14, 15, 18 kept as later cleanup / notes. Status stays `IN_REVIEW` until the visual check and commit | Owner, review.md |
