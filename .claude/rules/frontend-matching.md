---
paths:
  - "frontend/**"
  - "backend/**/matching/**"
---

# Frontend and matching engine (blueprint §5, §6, §14, §16)

## Part A — Frontend (§6, §7, §16)

Stack: React + TypeScript + Vite, Tailwind CSS with explicit tokens and a small in-house component set (§7). Exact versions not chosen yet — propose, get approval, pin.

### Structure (§16)

```text
frontend/src/
  api/          client.ts, types.ts, sessions.ts, matches.ts
  components/   Button.tsx, StatusBadge.tsx, CourtCard.tsx, EmptyState.tsx
  features/     public-session/, join/, my-game/, host-board/, match-review/
  routes/       route definitions and access guards
  styles/       tokens.css, globals.css
```

- API DTO types in `src/api/types.ts`. One small fetch wrapper handles credentials, CSRF, typed errors, loading.
- Route-specific state stays in each page. No global state library (§16).

### Routes (§16)

| Route | Data | Empty/error states |
|---|---|---|
| `/s/:publicCode` | public summary | unknown code → friendly 404; closed → read-only |
| `/s/:publicCode/join` | public summary + guest POST | in-place validation; pending-approval panel after submit |
| `/my/sessions/:sessionId` | own `/me` only | pending, ready/no match, assigned, playing, finished/closed, expired guest access |
| `/host/sessions/:sessionId` | host board | no ready players, fewer than four, occupied courts, request failure |
| `/host/sessions/:sessionId/review` | suggestion preview | wide gap warning, stale 409, Cancel/Confirm |

- Player page polls its own endpoint every 15 s while visible + explicit Refresh button. No WebSocket (§16).
- Swap = tap one player then another; works with keyboard; no drag-and-drop required (§16).

### Components (§16)

`SessionHero`, `QuestionStep`, `StatusBadge`, `CourtCard`, `PlayerChip` (host only), `WaitIndicator` (host only), `MatchProposal`, `SwapSelector`, `MyAssignmentCard`, `EmptyState`, `ErrorBanner`, `ConfirmDialog`.

### Design tokens (§6, §16)

| Token | Value |
|---|---|
| Background | `#F7F8F4` |
| Surface | `#FFFFFF` |
| Text | `#10202A` |
| Action (court green) | `#157B63` |
| Pale highlight (mint) | `#DDF7E8` |
| Accent (shuttle yellow) — not small body text, not sole carrier of meaning | `#F9C94D` |
| Error | `#B64243` |

Typography: Inter or system sans; 16px body, 14px supporting, 20px card title, 28px page title. 8px spacing grid; card padding 16px phone / 24px desktop; radius 12–16px. Max width 1200px host, 720px player. Buttons ≥ 44px high. Under 768px: one column + compact bottom nav; 768px+: wider court/ready layouts. Must work from 320px width (§17).

Accessibility: text label + color for every status; visible keyboard focus; check real color contrast against WCAG 2.2 — do not assume a pair passes.

### Page states and copy (§6, §16)

- Every fetch: `loading / success / empty / error`. Every host mutation: `idle / saving / success / conflict`. Disable buttons while saving.
- `409` → "This lineup changed. Refresh suggestions" (keep the rest of the host's state).
- `403` → "You don't manage this session" + link to public page.
- Guest cookie expired → "Check in again and tell the host to remove your old entry."
- Guest join success → "Waiting for host approval".
- Other copy examples from §6: "You're in", "Your next match: Court 2", "This match may have a wide skill gap." Do not invent new user-facing copy without approval.

## Part B — Matching engine (§5, §14)

- Pure function, e.g. `List<Proposal> suggest(BoardSnapshot snapshot)`. No repository access, no mutation. Lives in `backend/.../matching/`.
- `BoardSnapshot`: ready attendance IDs, private bands (nullable), `availableSince`, `gamesPlayed`, last three partners per player, free court numbers.
- Suggestions write nothing; only host confirm persists (§11).

### Deterministic v1 policy (§14)

1. Pool = up to 24 `READY` players not in an unfinished match; only free courts.
2. `slots = 4 × min(freeCourts, floor(readyCount / 4))`.
3. Select: sort by `gamesPlayed ASC`, `availableSince ASC`, attendance UUID; take first `slots`.
4. Group: sort selected by effective band 1…4, then same tie-breakers. `null` band = 2 **for calculation only** + `UNKNOWN_LEVEL` warning.
5. Consecutive groups of four → free courts in court-number order.
6. For each four, test the 3 unique team splits; rank by `teamGap = |sum(A) − sum(B)|`, then repeat-partner count over each player's last three **finished** matches, then stable UUID order.
7. `spread = max − min` band; `spread ≥ 2` → `WIDE_SKILL_GAP`.
8. Fewer than four ready → `WAITING_FOR_PLAYERS`; no free court → `NO_FREE_COURT`.
9. Reason codes from rules only (e.g. `SIMILAR_LEVEL`, `BALANCED_TEAMS`, `LONG_WAIT`), never AI.

Court compatibility is scored before team balance (§5). This is intentionally a simple heuristic, not a global optimizer — do not "improve" it without owner approval.

### Required boundary tests (§14)

Four equal-band players · `[1,2,3,4]` warns about spread even when team sums tie · one unknown band · four zero-game players chosen before four one-game players · fewer than four ready · occupied court excluded · two simultaneous courts never reuse an attendance ID · same snapshot → same ordering. Assert policy and invariants, not internal implementation details.
