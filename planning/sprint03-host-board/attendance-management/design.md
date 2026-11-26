# F07 design — Attendance management (approve, left)

Status: `APPROVED` (2026-11-26, by owner: all recommendations — OD-15 allow, D-1 = a, D-2 = a — and the copy table; earlier answers in §10)

## 1. Summary

The host of one game changes a player's attendance from the host board with two manual actions: **Approve** a pending guest and **Mark as left** (someone went home, or a pending check-in is a mistake or duplicate). The server enforces the state rules; the board re-reads itself after each change.

Players never change their own status. Besides these two host buttons, status changes only through the match flow (later features: host puts players on a court, host finishes the set → back to Ready, sets +1).

Owner decision 2026-11-26: **no Rest button**. A tired player stays Ready and the host swaps them out of a suggestion. This deviates on purpose from blueprint §11 "Host may set `READY ↔ RESTING`"; the `RESTING` value stays in the database check and the board's Resting section stays (always empty for now).

This is the **first host write route** and the **first state-machine write**, so it also ships two reusable pieces:

1. **Transition table in the state enum** (AD-03): `AttendanceState` says which target state the host may set from which current state. Later match commands add their own transitions to the same enum.
2. **Identity token bound to one request** (Host board review #17; owner 2026-11-26: bind it): the gateway's identity token also names the target service, the HTTP method and the path, so a token seen on the internal network cannot be replayed on another route within its 30 s.

Plus one shared UI piece: `ConfirmDialog` (§16 component inventory) before Mark as left.

## 2. Blueprint references

- §0 step 3: "Host approves the guest, requests suggestions, …"
- §3 step 2: "Organizer can approve a visitor."
- §3 table: host-only actions on the game; "Here 'court host' is the person running **this play session**".
- §11: "Guest is `PENDING` until host approval." State diagram: `PENDING → READY`, `READY → ASSIGNED`, `ASSIGNED → PLAYING`, `PLAYING → READY`, `READY → RESTING`, `RESTING → READY`, `READY → LEFT`.
- §11: "Regulars enter as `READY`; guests enter as `PENDING` and the host approves them. … Host may set `READY ↔ RESTING`. To mark an assigned/playing player `LEFT`, the host first cancels or finishes that match. … players who waited retain their earlier `available_since` and get priority next round."
- §11: "A closed session allows read-only history and no new assignments."
- §12: "If the cookie is lost, the guest checks in again and the host marks the old attendance `LEFT`".
- §13: "`PATCH /host/sessions/{sessionId}/attendance/{attendanceId}` — Host — Approve guest, set Ready/Resting/Left with state validation." "Never accept `hostUserId`, `playerId`, or `attendanceId` from a client as proof of its identity". "`400` invalid payload, `401` missing/expired identity, `403` authenticated but no permission, `404` unknown or private resource not visible, `409` state conflict."
- §15: "The host access policy takes `(principalUserId, sessionId)` and checks the persisted owner for **every** host mutation".
- §16: "every host mutation can be `idle / saving / success / conflict`." "Buttons disable while saving to avoid duplicate taps; the backend still validates writes." Component inventory: `ConfirmDialog`.

Routes renamed by OD-22: `/host/sessions/...` → `/host/games/...`. Deviation: §13 "set … Resting" is not offered (owner 2026-11-26).

## 3. API contract

### 3.1 `PATCH /api/v1/host/games/{gameId}/attendance/{attendanceId}` (§13)

| Item | Value |
|---|---|
| Route | `PATCH /api/v1/host/games/{gameId}/attendance/{attendanceId}`; gateway forwards to playmatch-service (same path) |
| Caller | this game's host |
| Identity source | account session at the gateway → identity token → `CallerIdentity.Account`. `gameId` and `attendanceId` name the target, never the caller (§13) |
| Access check | gateway: route kind `HOST` (signed-in account) + CSRF header `X-CSRF-TOKEN`; playmatch: `AccessPolicy.requireHost(caller, gameId)` (§15), then the attendance must belong to this game |
| Request | `{"state": "READY" \| "LEFT"}`. Missing, unknown or any other value (`PENDING`, `ASSIGNED`, `PLAYING`, `RESTING`) → 400 |
| Response | `200`, `Cache-Control: no-store`, `HostAttendanceResponse` `{"attendanceId":"uuid","state":"READY","availableSince":"2026-11-26T12:30:00Z"}` |
| Allowed response keys | host only (`HostAttendanceResponse` used only by `HostAttendanceController`); exactly `attendanceId`, `state`, `availableSince` |
| Errors | anonymous / guest cookie only → `401 UNAUTHENTICATED` (gateway, Host board D-3); missing or stale CSRF token → `403 CSRF_TOKEN_INVALID` (gateway); signed in, not this game's host → `403 FORBIDDEN`; unknown or malformed `gameId` → `404`; `attendanceId` unknown, malformed or of another game → `404 NOT_FOUND`; invalid body → `400 VALIDATION_FAILED`; transition not allowed (§5 table) → `409 STATE_CONFLICT`; player in a planned or active match → `409 STATE_CONFLICT` "Cancel or finish this player's match first."; game `CLOSED` → `409 STATE_CONFLICT` "This game is closed." |

Same target as the current state (for example two taps on "Approve" from two tabs) → `200` with the unchanged row, nothing written (D-2).

### 3.2 Identity token v2 (internal header, changed plumbing; AD-14; owner 2026-11-26)

| Item | Value |
|---|---|
| Header | `X-Internal-Identity` (unchanged) |
| Format | `v2.<base64url(claims)>.<base64url(HMAC-SHA256(secret, "v2." + base64url(claims)))>`; `v1` is no longer issued or accepted (gateway and services ship together from one repo) |
| Claims | `{"kind":"ACCOUNT","userId":"uuid","aud":"playmatch-service","method":"PATCH","path":"/api/v1/host/games/…/attendance/…","iat":…,"exp":iat+30}` |
| `aud` | target service name from the gateway route table (new field per route); a service compares it with its own `spring.application.name` |
| `method`, `path` | the forwarded request's method and path (no query string); the gateway forwards the same path, so the service compares with its own request line |
| Refused | any mismatch of `aud`, `method` or `path`, plus every v1 rule (signature, expiry, 30 s life) → `401 UNAUTHENTICATED` |
| Not covered | the request body (a replayed token on the same route with another body still passes within 30 s); accepted because a host token already allows any body on its own route, and services publish no host port |

No new internal routes. No new public routes besides 3.1 (already in §13).

## 4. Data and transactions

- **No migration.** Columns used: `attendance.state`, `attendance.available_since` (playmatch schema), `game.state`, `game.host_user_id`.
- One `@Transactional` method `AttendanceService.changeState(caller, gameId, attendanceId, target)`:
  1. `AccessPolicy.requireHost` (401 / 404 / 403).
  2. `SELECT state FROM game WHERE id = :gameId FOR SHARE` → `CLOSED` → 409. (Shared lock, so a later "close game" waits for this write; same pattern as member join.)
  3. `SELECT state, available_since FROM attendance WHERE id = :attendanceId AND game_id = :gameId FOR UPDATE` → no row → 404. The row lock makes two host taps and the later match confirm (which also locks these rows, AD-07) run one after the other.
  4. Check the transition (§5). `ASSIGNED` / `PLAYING` → 409 "Cancel or finish this player's match first."
  5. `UPDATE attendance SET state = :target WHERE id = :attendanceId`. `available_since` is never changed here (OD-07, owner 2026-11-26: keep).
- Write style: `JdbcClient` with named parameters, like the existing member join and guest check-in writes (D-1). No JPA entity yet.
- No call to another service (guest cookie is kept, D-3), so no compensation step (AD-16 not triggered).
- `matches_played` is never changed here.

## 5. Domain rules and state transitions

Host transitions through this route (`✓` allowed, `=` same state → 200 no change, `✗` → 409):

| From \ To | `READY` | `LEFT` |
|---|---|---|
| `PENDING` | ✓ approve (§11) | ✓ reject a mistaken or duplicate check-in (OD-15, §12) |
| `READY` | = | ✓ went home (§11) |
| `RESTING` | ✗ (state not reachable now; no Rest button) | ✗ |
| `ASSIGNED` | ✗ "cancel or finish first" | ✗ "cancel or finish first" (§11) |
| `PLAYING` | ✗ "cancel or finish first" | ✗ "cancel or finish first" (§11) |
| `LEFT` | ✗ left is final (OD-24 = a, owner 2026-11-26) | = |

Targets `PENDING`, `ASSIGNED`, `PLAYING`, `RESTING` are never set by this route → 400 (check-in and match commands own the first three; Rest is not offered).

`available_since` (OD-07, approve part; owner 2026-11-26: keep earlier time): approving keeps the check-in time, so a guest who waited for approval is not pushed behind people who arrived later. The board's order is `matchesPlayed` first, then `availableSince` (§14), so this only breaks ties. The cancel part of OD-07 belongs to Match lifecycle.

Other rules:

- Host = `game.host_user_id` (§11). The host may also be a player in the same game (OD-11); then the host can change their own row like any other.
- A `CLOSED` game: every change → 409; the board shows no action buttons.
- Members marked `LEFT` cannot join again (My attendance design D-4: "You were removed from this game."). Guests marked `LEFT` keep their cookie and see "You left this game." on My game; checking in again from the public page creates a new `PENDING` entry (existing guest check-in behavior: an existing `LEFT` row is not returned) — D-3.

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `playmatch/.../attendance/AttendanceState.java` | changed | AD-03 transition table: `hostMayMoveTo(target)`, `isHostTarget()`, `inMatch()` | pure; unit-tested over all 6 × 6 pairs |
| `playmatch/.../attendance/HostAttendanceController.java` | new | thin controller, `CallerIdentity` arg, `@Valid` body | `PATCH` only |
| `playmatch/.../attendance/ChangeAttendanceRequest.java` | new | record `{AttendanceState state}` (`@NotNull`) | non-host targets → 400 in the service |
| `playmatch/.../attendance/HostAttendanceResponse.java` | new | record `{attendanceId, state, availableSince}` | host audience only |
| `playmatch/.../attendance/AttendanceService.java` | new | command service, one `@Transactional` method, row locks | uses `AccessPolicy` |
| `playmatch/.../security/SecurityConfig.java` | changed | open `PATCH /api/v1/host/games/*/attendance/*` (identity from resolver + policy) | |
| `gateway/.../routing/GatewayRoutes.java`, `GatewayRouteTable.java` | changed | route `host-attendance` (`PATCH`, `HOST`, → playmatch) | CSRF already on for writes |
| `gateway/.../routing/GatewayRoute.java` | changed | add `audience` (service name) per route | |
| `gateway/.../routing/IdentityTokenFilter.java` | changed | issue v2 with `aud`, method, path | |
| `libs/common-web/.../identity/IdentityToken.java` | changed | v2 claims; `verify(token, audience, method, path)` | still JDK crypto only |
| `libs/common-web/.../identity/CallerIdentityArgumentResolver.java` | changed | passes own `spring.application.name`, request method and path to the verifier | |
| `playmatch/src/test/.../AccessMatrixIT`, `gateway/.../GatewayAccessMatrixIT` | changed | new route rows | AD-09 |

## 7. Frontend

- Route: unchanged `/host/games/:gameId`. Actions live on the existing player rows of the host board.
- API: `api/host.ts` → `changeAttendance(gameId, attendanceId, state)`; `api/client.ts` gets `apiPatch` (same CSRF and retry-once-on-stale-token rule as `apiPost`). Type `HostAttendanceChange` in `api/types.ts`.
- Components:
  - `features/host-board/AttendanceActions.tsx` (new): the buttons for one row, from a pure map `state → actions` in the same file. `PENDING`: Approve (primary), Reject; `READY`: Mark as left; `RESTING` / `ASSIGNED` / `PLAYING` / `LEFT`: none. No buttons when the game is `CLOSED`.
  - `components/ConfirmDialog.tsx` (new shared, §16 inventory): native `<dialog>` with `showModal()`, title, body, confirm + cancel buttons, focus moves into the dialog and back to the button that opened it, Esc cancels. Used before Reject and Mark as left (both final).
  - `PlayerList.tsx` (changed): renders `AttendanceActions` per row.
  - `HostBoardPage.tsx` (changed): one `useMutation(changeAttendance)`; while `saving`, **all** action buttons on the board are disabled (one change at a time, §16 duplicate taps); on `success` → `board.reload()`; on `conflict` → note "This player's status changed. The board was refreshed." + `board.reload()`; on `error` → `ErrorBanner` (401 → login redirect as on the board read, 403 `FORBIDDEN` → forbidden page; any other 403, e.g. `CSRF_TOKEN_INVALID` after the one retry, → `ErrorBanner` — clarified in review round 1, 2026-11-26).
- States: board read unchanged (`useAsync`); mutation `idle / saving / success / conflict / error` with an exhaustive `switch`.
- Layout: phone (320–767px) — buttons below the player chip in one row, each ≥ 44px high, wrap if narrow; desktop (768px+) — buttons at the right end of the row. No drag-and-drop.

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|
| `hostBoard.approve` | Approve | §3 "Organizer can approve a visitor" | yes (2026-11-26) |
| `hostBoard.reject` | Reject | NEW (OD-15) | yes (2026-11-26) |
| `hostBoard.markLeft` | Mark as left | NEW (§12 "the host marks the old attendance `LEFT`") | yes (2026-11-26) |
| `hostBoard.saving` | Saving… | NEW | yes (2026-11-26) |
| `hostBoard.rejectTitle` | Reject {name}? | NEW | yes (2026-11-26) |
| `hostBoard.rejectBody` | {name} will be moved to Left and will not be picked for matches. | NEW | yes (2026-11-26) |
| `hostBoard.leftTitle` | Mark {name} as left? | NEW | yes (2026-11-26) |
| `hostBoard.leftBody` | {name} will not be picked for matches again in this game. | NEW | yes (2026-11-26) |
| `hostBoard.cancelAction` | Keep | NEW | yes (2026-11-26) |
| `hostBoard.changeConflict` | This player's status changed. The board was refreshed. | NEW (§16 409 pattern) | yes (2026-11-26) |
| `hostBoard.closedNote` | This game is closed. The board is read-only. | NEW (§11 closed = read-only) | yes (2026-11-26) |

Error banner for other failures reuses the existing `ErrorBanner` text (no new string).

## 8. Security and privacy checklist

- [x] Identity only from the account session → signed token; path ids name the target.
- [x] `AccessPolicy.requireHost` on the route, then attendance ↔ game check in the same SQL (`WHERE id = :a AND game_id = :g`), so a host of game A cannot change a row of game B (404).
- [x] CSRF required (gateway, cookie-authenticated write); no CORS change.
- [x] Token bound to service + method + path, so a token from the board read cannot be replayed on this write.
- [x] Response: three fields, host only, `no-store`; no name, level, user or player id.
- [x] Logs: request line only (method, path, status, ms); the service logs no names.

## 9. Test plan

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-02 | Every (from, to) pair of the 6 states × 6 targets gives the §5 result | unit `AttendanceStateTest` |
| TC-02 | AC-01 | PENDING guest, host PATCH `READY` → 200 `{attendanceId, state: READY, availableSince}`; row `READY`, `available_since` unchanged | playmatch IT |
| TC-03 | AC-02 | PENDING → LEFT 200; READY → LEFT 200; `available_since` unchanged | playmatch IT |
| TC-04 | AC-03 | ASSIGNED and PLAYING (test data with a match) → LEFT / READY → 409 "Cancel or finish this player's match first."; row unchanged | playmatch IT |
| TC-05 | AC-03 | LEFT → READY → 409; RESTING row (test data) → READY / LEFT → 409; unchanged | playmatch IT |
| TC-06 | AC-03 | Game `CLOSED` → any change → 409 "This game is closed."; unchanged | playmatch IT |
| TC-07 | AC-02 | Same state (READY → READY) → 200, no write | playmatch IT |
| TC-08 | AC-04 | Body missing `state`, `state: "RESTING"`, `"PLAYING"`, `"FOO"`, not JSON → 400 `VALIDATION_FAILED`, unchanged | playmatch IT |
| TC-09 | AC-04 | Other account → 403; unknown game → 404; attendance of another game (same host owns both) → 404; malformed ids → 404; no token → 401 — row unchanged in each | playmatch `AccessMatrixIT` |
| TC-10 | AC-04 | Gateway: anonymous → 401; guest cookie only → 401; account without CSRF header → 403 `CSRF_TOKEN_INVALID`; account + CSRF → forwarded with one identity token | gateway `GatewayAccessMatrixIT` + stub |
| TC-11 | AC-05 | Token issued for `GET …/board` replayed on `PATCH …/attendance/…` → 401; token with `aud` player-service sent to playmatch → 401; v1 token → 401; valid v2 → passes; existing board / my-attendance routes still work with v2 | unit `IdentityTokenTest` + playmatch IT + gateway IT |
| TC-12 | AC-01 | Two parallel `PENDING → READY` requests → both 200, one row `READY` (row lock, D-2) | playmatch IT |
| TC-13 | AC-01 | `HostAttendanceResponse` keys exactly `attendanceId`, `state`, `availableSince` | `JsonAllowlist` (host set) |
| TC-14 | AC-07 | Guest marked LEFT: own status read → `LEFT`; member marked LEFT: join again → 409 "You were removed from this game." (existing behavior re-checked) | playmatch IT |
| TC-15 | — | Schema boundary test still passes | existing IT |
| TC-16 | AC-06 | Board: buttons per state; Reject / Mark as left open the dialog, Keep closes it with no request; all buttons disabled while saving; board re-reads after success; 409 shows the conflict note and re-reads; closed game shows no buttons | visual (owner) |
| TC-17 | AC-06 | Keyboard only: tab to a button, Enter opens dialog, Esc cancels, focus returns; buttons ≥ 44px at 320px and 1280px | visual (owner) |
| TC-18 | AC-01 | Live (Docker): guest checks in via the public page → host approves on the board → board shows the guest under Ready; guest's My game shows Ready; `player1` PATCH → 403; anonymous `curl` → 401 | live |

## 10. Open decisions and assumptions

| ID | Question | Options | Decision / recommendation |
|---|---|---|---|
| — | Which manual host buttons? | Approve + Left + Rest · Approve + Left · Approve only | **Decided 2026-11-26 (owner): Approve + Left.** No Rest (deviation from §11 `READY ↔ RESTING`) |
| OD-07 (approve part) | `available_since` on approve | keep · reset | **Decided 2026-11-26 (owner): keep** earlier time |
| OD-15 | Can the host reject a pending guest (`PENDING → LEFT`)? | allow · do not allow | **Decided 2026-11-26 (owner): allow** (the "Left" action on a pending row = Reject) |
| OD-23 | `RESTING → LEFT` | — | **Moot**: no Rest button, so no row reaches `RESTING` (owner 2026-11-26) |
| OD-24 | Undo `LEFT → READY` | (a) final · (b) allow | **Decided 2026-11-26 (owner): (a) final** (undo not chosen) |
| OD-25 (new, for Match confirm / Match lifecycle) | Drop the `ASSIGNED` step: putting players on a court makes them Playing at once, host taps Done at the end of the set? | keep blueprint `ASSIGNED → PLAYING` · drop it | **Not decided here** (owner 2026-11-26: record for the match features) |
| D-1 | Write style for the first attendance write | (a) `JdbcClient` + enum rule · (b) first JPA entity | **Decided 2026-11-26 (owner): (a)** |
| D-2 | Same state as current (double tap, two tabs) | (a) 200, no change · (b) 409 | **Decided 2026-11-26 (owner): (a)** |
| D-3 | Revoke the guest cookie when marked `LEFT`? | keep · revoke | **Decided 2026-11-26 (owner): keep cookie** |
| D-4 | Identity token scope before the first write route | (a) token v2 with `aud` + method + path · (b) accept the 30 s window | **Decided 2026-11-26 (owner): (a)** |
| — | Assumption: "Approve" does not change the guest's level or answers (level changes are Host skill assessment) | — | — |

## 11. Out of scope

- Rest button / `READY ↔ RESTING` → not offered (owner 2026-11-26); can come back as a later backlog item if the pilot needs it
- Assigning a player into a match, swaps → Match confirm (F10); the `ASSIGNED` step question → OD-25
- Cancel / finish a match to free a player → Match lifecycle (F11)
- Changing a level while approving → Host skill assessment (F14)
- Closing a game → L-10
- Undo of "left" → not offered (OD-24 = a)
- Polling / real-time board → Host board D-4, L-14
- Binding the token to the request body → not planned (§3.2 "Not covered")
