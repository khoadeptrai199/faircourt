# F05 design — Own attendance status + member join

Status: `APPROVED` 2026-11-20 by the owner (D-1…D-9 as recommended; copy rows approved). Naming: the play event is a **game** (OD-22); older blueprint routes `/sessions/...` read as `/games/...`. Security standard: the lessons of Account login and Guest check-in (OWASP Authentication, Session Management, Authorization cheat sheets): identity only from the gateway's session or the guest cookie, never from the browser's ids.

## 1. Summary

Two things, both about "me in this game":

1. **Member join.** A signed-in account presses Join game on the public page and becomes `READY` at once (blueprint §11). Joining again returns the same entry.
2. **My game page** `/my/games/:gameId`. A member or a guest sees only their own state (`PENDING`, `READY`, …). The assignment part (match number, court) is always `null` in this feature; it is filled by "My game: own assignment" (F12). The page has its own small endpoint, it never loads the host board (§16).

Who does what (every service touches only its own tables, AD-11, AD-12):

| Service | Does |
|---|---|
| gateway | Reads who the caller is (account session or guest cookie), answers the two public routes, calls playmatch with the resolved id as an explicit argument (never the cookie) |
| playmatch-service | Owns `attendance`: inserts the member's `READY` row, reads the caller's own row |
| player-service | Owns `player_profile`: finds or creates the member's profile (the seeded host has none, OD-11) |

## 2. Blueprint references

- §3: "View **own** match number, court number and start status: Joined player yes, host yes (as a player); another player's assignment: no."
- §11: "Regular player joins once, enters `READY`"; guest `PENDING` until approved.
- §13: `POST /sessions/{sessionId}/join` — "Signed-in player: Join once with member profile; return own attendance." `GET /sessions/{sessionId}/me` — "Member or scoped guest: own attendance state, optional own `matchNo`, `courtNo`, `matchState`; no other players." Example: `{"attendanceState":"ASSIGNED","assignment":{…}}`, or `assignment: null` while waiting.
- §16: `/my/sessions/:sessionId` (here `/my/games/:gameId`): "One large Court/Match card, status, refresh hint. States: Pending, Ready/no match, Assigned, Playing, Finished/closed, expired guest access." "The player's page does not load the host board then hide fields in CSS."
- §12: guest cookie rules (HttpOnly, hash only, scoped to its game and attendance).
- Decisions: OD-02 (public summary returns the opaque `gameId`), OD-11 (the host may play as a member), OD-22 (naming); AD-15 (internal routes), AD-16 (orchestration).

## 3. API contract

### Public routes (answered by the gateway; both are **new routes, need approval**, and are listed in blueprint §13)

**Member join**

| Item | Value |
|---|---|
| Route | `POST /api/v1/games/{gameId}/join` (§13) |
| Caller | signed-in account; CSRF token required; no body (an empty or `{}` body is accepted, anything else is ignored) |
| Identity source | the gateway's account session only. `gameId` in the path names the game, never the caller |
| Response (first time) | `201 {"attendanceState":"READY"}` |
| Response (joined before) | `200 {"attendanceState":"<state now>"}`, no new row (D-3) |
| Allowed response keys | `attendanceState` only |
| Errors | 401 `UNAUTHENTICATED` (no account session); 403 `CSRF_TOKEN_INVALID`; 404 `NOT_FOUND` (unknown or malformed `gameId`); 409 `STATE_CONFLICT` (game `CLOSED`, or the player was removed from this game, D-4); 503 `SERVICE_UNAVAILABLE` |

**My attendance**

