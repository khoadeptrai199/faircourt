# Frontend screens and API calls — FairCourt MVP

Source: [FairCourt_Project_Blueprint.md](../FairCourt_Project_Blueprint.md) §6, §16.
Backend routes are described in [backend.md](backend.md). If this file and the blueprint differ, the blueprint wins.
Naming (owner decision 2026-11-18): the blueprint's "session" is **game** (`/s/` → `/g/`, `/sessions/` → `/games/`); a single contest is a match (set in the UI). Router: `react-router` 8.5.1.

## Routes and the API each one calls

All API calls go to `/api/v1/...` on the same origin; locally Vite proxies `/api` to the backend (§0).

| Route | Screen (§6) | API call (§13) | Primary action | Empty / error states (§16) | Status |
|---|---|---|---|---|---|
| `/` | Home page (owner-approved 2026-11-12, not a §16 route) | `GET /me` (header: "Host login" link, or email + "Sign out"); when signed in `GET /host/games` ("Games you host" with a Manage link per game and a "Create a game" button, OD-04, OD-05) | `Host login` / `Sign out` | Not hosting any game → "You are not hosting any games yet." | Implemented ("Games you host" 2026-11-25; "Create a game" button 2026-11-26) |
| `/host/games/new` | Create a game (OD-05, owner 2026-11-26; not a §16 route) | `GET /me` (sign-in check), `POST /games` | `Create game` | Not signed in → `/login?next=/host/games/new`; field errors under each field (focus on the first); saving → all inputs disabled, "Creating…"; failure → one error banner "The game was not created. Try again.", input kept; success → the new game's host board. Times typed in Vietnam time; an end at or before the start is the next day ("Ends the next day") | Implemented 2026-11-26 (owner visual check 2026-11-26) |
| `/login` | Host login (design D-1; `?next=` same-site path only) | `GET /auth/csrf`, `POST /auth/login`, `GET /me` | `Sign in` | Empty fields, wrong credentials (one message), too many attempts (429), service down → Try again | Implemented 2026-11-18 (visual check pending) |
| `/forgot-password` | Ask for a code (design F21) | `GET /auth/csrf`, `POST /auth/password/forgot` | `Send code` | Empty email, service down → Try again; the answer never says whether the email has an account | Implemented 2026-11-18 (visual check pending) |
| `/reset-password` | Code + new password twice (email carried in router state from the previous page) | `POST /auth/password/reset` | `Change password` | Wrong or expired code (one message), password rules, mismatch, service down → Try again; success → link to `/login`, no automatic login | Implemented 2026-11-18 (visual check pending) |
| `/g/:publicCode` | Public game page | `GET /public/games/{publicCode}` | `Join game` | Unknown code → friendly 404; closed → read-only; any other failure (incl. 503) → error with Try again | Implemented 2026-11-18 (`Join game` shown disabled with "Coming soon" until guest check-in) |
| `/g/:publicCode/join` | Visitor check-in | `GET /public/games/{publicCode}`, `POST /public/games/{publicCode}/guests` | `Join as guest` | Validation in place; pending-approval panel after submit | Implemented (Guest check-in, 2026-11-18; visual check pending) |
| `/my/games/:gameId` | My game | `GET /games/{gameId}/me`, `POST /games/{gameId}/me/feedback` | `View my court` / `How was the match?` | Pending, Ready/no match, Assigned, Playing, Finished/closed, expired guest access | Implemented: attendance state (Own attendance status, 2026-11-20) and own assignment card "Your next match / Court n / Match n" or "Playing now", polling every 15 s while visible + Refresh (My game, 2026-11-27); feedback later |
| `/host/games/:gameId` | Host board | `GET /host/games/{gameId}/board`, `PATCH /host/games/{gameId}/attendance/{attendanceId}` (Approve, Reject, Mark as left; confirm dialog before Reject / Mark as left; all buttons disabled while saving; 409 → "This player's status changed. The board was refreshed."; closed game → read-only note) and `PATCH /host/matches/{matchId}/start\|finish\|cancel` from the court card (planned match: `Start match` + `Cancel match`; active match: `Finish` + `Cancel match`; confirm dialog before Finish and Cancel, none before Start; all board buttons disabled while one saves; 409 → "This match changed. The board was refreshed."; no court actions on a closed game) (later: `POST .../assessments`) | `Suggest matches` (link to `/host/games/:gameId/review`; disabled with closed note on a closed game) | No ready players, fewer than four (two for singles), request failure with Try again, 403 "You don't manage this game." with "Back to home" (owner 2026-11-26), 401 → `/login?next=…`, unknown game → not found; Refresh button, no polling | Implemented 2026-11-25 (read); attendance actions 2026-11-26 (visual check pending); court actions 2026-11-27 (Match start / finish / cancel; visual check pending) |
| `/host/games/:gameId/review` | Match review | `POST /host/games/{gameId}/suggestions` (then `GET /host/games/{gameId}/board` for names and levels), `POST /host/games/{gameId}/matches` (Confirm, one court at a time) | Tap a player, then another, to swap (same court, across courts, or with "Ready, not picked"; keyboard works, no drag-and-drop); `Confirm` per court; `Refresh suggestions`, `Back to board` (Start lives on the board court card, Match start / finish / cancel) | Wide gap and unknown-level warnings as text + icon (hidden on a court the host changed: "You changed this court."), no free court, fewer players than a match, closed game `409`, confirm `409` → "This lineup changed. Refresh suggestions" on that court with other edits kept, saved court → "Saved as match N." + Next up, all swaps and Confirms disabled while one court saves, 403 not-your-game message, 401 -> login, request failure with Try again | Suggestions view 2026-11-26; swap + confirm implemented 2026-11-26 (Match confirm; visual check pending) |

