# F12 design — My game: own assignment + polling

Status: `APPROVED` (2026-11-27, by owner: all recommendations and the copy table)

## 1. Summary

Today `GET /api/v1/games/{gameId}/me` answers `{"attendanceState": …, "assignment": null}`: the `assignment` key exists (added by Own attendance status) but is always `null`, and the My game page shows only a state card with a Refresh button.

After this feature:

- **Backend** — playmatch-service reads the caller's own *unreleased* match (the `match_player` row with `released_at IS NULL`, joined to its `play_match`) in the same SQL statement as the attendance state, and returns `assignment: {matchNo, courtNo, matchState}` or `null`. The gateway passes it through with a typed record instead of `Object`. No new route, no new table, no migration.
- **Frontend** — a new `MyAssignmentCard` ("Your next match / Court 2", match number, status, arrival hint) replaces the state card while the player has a match; ASSIGNED and PLAYING get different labels (§11). A new `hooks/usePolling.ts` re-reads `/me` every 15 s while the page is visible, without flashing "Loading"; the Refresh button stays.

Reusable pieces added: `usePolling` (the single seam a future WebSocket replaces, system-design §13) and a "refresh without loading" mode in `useAsync`.

## 2. Blueprint references

- §3: "View **own** match number, court number and start status — Joined player: Yes; Anyone: No"; "View another player's assignment … — Joined player: No".
- §6: "**Player-view composition:** one prominent “Your next match / Court X” card, current status and a brief arrival instruction." Copy: "Your next match: Court 2".
- §11: "Player response contains only their own attendance status, match number, court number and match status. No opponent list, skill band or full court board in player/public APIs." "The UI must show `ASSIGNED` separately from `PLAYING`."
- §12 invariants: "one unfinished assignment per player" (`ux_player_in_unreleased_match`); "On finish/cancel, mark all four `match_player.released_at` and update their attendance states within the same transaction."
- §13: "`GET /sessions/{sessionId}/me` — Member or scoped guest — Own attendance state, optional own `matchNo`, `courtNo`, `matchState`; no other players." Example: `{"attendanceState":"ASSIGNED","assignment":{"matchNo":3,"courtNo":2,"matchState":"PLANNED"}}` "to that player only, or `assignment: null` while waiting."
- §16: route `/my/sessions/:sessionId` (here `/my/games/:gameId`, OD-22) — "One large Court/Match card, status, refresh hint"; states "Pending, Ready/no match, Assigned, Playing, Finished/closed, expired guest access". "The player's page does **not** load the host board then hide fields in CSS … the player page can poll its own endpoint every 15 seconds while visible and offer an explicit Refresh button; no WebSocket required."
- §17 step 4 gate: "four assigned players each see only their own match/court; a fifth sees no assignment."
- §0 step 5: "An anonymous request and a different signed-in player's request cannot read the guest's assignment."

## 3. API contract

### `GET /api/v1/games/{gameId}/me` (public route, changed response only)

| Item | Value |
|---|---|
| Route | `GET /api/v1/games/{gameId}/me` (§13; already in the gateway route table as `my-attendance`, access `PLAYER`) |
| Caller | member or scoped guest |
| Identity source | account session wins; else guest cookie resolved by auth-service (unchanged) |
| Access check | unchanged: own row only — member via `player_id` of the signed-in account's profile, guest via the attendance id bound to the cookie; the row must belong to `gameId` |
| Request | none |
| Response | `200`, `Cache-Control: no-store`. `{"attendanceState": "PENDING\|READY\|ASSIGNED\|PLAYING\|RESTING\|LEFT", "assignment": null \| {"matchNo": int, "courtNo": int, "matchState": "PLANNED\|ACTIVE"}}` |
| Allowed response keys | `attendanceState`, `assignment`, `assignment.matchNo`, `assignment.courtNo`, `assignment.matchState` — nothing else (no `matchId` unless the owner picks D-1 option b) |
| Errors | unchanged: `401 UNAUTHENTICATED` (no credential), `401 GUEST_ACCESS_EXPIRED` (guest cookie does not resolve), `404 NOT_FOUND` (no own row in this game / unknown game), `503 SERVICE_UNAVAILABLE` (auth / player / playmatch down or answer malformed) |

