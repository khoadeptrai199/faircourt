# Frontend screens and API calls — FairCourt MVP

Source: [FairCourt_Project_Blueprint.md](../FairCourt_Project_Blueprint.md) §6, §16.
Backend routes are described in [backend.md](backend.md). If this file and the blueprint differ, the blueprint wins.

## Routes and the API each one calls

All API calls go to `/api/v1/...` on the same origin; locally Vite proxies `/api` to the backend (§0).

| Route | Screen (§6) | API call (§13) | Primary action | Empty / error states (§16) | Status |
|---|---|---|---|---|---|
| `/` | Home page (owner-approved 2026-11-12, not a §16 route) | none | — ("Host login" disabled, "Coming soon") | — | Implemented |
| `/s/:publicCode` | Public session page | `GET /public/sessions/{publicCode}` | `Join session` | Unknown code → friendly 404; closed → read-only | Not started |
| `/s/:publicCode/join` | Visitor check-in | `GET /public/sessions/{publicCode}`, `POST /public/sessions/{publicCode}/guests` | `Join as guest` | Validation in place; pending-approval panel after submit | Not started |
| `/my/sessions/:sessionId` | My game | `GET /sessions/{sessionId}/me`, `POST /sessions/{sessionId}/me/feedback` | `View my court` / `How was the match?` | Pending, Ready/no match, Assigned, Playing, Finished/closed, expired guest access | Not started |
| `/host/sessions/:sessionId` | Host board | `GET /host/sessions/{sessionId}/board`, `PATCH .../attendance/{attendanceId}`, `PATCH /host/matches/{matchId}/start\|finish\|cancel`, `POST .../assessments` | `Suggest matches` | No ready players, fewer than four, occupied courts, request failure | Not started |
| `/host/sessions/:sessionId/review` | Match review | `POST /host/sessions/{sessionId}/suggestions`, `POST /host/sessions/{sessionId}/matches` | `Start match` | Wide gap warning, stale suggestion `409`, Cancel/Confirm | Not started |

Login (`POST /auth/login`, `GET /auth/csrf`) arrives with the identity slice (§17 step 2). The blueprint does not name a login route path yet.

## Rules for API use (§16)

- The player page calls only its own `GET /sessions/{id}/me`. It never loads the host board and hides fields in CSS.
- The guest secret lives in an HttpOnly cookie; React never reads or stores it.
- After an assignment the player page may poll its own endpoint every 15 seconds while visible, plus a Refresh button. No WebSocket.
- One small fetch wrapper handles credentials, CSRF, typed errors and loading. DTO types live in `src/api/types.ts`.
- No global state library until several screens really share changing state.

## Page-state matrix (§16)

- Every fetch: `loading / success / empty / error`.
- Every host mutation: `idle / saving / success / conflict`; buttons disable while saving.
- `409` → "This lineup changed. Refresh suggestions" (keep the host's session).
- `403` → "You don't manage this session" and offer the public page.
- Guest cookie expired → "Check in again and tell the host to remove your old entry."

## Planned folder layout (§16)

```text
frontend/src/
  api/            client.ts, types.ts, sessions.ts, matches.ts
  components/     Button.tsx, StatusBadge.tsx, CourtCard.tsx, EmptyState.tsx
  features/       public-session/, join/, my-game/, host-board/, match-review/
  routes/         route definitions and access guards
  styles/         tokens.css, globals.css
```

Today only `features/home/` and `styles/` exist.

Component inventory: `SessionHero`, `QuestionStep`, `StatusBadge`, `CourtCard`, `PlayerChip` (host only), `WaitIndicator` (host only), `MatchProposal`, `SwapSelector`, `MyAssignmentCard`, `EmptyState`, `ErrorBanner`, `ConfirmDialog`.

## Design tokens (§6, §16)

| Token | Value | Use |
|---|---|---|
| Background | `#F7F8F4` | Page background |
| Surface | `#FFFFFF` | Cards |
| Ink | `#10202A` | Body text, navigation |
| Action | `#157B63` | Primary actions, active court |
| Highlight | `#DDF7E8` | Soft highlights, success panels |
| Accent | `#F9C94D` | Session/next-match accent; not small body text |
| Error | `#B64243` | Conflicts, failed actions |

Type: 16px body, 14px supporting, 20px card title, 28px page title. 8px spacing grid; card padding 16px phone / 24px desktop; max width 1200px host, 720px player; buttons ≥ 44px high. Under 768px one column with compact bottom navigation. Status always shows icon/text together with color. Tokens are defined in `frontend/src/styles/tokens.css`.
