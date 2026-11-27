# F11 design — Match start / finish / cancel

Status: `APPROVED` (2026-11-27, by owner: D-1…D-5 as recommended, copy table)

## 1. Summary

The host moves a saved match through the rest of its life from the **court card on the host board**: **Start match** (planned → active, players Assigned → Playing), **Finish** (active → finished, players back to Ready, one set counted, wait time reset) and **Cancel match** (planned or active → cancelled, players back to Ready, no set counted). Each is one transaction with the match row locked; any illegal move answers `409 STATE_CONFLICT` with nothing changed. After a finish, the court is free and the next suggestion favours the players who waited (§0 step 4, §17).

Reusable pieces:

1. **`MatchState` transitions** (playmatch, AD-03): `canStart()`, `canFinish()`, `canCancel()` on the enum; one place for the §11 match state machine.
2. **`AttendanceState` match moves**: `ASSIGNED → PLAYING` on start, `ASSIGNED|PLAYING → READY` on finish/cancel, checked through the enum.
3. **`MatchService.start / finish / cancel`**: three `@Transactional` commands on the existing service, sharing one private "load and lock" step (resolve game from match → `requireHost` → lock game row `FOR SHARE` → match row `FOR UPDATE` → players' attendance rows `FOR UPDATE` by id). Same lock order as confirm, so no deadlock.
4. **`CourtActions`** (frontend, `features/host-board/`): the buttons on a court card plus the existing `ConfirmDialog` for Finish and Cancel.

No migration, no new dependency, no new internal route, no call to another service.

## 2. Blueprint references

- §0 step 4: "Host starts and finishes that match. The guest's own page changes status after refresh; the host can suggest another match. Four selected attendees are never assigned elsewhere simultaneously."
- §0 step 6: "Restart Compose without deleting volumes: the session and finished match remain."
- §3 table: "Generate suggestions; assign, swap, start or finish a match — only host". §3 flow 6: "Only the host taps **Finish**. The app saves who played together and who waited."
- §5 edge cases: "organizer starts a match then cancels".
- §6 screens: Match review primary action "`Start match`" (moved to the court card, Match confirm design D-7).
- §11: "This creates a `PLANNED` match, then host starts and finishes it." "Cancelling a planned match changes `ASSIGNED → READY`; cancelling an active match changes `PLAYING → READY`. … To mark an assigned/playing player `LEFT`, the host first cancels or finishes that match. Finishing a match changes all four players to `READY`, increments `games_played`, and resets their `available_since`; players who waited retain their earlier `available_since` and get priority next round." "Match states: `PLANNED → ACTIVE → FINISHED`, with `PLANNED → CANCELLED` and `ACTIVE → CANCELLED` allowed. A closed session allows read-only history and no new assignments. The UI must show `ASSIGNED` separately from `PLAYING`." "Score: No score entry … A completed match records participants and time only."
- §12: `play_match.started_at`, `finished_at`; `match_player.released_at`; "On finish/cancel, mark all four `match_player.released_at` and update their attendance states within the same transaction."
- §13: `PATCH /host/matches/{matchId}/start` — "Owning host — Planned → active; four attendance rows Assigned → Playing." `PATCH /host/matches/{matchId}/finish` — "Active → finished; release players, set Ready and update wait/game counters." `PATCH /host/matches/{matchId}/cancel` — "Planned/active → cancelled; release players and restore Ready." Status codes: "`401` … `403` … `404` unknown or private resource not visible, `409` state conflict."
- §15: "`MatchService.confirm(...)`, `start(...)`, `finish(...)`, and `cancel(...)` each run in a single `@Transactional` method. … Finishing marks match complete, releases four assignments, increments game counts and returns attendees to Ready together." "The host access policy … checks the persisted owner for **every** host mutation."
- §16: board "occupied courts" state; "every host mutation can be `idle / saving / success / conflict`"; "Buttons disable while saving"; component `ConfirmDialog`.
- §17 step 3 gate: "board, suggestion, tap-to-swap, confirm, start, finish, cancel. Gate: correct DB state …; no partial match rows." Checklist: "Court and player cannot be assigned twice while unfinished; finish/cancel releases both." "Second round accounts for time spent waiting."
- Owner decisions: OD-20 singles = 2 players per match; OD-19 `matches_played` = sets played; OD-22 route words; OD-25 (a) keep `ASSIGNED` (Start on the board court card); OD-07 cancel part open (D-1 below).

## 3. API contract

Three routes, same shape. §13 has no "session" word in them, so the paths stay as written.

| Item | Value |
|---|---|
| Routes | `PATCH /api/v1/host/matches/{matchId}/start`, `…/finish`, `…/cancel` (§13) |
| Caller | the host of the game this match belongs to |
| Identity source | account principal → identity token v2 (`aud=playmatch`, method + path bound) |
| Access check | gateway route rule (account required, CSRF required); service: `AccessPolicy.requireAccount` in the controller, then inside the command: match id → its `game_id` (unknown → 404), `requireHost(caller, gameId)` |
| Request | no body (any body is ignored) |
| Response | `200`, `Cache-Control: no-store`, `HostMatchStateResponse` (below) |
| Allowed response keys | host audience only: `matchId`, `matchNo`, `courtNo`, `state` |

Response (finish example):

```json
{"matchId": "match-uuid", "matchNo": 3, "courtNo": 1, "state": "FINISHED"}
```

Errors, in the order they are checked:

| Case | Status | Code |
|---|---|---|
| No account (anonymous, or guest cookie only) | 401 | `UNAUTHENTICATED` |
| CSRF token missing / wrong (gateway) | 403 | `CSRF_TOKEN_INVALID` |
| Malformed or unknown match id | 404 | `NOT_FOUND` |
| Match of a game the caller does not host | 403 | `FORBIDDEN` |
| Game `CLOSED` (D-3) | 409 | `STATE_CONFLICT` |
| Illegal move: start on not-`PLANNED`; finish on not-`ACTIVE`; cancel on `FINISHED` / `CANCELLED` (includes a repeated tap) | 409 | `STATE_CONFLICT` |
| A player of the match is not in the expected attendance state (start: `ASSIGNED`; finish: `PLAYING`; cancel: `ASSIGNED` for planned, `PLAYING` for active) — data drift guard | 409 | `STATE_CONFLICT` |

The 409 message is the existing `ErrorCode` text ("This changed in the meantime. Refresh and try again."). Unknown match and a match of another game both give no hint about other games (404 before the host check, 403 only for a real match).

## 4. Data and transactions

- Tables (playmatch schema only): `game` (read, lock), `play_match` (update `state`, `started_at`, `finished_at`), `match_player` (update `released_at`), `attendance` (update `state`, and on finish `matches_played`, `available_since`). **No migration**: all columns exist in V1/V2.
- Common step for all three (one private method, inside each command's transaction):
  1. `SELECT game_id FROM play_match WHERE id = :matchId` → none → 404.
  2. `policy.requireHost(caller, gameId)`.
  3. `SELECT state FROM game WHERE id = :gameId FOR SHARE` → `CLOSED` → 409. (Shared lock: waits for a running confirm, which takes the row `FOR UPDATE`; two lifecycle commands of the same game do not block each other.)
  4. `SELECT state, match_no, court_no FROM play_match WHERE id = :matchId FOR UPDATE` → transition check → 409.
  5. `SELECT a.id, a.state FROM attendance a JOIN match_player mp ON mp.attendance_id = a.id AND mp.match_id = :matchId WHERE a.game_id = :gameId ORDER BY a.id FOR UPDATE OF a` → every state must be the expected one → else 409.
- Lock order game → match → attendance (by id) everywhere (confirm: game → attendance; attendance management: game `FOR SHARE` → one attendance). No cycle, so no deadlock.
- **Start**: `play_match SET state='ACTIVE', started_at=:now`; `attendance SET state='PLAYING'` for the match's players. `match_player` unchanged (still unreleased).
- **Finish**: `play_match SET state='FINISHED', finished_at=:now`; `match_player SET released_at=:now WHERE match_id=:matchId`; `attendance SET state='READY', matches_played = matches_played + 1, available_since=:now` for the match's players. Players outside the match are not touched (they keep their older `available_since`, §11).
- **Cancel**: `play_match SET state='CANCELLED'` (`started_at` kept if it was active; `finished_at` stays null, D-4); `match_player SET released_at=:now`; `attendance SET state='READY'` only — `matches_played` and `available_since` unchanged (D-1).
- `:now` = `clock.instant()` once per command, so all rows of one command carry the same time.
- Every update checks its affected-row count (match 1 row; players 2 or 4 by format, equal to the rows locked in step 5); a mismatch throws, and the transaction rolls back (no partial success, §15).
- Writes in `JdbcClient` with named parameters, like confirm (no JPA in playmatch; A-2 of Match confirm).

## 5. Domain rules and state transitions

- `MatchState` (AD-03): `canStart()` = `PLANNED`; `canFinish()` = `ACTIVE`; `canCancel()` = `PLANNED` or `ACTIVE`. `FINISHED` and `CANCELLED` are final.
- `AttendanceState`: expected state of the match's players = `ASSIGNED` when the match is `PLANNED`, `PLAYING` when `ACTIVE` (new method, e.g. `AttendanceState.forMatch(MatchState)`); targets `PLAYING` (start) and `READY` (finish, cancel).
- Singles (OD-20): the same commands; the match simply has 2 players. Doubles: 4.
- After finish or cancel the court is free (`ux_busy_court` only covers `PLANNED`/`ACTIVE`) and the players are released (`ux_player_in_unreleased_match` covers `released_at IS NULL`), so confirm and suggestions can use them again (§17 checklist). The suggester already reads `matches_played`, `available_since` and the last three `FINISHED` matches by `finished_at`; no matcher change.
- "Mark as left" on an assigned/playing player stays refused until the match is finished or cancelled (existing rule, §11).
- A guest's own page (`GET /games/{id}/me`) already returns `attendanceState`; after start it says `PLAYING`, after finish/cancel `READY` (§0 step 4). Showing the match number and court there is My game (F12).
- No score (§11).

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `playmatch/match/MatchState.java` | changed | state enum | `canStart`, `canFinish`, `canCancel` |
| `playmatch/attendance/AttendanceState.java` | changed | state enum | expected in-match state per `MatchState` |
| `playmatch/match/HostMatchStateResponse.java` | new | response record | `matchId`, `matchNo`, `courtNo`, `state` |
| `playmatch/match/MatchService.java` | changed | command service | `start`, `finish`, `cancel` (each `@Transactional`) + shared private load-and-lock |
| `playmatch/match/HostMatchLifecycleController.java` | new | thin controller | three `@PatchMapping`s; `requireAccount`, parse id (malformed → 404), `200` + `no-store` |
| `playmatch/security/SecurityConfig.java` | changed | route rule | `PATCH /api/v1/host/matches/*/{start,finish,cancel}` authenticated |
| `gateway/routing/GatewayRouteTable.java` | changed | route table | `host-match-start`, `host-match-finish`, `host-match-cancel`: `PATCH` → playmatch, `HOST`, CSRF (one route per action, so any other path stays 401) |
| `GatewayAccessMatrixIT`, `PlaymatchServiceAccessIT` | changed | access matrix | rows for anonymous, guest, other account, host, per route |
| `playmatch/.../MatchStateTest.java` | new | unit | full transition table |
| `playmatch/.../AttendanceStateTest.java` | changed | unit | expected in-match state |
| `playmatch/.../MatchLifecycleIT.java` | new | IT | AC-01…AC-07, incl. a two-thread race |
| `support/TestData` (playmatch tests) | changed | builders | planned / active match builders if missing |
| `API Document/backend.md`, `API Document/frontend.md`, `.claude/flow/maps/api-routes.md`, `.claude/flow/maps/index.md` | changed | docs | three routes, new test classes |

## 7. Frontend

- Route unchanged: `/host/games/:gameId` → `HostBoardPage`. After any successful action, or a 409, the page reloads the board (same as attendance changes).
- `api/matches.ts`: `startMatch(matchId)`, `finishMatch(matchId)`, `cancelMatch(matchId)` (PATCH with CSRF via `client.ts`). `api/types.ts`: `HostMatchState` (`{matchId, matchNo, courtNo, state}`).
- **Components:**
  - `components/CourtCard.tsx` (changed): optional `actions` slot rendered below the teams; card itself stays presentational.
  - `features/host-board/CourtActions.tsx` (new): for a `PLANNED` match → primary **Start match** + secondary **Cancel match**; for `ACTIVE` → primary **Finish** + secondary **Cancel match**. Finish and Cancel open the existing `ConfirmDialog` (D-2); Start does not. Hidden when the game is `CLOSED` (D-3).
  - Status stays through `StatusBadge`: court card "Next up" (planned) / "Playing" (active); the Assigned and Playing player lists stay separate (§11, AC-06).
- **States** (one `useMutation` for match actions, exhaustive switch): `idle`; `saving` → the tapped button says "Saving…", every court action and attendance action is disabled; `success` → board reloads; `conflict` (409) → text "This match changed. The board was refreshed." + reload; `error`: 401 → login with `next`, 403 → existing "You don't manage this game." page, other → existing `ErrorBanner` with retry.
- **Layout:** phone (320–767px): court carousel card grows to fit two stacked full-width buttons (≥ 44px). Desktop (768px+): buttons side by side in the card. Visible focus on every button.

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|
| `hostBoard.startMatch` | Start match | §6 "`Start match`" | yes (2026-11-27) |
| `hostBoard.finish` | Finish | §3 "Only the host taps **Finish**" | yes (2026-11-27) |
| `hostBoard.cancelMatch` | Cancel match | NEW (§13 "cancel") | yes (2026-11-27) |
| `hostBoard.finishTitle` | `Finish match ${n}?` | NEW | yes (2026-11-27) |
| `hostBoard.finishBody` | Each player gets one set counted and goes back to Ready. | NEW | yes (2026-11-27) |
| `hostBoard.cancelTitle` | `Cancel match ${n}?` | NEW | yes (2026-11-27) |
| `hostBoard.cancelBody` | No set is counted. Players go back to Ready. | NEW | yes (2026-11-27) |
| `hostBoard.matchConflict` | This match changed. The board was refreshed. | NEW (same form as the approved `changeConflict`) | yes (2026-11-27) |
| reused | Saving…, Keep (dialog dismiss), Next up, Playing, You don't manage this game. | existing `hostBoard.*` | yes |

## 8. Security and privacy checklist

- [x] Identity only from the identity token (account); the path id names the match, never the caller.
- [x] Game resolved from the match on the server, then `AccessPolicy.requireHost`; route rule in gateway and playmatch.
- [x] Response host-only, holds only ids/numbers/state of this match; not reused for players.
- [x] PATCH → CSRF at the gateway; no CORS change.
- [x] 404 for unknown match before the host check; 409 message generic.
- [x] Logs: request line only; no names.
- [x] `Cache-Control: no-store`.

## 9. Test plan

Acceptance criteria:

| ID | Observable criterion |
|---|---|
| AC-01 | Start on a `PLANNED` match → 200 `state: "ACTIVE"`; `started_at` set; its players `PLAYING`; `match_player` still unreleased; other rows unchanged |
| AC-02 | Finish on an `ACTIVE` match → 200 `state: "FINISHED"`; `finished_at` set; every `match_player.released_at` set; its players `READY`, `matches_played + 1`, `available_since` = finish time; players outside the match unchanged |
| AC-03 | Cancel on `PLANNED` (players `ASSIGNED`) and on `ACTIVE` (players `PLAYING`) → 200 `state: "CANCELLED"`; players released and `READY`; `matches_played` and `available_since` unchanged (D-1) |
| AC-04 | Illegal move (start on active/finished/cancelled, finish on planned/finished/cancelled, cancel on finished/cancelled), closed game, or attendance drift → `409 STATE_CONFLICT`; match, `match_player` and attendance rows unchanged |
| AC-05 | After finish or cancel the same court and the same players can be confirmed again (201); after finish, the next suggestion picks the players who sat out first (§17) |
| AC-06 | Board UI: planned court card shows "Next up" + Start match / Cancel match; active shows "Playing" + Finish / Cancel match; Assigned and Playing lists stay separate (§11); Finish and Cancel ask first |
| AC-07 | Anonymous 401, guest 401, other account 403, unknown / malformed match 404, no CSRF 403, host 200 — for each of the three routes |
| AC-08 | Singles game: start / finish / cancel move both players; finish counts one set for each |
| AC-09 | Two concurrent finishes (or finish + cancel) of the same match → one 200, one 409; counters incremented once |

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Planned doubles match / start / 200 body exact; DB as AC-01 | IT |
| TC-02 | AC-02 | Active match, 8 players, 4 waiting with older `available_since` / finish / DB as AC-02; the 4 waiting unchanged | IT |
| TC-03 | AC-03 | Planned match / cancel / AC-03; active match / cancel / AC-03, `started_at` kept, `finished_at` null | IT |
| TC-04 | AC-04 | Each illegal move from the AC-04 list / 409; row snapshot before = after | IT |
| TC-05 | AC-04 | Closed game with an active match / finish / 409, unchanged | IT |
| TC-06 | AC-04 | Active match whose player row was set to `READY` directly in SQL (drift) / finish / 409, unchanged | IT |
| TC-07 | AC-05 | Finish court 1 / confirm the same 4 on court 1 / 201; same after cancel | IT |
| TC-08 | AC-05 | 1-court game, 8 READY (all 0 sets) / confirm + start + finish players 1–4 / suggestions pick players 5–8 | IT |
| TC-09 | AC-07 | Access-matrix rows (gateway + service) for the three routes; malformed id 404 | access matrices |
| TC-10 | AC-08 | Singles game: confirm 1 + 1, start, finish / 2 rows released, both `matches_played` 1 | IT |
| TC-11 | AC-09 | Two threads finish the same active match together / one 200 + one 409; `matches_played` + 1 only | IT (two threads) |
| TC-12 | AC-01…AC-04 | `MatchState` transition table; `AttendanceState` expected in-match state | unit |
| TC-13 | AC-06 | Board at 320 / 768 / 1280px: Start, then Finish with dialog, then Cancel on a second court; keyboard only; Saving… disables buttons; 409 text after finishing in another tab | visual (owner) |
| TC-14 | AC-02, §0 step 6 | Finish a match, restart Compose without removing volumes / board loads, court free, finished match kept in DB | manual (owner) |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 (OD-07, cancel part) | On cancel, do the players keep their earlier `available_since`? | (a) keep it (they did not play) · (b) reset to now | **(a)**: a cancelled match is not a played set; resetting would push them behind people who waited less. Same rule you chose for approval |
| D-2 | Ask "Are you sure?" before which actions? | (a) Finish and Cancel ask; Start does not · (b) only Cancel asks · (c) none | **(a)**: finish and cancel cannot be undone (finish counts a set); start is the normal next step and a wrong start can still be cancelled. One extra tap per set on Finish |
| D-3 | Game is `CLOSED` while a match is planned or active | (a) 409 for all three, buttons hidden (read-only, §11) · (b) still allow finish / cancel to release players | **(a)**: §11 "read-only history"; the MVP has no close route yet, so this only guards the rule |
| D-4 | Cancel an active match: what happens to `started_at` | (a) keep it, `finished_at` stays null · (b) also set `finished_at` | **(a)**: `finished_at` means "finished"; the suggester's partner history reads only `FINISHED` matches anyway |
| D-5 | Answer when the same action is repeated (double tap, second browser) | (a) 409 `STATE_CONFLICT`, board reloads · (b) 200 no-op | **(a)**: one rule for every illegal move; the button is disabled while saving, so a double tap rarely reaches the server |
| A-1 | Assumption: no request body; response is the match's ids/number/state only | — | §13 gives none; the board reloads after each action |
| A-2 | Assumption: one gateway route per action instead of a wildcard, so other paths stay 401 | — | internal routing detail |

## 11. Out of scope

- Player page showing match number, court and Playing / Finished → My game (F12).
- Feedback after a finished match → S05.
- Scores → not in MVP (§11).
- Closing a game → not in §13 MVP routes.
- Board polling → later.