`matchState` is only ever `PLANNED` or `ACTIVE`: a `FINISHED` or `CANCELLED` match has released its players, so it is never the caller's unreleased match.

### `GET /internal/v1/games/{gameId}/my-attendance` (internal, changed response only)

Same route and parameters as today (`?userId=` or `?attendanceId=`, exactly one). Response gains `assignment` with the same shape as above. No new internal route. (Moving this older route from query ids to the identity token is the general backlog item from the Host board decision, not this feature.)

## 4. Data and transactions

- Tables read (playmatch schema only): `attendance`, `match_player`, `play_match`. Nothing written. No migration.
- One read-only SQL statement per call, so state and assignment come from the same snapshot:

  ```sql
  SELECT a.state, m.match_no, m.court_no, m.state AS match_state
  FROM attendance a
  LEFT JOIN match_player mp ON mp.attendance_id = a.id AND mp.game_id = a.game_id AND mp.released_at IS NULL
  LEFT JOIN play_match m ON m.id = mp.match_id AND m.game_id = a.game_id
  WHERE a.game_id = :game AND a.id = :attendance          -- guest
  -- or: WHERE a.game_id = :game AND a.player_id = :player -- member
  ```

  `ux_player_in_unreleased_match` guarantees at most one joined row; `ix_match_players_by_attendance` serves the join.
- Consistency: confirm / start / finish / cancel update `attendance.state`, `match_player.released_at` and `play_match.state` in one transaction (§12, Match confirm and Match lifecycle), so one statement never sees "ASSIGNED without a match". If it ever did (defensive), the page shows the plain state card (§7).

## 5. Domain rules and state transitions

No transition changes. Read mapping the page relies on:

| Attendance state | Unreleased match | `assignment` | Card |
|---|---|---|---|
| `PENDING` | none | `null` | state card "Waiting for host approval" (existing) |
| `READY` | none | `null` | state card "You're in. / Waiting for a match." (existing) — also what a player sees right after their match is finished or cancelled (§16 "Finished") |
| `ASSIGNED` | `PLANNED` | set | assignment card, "Your next match" |
| `PLAYING` | `ACTIVE` | set | assignment card, "Playing now" |
| `RESTING` | none | `null` | state card "Resting" (existing; no host button sets it today) |
| `LEFT` | none | `null` | state card "You left this game." (existing; final) |

"Closed" game (§16 "Finished/closed"): no route in §13 closes a game, so `game.state` is always `OPEN` in the MVP; this feature does not read it. See §10 D-3.

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `playmatch-service/.../attendance/MyAttendanceQuery.java` | changed | `*Query`, `JdbcClient`, named params | `byAttendance` / `byPlayer` return `Optional<MyAttendanceRow>` (state + optional assignment) from the SQL in §4 |
| `playmatch-service/.../attendance/MyAssignment.java` | new | value record | `int matchNo, int courtNo, MatchState matchState` (reuses the existing `match.MatchState` enum) |
| `playmatch-service/.../attendance/MyAttendanceResponse.java` | changed | response record | `(AttendanceState attendanceState, MyAssignment assignment)`; `assignment` may be `null` and is serialized as `null` |
| `playmatch-service/.../attendance/MemberAttendanceService.java` | changed | — | `myAttendance` maps the row; `join` keeps using the state only |
| `gateway/.../player/MyAttendanceResponse.java` | changed | response record | `Object assignment` → `MyAssignment assignment` |
| `gateway/.../player/MyAssignment.java` | new | record | `int matchNo, int courtNo, String matchState`; gateway accepts only `PLANNED`/`ACTIVE` and positive numbers, otherwise `503` (malformed downstream answer, same rule as a missing state today) |
| `gateway/.../guest/PlaymatchClient.java` | changed | `RestClient` | `myAttendance` returns state + assignment |
| `gateway/.../player/PlayerAccessController.java` | changed | thin controller | passes the assignment through |