| Item | Value |
|---|---|
| Route | `GET /api/v1/games/{gameId}/me` (§13) |
| Caller | account session **or** guest cookie. If both exist the account wins (README design note) |
| Identity source | account session user id, or the guest cookie resolved through auth-service to an attendance id |
| Access check | playmatch returns a row only if it belongs to the resolved caller **and** to `gameId`; anything else is `404` (no hint whether the game or the person is unknown) |
| Response | `200 {"attendanceState":"READY","assignment":null}`; `Cache-Control: no-store` |
| Allowed response keys | `attendanceState`, `assignment` (always `null` here; its keys come with F12) |
| Errors | 401 `UNAUTHENTICATED` (no account session and no guest cookie); 401 `GUEST_ACCESS_EXPIRED` (a guest cookie was sent but is expired, revoked or unknown, D-5); 404 `NOT_FOUND` (this caller has no entry in this game, or another game's guest cookie); 503 |

`gateway` route table gets a new access kind `PLAYER` (account or guest cookie) next to `PUBLIC` and `ACCOUNT`; the controller does the real check because only it can read the cookie (same pattern as the guest check-in route).

### Internal routes (AD-15, **new, need approval**; never routed by the gateway to the browser)

| Service | Route | Called by | Request → response |
|---|---|---|---|
| player | `POST /internal/v1/players/members` | playmatch | `{userId, displayName}` → `{playerId}`. Finds the `MEMBER` profile of this account or creates it (band `null`, status `SELF_REPORTED`); idempotent; unique `user_id` makes a parallel call return the same profile |
| player | `GET /internal/v1/players/by-user/{userId}` | playmatch | → `{playerId}`, or `404` if the account has no profile |
| playmatch | `POST /internal/v1/games/{gameId}/member-attendances` | gateway | `{userId, displayName}` → `{attendanceState, created}`; 404 / 409 / 503 |
| playmatch | `GET /internal/v1/games/{gameId}/my-attendance?userId=…` or `?attendanceId=…` (exactly one) | gateway | → `{attendanceState}`; `404` if not this caller's row in this game |

The member display name is chosen by the gateway (D-1) and copied into `attendance.display_name`, like guests.

## 4. Data and transactions

**No migration.** `attendance` (`state`, `display_name`, `UNIQUE (game_id, player_id)`) and `player_profile` (`user_id UNIQUE`, `kind MEMBER`) already allow everything here.

Member join in playmatch (one orchestration, no distributed transaction, AD-16):

1. Find the game by id (404), refuse `CLOSED` (409).
2. Ask player-service for the member's profile (create if missing). Down → 503, nothing written.
3. One local transaction: `INSERT INTO attendance … state 'READY' ON CONFLICT (game_id, player_id) DO NOTHING`; then read the row. If a row existed before, answer `200` with its state and write nothing; if its state is `LEFT`, answer 409 (D-4).
4. Return `{attendanceState, created}`.

Failure cases:

| Failure | Result |
|---|---|
| player-service down | 503, nothing created |
| profile created, then the attendance insert fails | a member profile without attendance stays (harmless, it is the account's own profile and is reused next time); the caller sees 500/503 and can press Join again |
| two parallel joins (double tap) | one row (unique key); both answers carry the same state, one is 201 and the other 200 |

`GET /me` is read-only: playmatch selects only `state` of the caller's own row (`MyAttendanceQuery`, `JdbcClient`, named parameters). Member path: playmatch asks player-service `by-user` for the `playerId` (404 → "not joined"), then selects `attendance` by `(game_id, player_id)`. Guest path: select by `(id = attendanceId, game_id)`.

## 5. Domain rules

- A member joins **once per game**; a repeat returns the existing entry. The first join is `READY` (§11), `available_since` = join time (default).
- The host may join their own game as a member (OD-11 = b). Nothing else is special about the host in this feature; their status in the host board is the Host board feature's business.
- A guest never joins as a member and never gets an account; a guest's cookie is scoped to one game and one attendance (§3, §12).
- `LEFT`: the entry stays (history); the caller reads `LEFT` on their page; a member cannot silently re-enter (D-4); a guest checks in again as a new guest (existing rule).
- No attendance cap (owner rule).
- `assignment` is always `null` here. State enum values are sent as the DB strings (`PENDING`, `READY`, `ASSIGNED`, `PLAYING`, `RESTING`, `LEFT`); the page already has labels for the first two and `LEFT`, others are shown generically until F12.

## 6. Backend components

| File | Service | New/changed | Notes |
|---|---|---|---|
| `PlayerAccessController` (+ `RouteAccess.PLAYER`), `GatewayRouteTable` rows `member-join`, `my-attendance`; `PlaymatchClient` (+2 calls); `GuestCookie` reused | gateway | new / changed | Resolves identity, builds the 2 responses, `no-store`; `GUEST_ACCESS_EXPIRED` mapping |
| `MemberAttendanceController`, `MemberAttendanceService`, `MyAttendanceQuery`, `PlayerClient` (+2 calls), `SecurityConfig` (+2 internal matchers) | playmatch | new / changed | `com.faircourt.playmatch.attendance`; clock and tx per conventions |
| `MemberProfileController`, `MemberProfileService` | player | new | `com.faircourt.player.member`; find-or-create in one transaction |
| tests | all | new | see section 9; every new route adds rows to `GatewayAccessMatrixIT` and the owning service's `AccessMatrixIT`; `JsonAllowlist` on both responses |

No change in auth-service (the existing `guest-credentials/resolve` is reused).

## 7. Frontend

- **Routes:** `/my/games/:gameId` (new), existing `/g/:publicCode` and `/g/:publicCode/join` (changed). Data: `GET /api/v1/games/{gameId}/me` through `src/api/attendance.ts` (`getMyAttendance`, `joinAsMember`), types in `api/types.ts` (`MyAttendance`, `AttendanceState`).
- **Files:** `src/features/my-game/MyGamePage.tsx` (renders by exhaustive `switch` on `useAsync` status and on state); `src/features/my-game/StateCard.tsx` (one large card, no court yet); public page `GameHero` / `PublicGamePage` (changed); `JoinPage` pending panel (changed); `AppRoutes.tsx`; `copy/en.ts`; `lib/status.ts` (attendance labels via `StatusBadge`, text + colour).
- **Public page, Join button:** not signed in → as today (link to `/g/<code>/join`, guest check-in). Signed in (`useAccount`) → the button calls member join (`useMutation`, disabled while saving) and then goes to `/my/games/<gameId>`. Closed game → disabled as today.
- **Guest after check-in:** the pending panel gets a link "See my status" to `/my/games/<gameId>` (the join page already holds the public summary with the id).
- **States of My game:** loading; `PENDING` ("Waiting for host approval", §13 text); `READY` ("You're in. Waiting for a match."); `ASSIGNED`/`PLAYING` (shown generically, details come with F12); `RESTING`; `LEFT`; error (503 banner with Try again); `404` (not joined: "You haven't joined this game yet." + link to the public page); `401` (guest access gone or expired: the §16 text, link to check in again). The page shows a **Refresh** button and no polling (polling comes with F12). A closed game still shows the state.
- **Layout:** 320–767px: one centered card, large status, Refresh below; 768px+: same card, max width ~720px (§16). Targets ≥ 44px, focus visible, status never by colour alone.

### Copy

| Key | Text | Source | Approved |
|---|---|---|---|
| `myGame.title` | My game | NEW (page heading; §16 route name) | yes (2026-11-20) |
| `myGame.pending` | Waiting for host approval | §13 | yes (blueprint) |
| `myGame.pendingBody` | The host will confirm you. Keep your phone handy. | reuses `join.pendingBody` | yes |
| `myGame.ready` | You're in. | NEW | yes (2026-11-20) |
| `myGame.readyBody` | Waiting for a match. | NEW | yes (2026-11-20) |
| `myGame.resting` | Resting | NEW | yes (2026-11-20) |
| `myGame.left` | You left this game. | NEW | yes (2026-11-20) |
| `myGame.inMatch` | You have a match. | NEW (shown for `ASSIGNED`/`PLAYING` until the assignment card exists) | yes (2026-11-20) |
| `myGame.refresh` | Refresh | NEW (§16 "refresh hint") | yes (2026-11-20) |
| `myGame.notJoined` | You haven't joined this game yet. | NEW | yes (2026-11-20) |
| `myGame.expired` | Check in again and tell the host to remove your old entry. | §16 | yes (blueprint) |
| `myGame.checkInAgain` | Check in again | NEW | yes (2026-11-20) |
| `myGame.backToGame` | Back to the game | NEW | yes (2026-11-20) |
| `join.seeStatus` | See my status | NEW | yes (2026-11-20) |
| `join.joining` | Joining… | NEW (saving label of the member Join button) | yes (2026-11-20) |
| errors | reuse `common.errorTitle` and Try again | existing | yes |

## 8. Security and privacy checklist

- [ ] Identity only from the account session or guest cookie; ids in path/body name the target only
- [ ] `404` when the row is not the caller's in that game; no hint if the game or person exists
- [ ] `/me` answers only `attendanceState` and `assignment`; no names, bands, ids, other players, host board
- [ ] CSRF required on member join; no CORS change; `no-store` on both answers
- [ ] The cookie never reaches playmatch or player-service (explicit ids only); HeaderSanitizer unchanged
- [ ] No names, emails or tokens in logs; the member name is not sent to anyone but playmatch
- [ ] Account wins over guest cookie; a guest cookie of another game gives 404, not another game's data
- [ ] The page calls its own `/me`, never the host board

## 9. Test plan (cases are detailed at Gate 2 in `implement.md`)

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01, AC-04 | Member joins: `201 READY`, one row; again: `200`, same row; double parallel join → one row | playmatch `MemberAttendanceIT` |
| TC-02 | AC-01 | Guest reads own `PENDING`; member reads own `READY` | gateway + playmatch IT |
| TC-03 | AC-02 | Access matrix: anonymous 401; guest of another game 404; account that has not joined 404; another account cannot read this one's row | `GatewayAccessMatrixIT`, `AccessMatrixIT` |
| TC-04 | AC-03 | `/me` keys ⊆ `{attendanceState, assignment}`; join keys = `{attendanceState}` | `JsonAllowlist` |
| TC-05 | AC-05 | Join a `CLOSED` game → 409, row counts unchanged | IT |
| TC-06 | AC-06 | Expired/revoked/unknown guest cookie → 401 `GUEST_ACCESS_EXPIRED`; no credentials → 401 `UNAUTHENTICATED` | gateway IT |
| TC-07 | — | Host account (no profile) joins: profile created, `READY`; second call reuses the profile | player + playmatch IT |
| TC-08 | — | player-service down → 503, no attendance row; `LEFT` member joins → 409 | IT |
| TC-09 | — | Schema boundary and access-matrix rows for the 4 internal and 2 public routes | boundary tests |
| TC-10 | AC-06 | `/my/games/:gameId` at 320 / 375 / 1280px, all states | visual (owner) |
| TC-11 | — | Live check on the Docker stack (join, status as member and guest, logs without personal data) | manual script |
| TC-12 | — | Pre-commit pipeline | pipeline |

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | **Display name of a new member profile.** Accounts have only an email; the seeded players already have names ("Player 1…"), the host has none | (a) the part of the email before `@` (host → `host`) · (b) a fixed word ("Host") for the host only · (c) ask the player a name on first join (adds a form) | (a). The host board shows it to the host only; real name editing is a later profile feature. Honest cost: it exposes the email's first part to the host |
| D-2 | The 4 internal routes and the 2 public routes of section 3 (new routes between services and to the browser) | approve / change | approve |
| D-3 | Second member join answers `200` with the current state (first time `201`), same pattern as guests | approve / change | approve |
| D-4 | A member whose entry is `LEFT` presses Join again | (a) `409` "removed from this game" (the host's decision stands) · (b) put them back to `READY` | (a). The blueprint is silent; the host-removal rules belong to Attendance management |
| D-5 | Expired guest access: `401 GUEST_ACCESS_EXPIRED` only when a cookie was sent and no longer valid; without any credential plain `401 UNAUTHENTICATED`. The page shows the same "Check in again…" text for both. Limit: a cookie that the browser already dropped at its end time looks like "no credential" | approve / change | approve |
| D-6 | Signed-in members on the public page: Join button joins directly (no questions) and opens My game | approve / change | approve |
| D-7 | Refresh button, no polling now (polling is F12) | approve / change | approve |
| D-8 | The pending panel link "See my status" for guests (needed because guests have no other way to reach their page) | approve / change | approve |
| D-9 | Copy table in section 7 (all `no` rows) | approve / edit | approve |

## 11. Out of scope

- Assignment content (match, court, partner) → My game: own assignment (F12); polling → F12
- Host approving, resting or removing players → Attendance management (F07); host board → F06
- Feedback → F13; own profile or name editing → later backlog
- Discovery of "my games" for a signed-in member (a list) → Discovery (after one game works end to end)