Login: `POST /auth/login`, `POST /auth/logout`, `GET /me` (Account login, 2026-11-18). The CSRF token is fetched from `GET /auth/csrf`, kept only in memory and sent in the header the gateway names; it is forgotten at login and logout. The blueprint does not name a login route path yet.

## Rules for API use (§16)

- The player page calls only its own `GET /games/{id}/me`. It never loads the host board and hides fields in CSS.
- The guest secret lives in an HttpOnly cookie; React never reads or stores it.
- After an assignment the player page may poll its own endpoint every 15 seconds while visible, plus a Refresh button. No WebSocket. Implemented in `hooks/usePolling.ts` (visible tab only; one read right after the tab becomes visible; stops after 401/404 and at Left, keeps going after 503) and `useAsync.refresh()` (re-reads without showing "Loading").
- One small fetch wrapper handles credentials, CSRF, typed errors and loading. DTO types live in `src/api/types.ts`.
- No global state library until several screens really share changing state.

## Page-state matrix (§16)

- Every fetch: `loading / success / empty / error`.
- Every host mutation: `idle / saving / success / conflict`; buttons disable while saving.
- `409` → "This lineup changed. Refresh suggestions" (keep the host's state).
- `403` → "You don't manage this game" and offer the public page (§16). Host board: links home instead, because the 403 answer has no public code (owner 2026-11-26).
- Guest cookie expired → "Check in again and tell the host to remove your old entry."

## Planned folder layout (§16)

```text
frontend/src/
  api/            client.ts, types.ts, games.ts, matches.ts
  components/     Button.tsx, StatusBadge.tsx, CourtCard.tsx, EmptyState.tsx
  features/       public-game/, join/, my-game/, host-board/, match-review/
  routes/         route definitions and access guards
  styles/         tokens.css, globals.css
```

Today: `api/` (client, errors, types, games), `components/` (Button, StatusBadge, EmptyState, ErrorBanner, BackHomeLink), `copy/`, `hooks/` (useAsync), `lib/` (time, money, status), `routes/`, `features/home/`, `features/public-game/` (PublicGamePage, GameHero), `styles/`.

Component inventory: `GameHero`, `QuestionStep`, `StatusBadge`, `CourtCard`, `PlayerChip` (host only), `WaitIndicator` (host only), `MatchProposal`, `SwapSelector`, `MyAssignmentCard`, `EmptyState`, `ErrorBanner`, `ConfirmDialog`.

## Design tokens (§6, §16)

| Token | Value | Use |
|---|---|---|
| Background | `#F7F8F4` | Page background |
| Surface | `#FFFFFF` | Cards |
| Ink | `#10202A` | Body text, navigation |
| Action | `#157B63` | Primary actions, active court |
| Highlight | `#DDF7E8` | Soft highlights, success panels |
| Accent | `#F9C94D` | Game/next-match accent; not small body text |
| Error | `#B64243` | Conflicts, failed actions |

Type: 16px body, 14px supporting, 20px card title, 28px page title. 8px spacing grid; card padding 16px phone / 24px desktop; max width 1200px host, 720px player; buttons ≥ 44px high. Under 768px one column with compact bottom navigation. Status always shows icon/text together with color. Tokens are defined in `frontend/src/styles/tokens.css`.