## 7. Frontend

- Route and data request: `/my/games/:gameId` → `GET /api/v1/games/{gameId}/me` only (unchanged; never the host board).
- Components:
  - `features/my-game/MyAssignmentCard.tsx` (new, §16 inventory name): same large green card look as `StateCard`; `StatusBadge` with the match status label; big text "Court {n}"; line "Match {n}"; arrival hint per match state.
  - `features/my-game/StateCard.tsx` (unchanged look) for every state without an assignment.
  - `features/my-game/MyGamePage.tsx` (changed): picks the card, adds the refresh hint line under the Refresh button, wires polling.
  - `features/my-game/myCard.ts` (new, pure): maps the `/me` answer to "which card + which texts", so it can be unit-tested with Vitest without a DOM.
  - `hooks/usePolling.ts` (new): `usePolling(callback, intervalMs, enabled)`; runs `callback` every `intervalMs` only while `document.visibilityState === 'visible'`; pauses when hidden; runs once right away when the page becomes visible again; clears its timer on unmount.
  - `hooks/useAsync.ts` (changed, additive): new `refresh()` next to `reload()` — re-fetches without switching to `loading`, so a poll does not flash "Loading…". A refresh answer replaces the state exactly like a normal answer (success, empty or error).
  - `api/types.ts`: `MyAttendance.assignment: MyAssignment | null`, `MyAssignment = { matchNo: number; courtNo: number; matchState: 'PLANNED' | 'ACTIVE' }`.
  - `lib/status.ts`: `ATTENDANCE_STATUS.ASSIGNED` and `.PLAYING` get different labels (§11); new `MY_MATCH_STATUS` for the assignment card.
- Polling rules (D-2): every 15 s; on while the last answer was a success and the state is not `LEFT`; also on after a `503`/network error so the page recovers by itself; off after `401` or `404` (the person must act: check in again / join). Refresh button: always available on the success view, uses `refresh()`.
- States: loading (first load only) / success (state card or assignment card) / 404 not joined / 401 expired (existing) / other error → `ErrorBanner` with retry (existing).
- Layout: 320–767px — one full-width card, Refresh button full width under it, hint text under the button; 768px+ — same column centered, max width 720px (unchanged page frame).

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|
| `myGame.assigned` | Your next match | §6 "Your next match: Court 2" | 2026-11-27 |
| `myGame.playing` | Playing now | NEW (§11 "show ASSIGNED separately from PLAYING") | 2026-11-27 |
| `myGame.court(n)` | Court {n} | §6 "Court 2" | 2026-11-27 |
| `myGame.matchNo(n)` | Match {n} | NEW (§13 field `matchNo`) | 2026-11-27 |
| `myGame.assignedHint` | Go to the court. The host will start the match. | NEW (§6 "a brief arrival instruction") | 2026-11-27 |
| `myGame.playingHint` | Your match is on. | NEW | 2026-11-27 |
| `myGame.refreshHint` | This page updates every 15 seconds. | NEW (§16 "refresh hint") | 2026-11-27 |
| `myGame.inMatch` | You have a match. | existing (2026-11-20) — kept only for the defensive case "ASSIGNED/PLAYING but no assignment" | approved |

## 8. Security and privacy checklist

- [x] Identity derived from account session / guest cookie only (unchanged gateway code path)
- [x] No host route touched; no `AccessPolicy` change needed
- [x] Response contains only allowlisted fields: the SQL selects only the caller's own row and the caller's own match's `match_no`, `court_no`, `state` — no other `match_player` row, no team, no name, no band, no ids
- [x] No write; CSRF and CORS unchanged
- [x] No personal data in logs; no new log lines

## 9. Test plan

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Given a doubles game with 5 READY players and a host-confirmed match of 4 on court 1 (match 1), when each of the 4 reads own attendance, then each gets `ASSIGNED` + `{matchNo:1, courtNo:1, matchState:PLANNED}`; the fifth gets `READY` + `assignment: null` | playmatch IT |
| TC-02 | AC-01 | Given TC-01 after the host starts the match, when a player reads, then `PLAYING` + `matchState: ACTIVE` | playmatch IT |
| TC-03 | AC-01 | Given an active match that the host finishes (and separately one that the host cancels), when a player of it reads, then `READY` + `assignment: null` | playmatch IT |
| TC-04 | AC-01 | Given a guest in a planned match, when read by `attendanceId`, then the guest's own assignment; a member read by `userId` gets the member's own | playmatch IT |
| TC-05 | AC-01 | Given two matches on courts 1 and 2 in one game, when a player of court 2 reads, then court 2 and its match number, never court 1's | playmatch IT |
| TC-06 | AC-02 | Internal answer keys ⊆ `{attendanceState, assignment, assignment.matchNo, assignment.courtNo, assignment.matchState}` for both `null` and set | `JsonAllowlist` (playmatch IT) |
| TC-07 | AC-02 | Gateway `/me` with a stubbed assignment → same keys, values passed through; stubbed `matchState: "FINISHED"` or extra unknown keys → only allowlisted keys in the answer / `503` for a bad state | gateway IT (`PlayerAccessIT`, JDK stub) |
| TC-08 | AC-03 | Anonymous `GET /me` → `401`; a different signed-in account without a row in the game → `404`; another guest's cookie → that guest's own row only (never the first guest's assignment) | gateway IT + existing `GatewayAccessMatrixIT` rows stay green |
| TC-09 | AC-02, AC-03 | A player of game X reading game Y (where they have no row but a match exists) → `404`, no assignment | playmatch IT |
| TC-10 | AC-04 | `myCard` maps: no assignment → state card per state; PLANNED → "Your next match", "Court 2", "Match 3", assigned hint; ACTIVE → "Playing now", playing hint; ASSIGNED without assignment → state card "You have a match." | Vitest (pure) |
| TC-11 | AC-04 | Visual on the running stack: page open as guest; host confirms → within 15 s card shows Court/Match; host starts → "Playing now"; host finishes → "You're in."; switching tab away stops requests (DevTools network), coming back triggers one request at once; Refresh updates without "Loading…" | human check |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 (= OD-16) | Should the own assignment include `matchId`, so the later Match-fit feedback can send it? | (a) not now — decide in the Match-fit feedback design · (b) add `matchId` to `assignment` now | **(a)**. `assignment` exists only while the match is unreleased; feedback is for a *finished* match, when `assignment` is already `null` again. So a `matchId` here does not solve the feedback question — the feedback design needs its own field (for example "last finished match waiting for your answer"). Adding an unused id now only widens the player response. |
| D-2 | Polling behaviour | 15 s while visible, pause when hidden, immediate read when visible again, stop after `401`/`404`, continue after `503`/network error, stop at `LEFT` | As listed (technical, follows §16). |
| D-3 | "Finished/closed" state from §16 | (a) Finished = back to the Ready card; closed not handled (no close route in the MVP) · (b) add `gameState` to `/me` | **(a)**: §13 `/me` has no game state, and nothing sets `CLOSED` in the MVP. |
| D-4 | Copy texts in §7 marked NEW | approve / change | Approve or give your wording. |

Assumption: no new dependency (Vitest is already a dev dependency since Match confirm; the `myCard` test runs in its default Node environment, no DOM library).

## 11. Out of scope

- Opponents, partners, teams, bands on the player page → never (§11).
- `matchId` / feedback prompt → Match-fit feedback (F13).
- Closing a game → not in the MVP API (§13).
- WebSocket / push updates → later (system-design §13; Kafka backlog).
- Moving `/internal/v1/games/{gameId}/my-attendance` from query ids to the identity token → existing backlog item (Host board decision 2026-11-25).
