# F10 design — Match confirm + tap-to-swap review

Status: `APPROVED` (2026-11-26, by owner: D-1…D-7 as recommended, copy table)

## 1. Summary

On the review page (`/host/games/:gameId/review`, built read-only in Match suggestions) the host can now **swap players by tapping** — inside a court, across courts, or with a Ready player who was not picked — and then **Confirm** one court at a time. Confirm saves exactly one `PLANNED` match with its players, or answers `409 MATCH_CONFLICT` with nothing written. This is the first multi-row write in the match loop; Match start / finish / cancel reuses its transaction pattern.

Reusable pieces:

1. **`MatchLineup`** (playmatch, value object): Team A + Team B attendance ids, checked against the game's format (doubles 2 + 2, singles 1 + 1, all distinct). Used again by start / finish / cancel to read a match's players.
2. **`MatchService.confirm`** (playmatch, one `@Transactional` command): lock the game row, re-check everything, insert match + players, move attendances to `ASSIGNED`, increment `next_match_no`. A unique-index violation is mapped to 409, never swallowed (§15).
3. **`lineup.ts`** (frontend, pure reducer in `features/match-review/`): the editable lineups + bench and the tap-to-swap rule. No React, so it can be unit-tested (D-6).
4. **`SwapSelector`** behavior lives in the `MatchProposal` player buttons (§16 component inventory); a player is a real `<button aria-pressed>`, so it works by keyboard and without drag-and-drop.

## 2. Blueprint references

- §0 step 3: "Host approves the guest, requests suggestions, may swap a player, and confirms one four-player match on Court 1. The host sees all four; the guest sees **only** their own match number, Court 1 and `PLANNED` status if selected. If the suggestion did not select the guest, the host swaps them in before confirming."
- §0 step 4: "Host starts and finishes that match. … Four selected attendees are never assigned elsewhere simultaneously."
- §3: "Only the host taps to swap players or accepts."
- §5.5: "Host may swap players or regenerate. Only confirming a proposal saves a planned match."
- §11: "Host accepts a suggestion or chooses four players manually. This creates a `PLANNED` match, then host starts and finishes it." Attendance `READY → ASSIGNED → PLAYING`. "A closed session allows read-only history and no new assignments." "The UI must show `ASSIGNED` separately from `PLAYING`."
- §12 application invariants: "`court_no ≤ play_session.court_count`; exactly four distinct attendance IDs per match, two per team; all four belong to its session and are `READY` when planned; no other unreleased match for them; host owns the session … Enforce these in the service in one database transaction. The partial unique indexes also protect … one busy match per court and one unfinished assignment per player."
- §13: `POST /host/sessions/{sessionId}/matches` — "Confirm one proposed or manually edited four-player pairing as `PLANNED`." Request `{"courtNo", "teams": {"A": [...], "B": [...]}}`. "Confirmation returns `201` with `matchId`, `matchNo`, `courtNo`, `state: "PLANNED"`. Suggestions may be stale: re-read the four attendance rows and court state when confirming, then return `409 MATCH_CONFLICT` with a friendly retry message if anyone is unavailable." Error body `{"code":"MATCH_CONFLICT","message":"A player or court is no longer available.", …}`; "`400` invalid payload".
- §14: "The host can swap across courts or replace any player before confirming; the API revalidates the final four on each court."
- §15: "Confirm locks the session row to allocate/increment `next_match_no`, validates court, four distinct ready attendees and team sizes, creates match + assignments and marks `ASSIGNED`; no partial success. … Do not catch a database uniqueness error and pretend the match was saved; map it to `409` and let the host refresh."
- §16: `/host/sessions/:sessionId/review` — phone "One court at a time; tap a player then another to swap", desktop "Courts side by side; same tap-to-swap works", states "Wide gap warning, stale suggestion 409, Cancel/Confirm". "`409` shows “This lineup changed. Refresh suggestions” without discarding the host's whole session." "Support keyboard focus and the tap-to-swap flow without drag-and-drop."
- §17 step 3 gate: "correct DB state and `409` on an unavailable player/court; no partial match rows." Checklist: "Court and player cannot be assigned twice while unfinished".
- Owner decisions: OD-20 (b) singles = 2 players per match, doubles = 4; OD-22 route words (`/host/games/...`); OD-25 open (D-1 below); OD-08 open (D-6 below).

## 3. API contract

### 3.1 `POST /api/v1/host/games/{gameId}/matches` (§13)

| Item | Value |
|---|---|
| Route | `POST /api/v1/host/games/{gameId}/matches` (§13, renamed per OD-22) |
| Caller | this game's host |
| Identity source | account principal → identity token v2 (`aud=playmatch`, method + path bound) |
| Access check | gateway route rule (account required, CSRF required); service: `AccessPolicy.requireAccount` before the id is parsed, then `requireHost(caller, gameId)` inside the command |
| Request | `ConfirmMatchRequest` (below) |
| Response | `201`, `Cache-Control: no-store`, `ConfirmMatchResponse` (below) |
| Allowed response keys | host audience only: `matchId`, `matchNo`, `courtNo`, `state` |
| Errors | see table below |

Request (doubles; singles has one id per team):

```json
{
  "courtNo": 1,
  "teams": {"A": ["attendance-uuid-1", "attendance-uuid-4"],
            "B": ["attendance-uuid-2", "attendance-uuid-3"]}
}
```

Boundary validation (`@Valid`, → `400 VALIDATION_FAILED`): body present and JSON; `courtNo` integer 1–4 (the schema's `court_count` range); `teams.A` and `teams.B` present, each 1–2 well-formed UUIDs. Unknown extra fields are ignored (existing Jackson setting, not changed).

Response:

```json
{"matchId": "match-uuid", "matchNo": 3, "courtNo": 1, "state": "PLANNED"}
```

Errors, in the order they are checked:

| Case | Status | Code |
|---|---|---|
| No account (anonymous, or guest cookie only) | 401 | `UNAUTHENTICATED` |
| CSRF token missing / wrong (gateway) | 403 | `CSRF_TOKEN_INVALID` |
| Malformed or unknown game id | 404 | `NOT_FOUND` |
| Other account | 403 | `FORBIDDEN` |
| Body invalid (see above) | 400 | `VALIDATION_FAILED` |
| Game `CLOSED` | 409 | `STATE_CONFLICT` |
| Team sizes do not fit the game's format (doubles 2 + 2, singles 1 + 1), or an id appears twice | 400 | `VALIDATION_FAILED` |
| `courtNo > court_count` | 409 | `MATCH_CONFLICT` (D-3) |
| Court has a `PLANNED` or `ACTIVE` match | 409 | `MATCH_CONFLICT` |
| An id is not an attendance of this game (unknown or another game's) | 409 | `MATCH_CONFLICT` |
| A player is not `READY`, or is in an unreleased match | 409 | `MATCH_CONFLICT` |
| Unique index `ux_busy_court` / `ux_player_in_unreleased_match` / `(game_id, match_no)` violated at insert | 409 | `MATCH_CONFLICT` |

The 409 message is the existing `ErrorCode` text ("A player or court is no longer available."); it never names which player, so nothing about other games leaks.

No new internal route. No call to another service (everything is in the playmatch schema).

## 4. Data and transactions

- Tables (playmatch schema only): read + write `game` (`next_match_no`), `attendance` (`state`), insert `play_match`, `match_player`. **No migration**: V1 already has `next_match_no`, `ux_busy_court` and `ux_player_in_unreleased_match` (renamed to `game_id` columns in V2).
- One `@Transactional` method `MatchService.confirm`:
  1. `requireHost(caller, gameId)`.
  2. `SELECT state, court_count, match_format, next_match_no FROM game WHERE id = :gameId FOR UPDATE` — serializes every confirm of this game, and waits for / blocks the attendance changes, which take the same row `FOR SHARE` (Attendance management). Two confirms that share a player therefore run one after the other; the second sees `ASSIGNED` and answers 409.
  3. Closed → 409; `MatchLineup.of(teamA, teamB, format)` → 400; court range / busy → 409.
  4. `SELECT id, state FROM attendance WHERE game_id = :gameId AND id IN (:ids) ORDER BY id FOR UPDATE`; fewer rows than ids → 409; any state ≠ `READY` → 409; any `match_player` row with `released_at IS NULL` for them → 409.
  5. Insert `play_match` (`id = UUID.randomUUID()`, `match_no = next_match_no`, `court_no`, `state = 'PLANNED'`, `created_at = now(clock)`); `UPDATE game SET next_match_no = next_match_no + 1`; insert one `match_player` per player (`team` A/B, `released_at NULL`); `UPDATE attendance SET state = 'ASSIGNED'` for them. `available_since` and `matches_played` are not touched.
  6. `DuplicateKeyException` from any insert → `ApiException(MATCH_CONFLICT)`; the transaction rolls back (nothing saved, §15).
- Writes use `JdbcClient` with named parameters, same style as `AttendanceService` (the playmatch service has no JPA entities; conventions name `PlayMatch` / `MatchPlayer` entities, but adding JPA only for this would be a new pattern → plain SQL kept, listed in `implement.md`).
- Lock order is always game row → attendance rows (by id) → inserts, so two commands cannot deadlock.

## 5. Domain rules and state transitions

- Attendance `READY → ASSIGNED` (§11). Added to `AttendanceState` as `canBeAssigned()` (`this == READY`), the enum's transition check (AD-03). Host manual moves are unchanged.
- Match: new row starts `PLANNED`. No `MatchState` transition in this feature (start / finish / cancel come next).
- **D-1 (OD-25): keep the `ASSIGNED` step.** Confirm → players `ASSIGNED`, match `PLANNED`, shown on the host board court card as "Next up" (existing). The host then taps Start on the board (Match start / finish / cancel). Reason: §0 step 3 (highest precedence) requires the guest to see `PLANNED`, and step 4 requires the host to start the match.
- Singles (OD-20): same command, `MatchLineup` expects 1 + 1. Doubles: 2 + 2.
- Players manually chosen through swaps are allowed (§11 "or chooses four players manually"; §14 "replace any player"): the server accepts any `READY` attendances of this game, whether or not the suggester picked them.
- The suggestion's reason tags and warnings are not sent and not stored; the server does not check skill gap (§14: it "revalidates" availability only).
- Closed game: 409 `STATE_CONFLICT` (§11), same as suggestions.

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `playmatch/match/ConfirmMatchRequest.java` | new | request record + `@Valid` | `courtNo` (1–4), `teams {A, B}` (each 1–2 UUIDs) |
| `playmatch/match/ConfirmMatchResponse.java` | new | response record | `matchId`, `matchNo`, `courtNo`, `state` |
| `playmatch/match/MatchLineup.java` | new | value object | `of(teamA, teamB, MatchFormat)`: sizes per format, distinct ids → else `VALIDATION_FAILED`; `all()` sorted for the lock query |
| `playmatch/match/MatchService.java` | new | command service, one `@Transactional` | §4 steps 1–6; injects `Clock` |
| `playmatch/match/HostMatchController.java` | new | thin controller | `requireAccount`, parse id (malformed → 404), `@Valid` body, `201` + `no-store` |
| `playmatch/attendance/AttendanceState.java` | changed | state enum | `canBeAssigned()` |
| `playmatch/security/SecurityConfig.java` | changed | route rule | `POST /api/v1/host/games/*/matches` authenticated |
| `gateway/routing/GatewayRouteTable.java` | changed | route table | `host-matches`: `POST /api/v1/host/games/*/matches` → playmatch, account required, CSRF |
| `GatewayAccessMatrixIT`, `PlaymatchServiceAccessIT` | changed | access matrix | rows for anonymous, guest, other account, host |
| `playmatch/.../MatchLineupTest.java` | new | unit | sizes, duplicates, singles/doubles |
| `playmatch/.../AttendanceStateTest.java` | changed | unit | `canBeAssigned` |
| `playmatch/.../MatchConfirmIT.java` | new | IT | AC-01…AC-05, AC-07 incl. two-thread race |
| `support/TestData` (playmatch tests) | changed | builders | a planned/active match builder if not present |
| `API Document/backend.md`, `API Document/frontend.md`, `.claude/flow/maps/api-routes.md`, `.claude/flow/maps/index.md` | changed | docs | new route, new test class |

No new dependency on the backend.

## 7. Frontend

- Route unchanged: `/host/games/:gameId/review` → `features/match-review/MatchReviewPage.tsx`. Data load unchanged (suggestions, then board).
- **Editable state** (`features/match-review/lineup.ts`, pure): built from the suggestions + board:
  - `courts`: one entry per proposal `{courtNo, A: id[], B: id[], edited: boolean, saved: {matchNo} | null}`;
  - `bench`: board players with state `READY` who are in no proposal, in the board's existing order (longest waiting first);
  - `selected`: one slot or none. A slot is `{court, team, index}` or `{bench, index}`.
  - Action `tap(slot)`: nothing selected → select it; same slot → deselect; another slot → **swap the two players**, mark every touched court `edited`, clear selection. Slots of a saved court cannot be selected. Swapping two bench players does nothing but clear the selection.
  - Action `saved(courtNo, matchNo)`: lock that court.
  - Action `reset(suggestions, board)`: "Refresh suggestions" rebuilds everything (edits are dropped — the host asked for it).
- **Components:**
  - `MatchProposal.tsx` (changed): each player is a `<button aria-pressed>` showing the existing `PlayerChip`; selected = thick action-colored ring + check icon (not color alone). Edited court (D-4): reason tags and warnings hidden, note "You changed this court." shown. Footer: **Confirm** button (disabled while saving or while another court is saving), then per-court states below.
  - `BenchList.tsx` (new, feature folder): "Ready, not picked" heading + the bench player buttons, same selection look. Hidden when empty.
  - Hint line under the title: "Tap a player, then another, to swap them." An `aria-live="polite"` region says "{name} selected. Tap another player to swap." while one is selected.
  - No `ConfirmDialog` for Confirm (D-5): one tap saves; a mistake is undone by Cancel on the board (next feature).
- **States per court** (`useMutation`, exhaustive switch): `idle` → Confirm; `saving` → button "Saving…" disabled, other courts' Confirm disabled; `success` → card shows "Saved as match {n}." + StatusBadge "Next up", players locked; `conflict` (409) → inline `ErrorBanner`-style text "This lineup changed. Refresh suggestions" (§16) with the existing "Refresh suggestions" button; the host's edits on other courts are kept; `error`: 401 → login with `next`, 403 → "You don't manage this game.", other → existing generic error with retry.
- Page-level: once at least one court is saved, the existing "Back to board" link stays the way out; the board shows the planned match on its court card (existing "Next up").
- **Layout:** phone (320–767px) one court per row, bench below the courts, sticky bottom bar keeps "Refresh suggestions" + "Back to board"; Confirm sits inside each court card. Desktop (768px+) courts side by side (2 columns), bench full width below. Player buttons ≥ 44px; visible focus ring on every button.
- `api/matches.ts`: `confirmMatch(gameId, request)` (POST with CSRF via `client.ts`). `api/types.ts`: `ConfirmMatchRequest`, `ConfirmedMatch`.

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|
| `matchReview.confirm` | Confirm | §16 "Cancel/Confirm" | yes (2026-11-26) |
| `matchReview.saved` | `Saved as match ${n}.` | NEW | yes (2026-11-26) |
| `matchReview.conflict` | This lineup changed. Refresh suggestions | §16 verbatim | yes (2026-11-26) |
| `matchReview.swapHint` | Tap a player, then another, to swap them. | NEW (§16 "tap a player then another to swap") | yes (2026-11-26) |
| `matchReview.selected` | `${name} selected. Tap another player to swap.` | NEW (screen-reader announcement) | yes (2026-11-26) |
| `matchReview.benchTitle` | Ready, not picked | NEW | yes (2026-11-26) |
| `matchReview.edited` | You changed this court. | NEW (D-4 a) | yes (2026-11-26) |
| `matchReview.notSaved` | Nothing is saved until you confirm a court. | existing (approved 2026-11-26) | yes |
| reused | Saving…, Next up, Refresh suggestions, Back to board, You don't manage this game., Court N, Team A, Team B | existing `hostBoard.*` / `matchReview.*` | yes |

## 8. Security and privacy checklist

- [x] Identity only from the identity token (account); path id names the game; body ids name target attendances, never the caller.
- [x] `AccessPolicy.requireHost` inside the command; route rule in gateway and playmatch.
- [x] Response is host-only and holds only the new match's ids/numbers; never reused for a player.
- [x] POST → CSRF at the gateway; no CORS change.
- [x] 409 message is generic (no player names, no hint whether an id belongs to another game).
- [x] Logs: none beyond the request line; no names.
- [x] `Cache-Control: no-store`.

## 9. Test plan

Acceptance criteria:

| ID | Observable criterion |
|---|---|
| AC-01 | Doubles confirm → `201 {matchId, matchNo, courtNo, state: "PLANNED"}`; 1 `play_match` row `PLANNED`, 4 `match_player` rows (2 per team, `released_at` null), those 4 attendances `ASSIGNED`, `next_match_no` + 1, `available_since` and `matches_played` unchanged |
| AC-02 | Player not `READY` / not in this game / in an unreleased match, court busy, court > `court_count` → `409 MATCH_CONFLICT`; row counts of `play_match`, `match_player` and attendance states and `next_match_no` unchanged |
| AC-03 | Duplicate ids, wrong team sizes for the format, missing teams, malformed id, `courtNo` outside 1–4 → `400 VALIDATION_FAILED`; nothing written |
| AC-04 | Two concurrent confirms sharing a player (different courts) → exactly one 201 and one 409; one match saved |
| AC-05 | Anonymous 401, guest 401, other account 403, unknown/malformed game 404, no CSRF 403, host 201; closed game 409 `STATE_CONFLICT` |
| AC-06 | Review page: tap one player then another swaps them (same court, across courts, with the bench), by mouse/touch and keyboard; Confirm saves; 409 shows "This lineup changed. Refresh suggestions" and keeps the other courts' edits |
| AC-07 | Singles game: 1 + 1 confirm → 201, 2 `match_player` rows; 2 + 2 on a singles game → 400 |
| AC-08 | After a confirm, the host board shows the match on its court with both teams; a second suggestion leaves those players and that court out |

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Seeded game, 8 READY doubles / when host confirms court 1 with 4 of them / then 201 body exact, DB rows as AC-01 | IT |
| TC-02 | AC-02 | One of the 4 is `PENDING` (then `LEFT`, then `ASSIGNED` in another match) / confirm / 409 `MATCH_CONFLICT`, row counts and states unchanged | IT |
| TC-03 | AC-02 | Court 1 has a `PLANNED` match (then `ACTIVE`) / confirm court 1 / 409; court 3 on a 2-court game / 409; unchanged | IT |
| TC-04 | AC-02 | An attendance id of another game / confirm / 409; unchanged | IT |
| TC-05 | AC-03 | Duplicate id; 3 + 1; 2 + 1; `teams` missing; `"abc"` as id; `courtNo` 0 and 5 / 400; unchanged | unit (`MatchLineupTest`) + IT |
| TC-06 | AC-04 | Two threads, each confirming a lineup with one shared player on courts 1 and 2 / start together / one 201 + one 409; 1 match, 4 `match_player` | IT (two threads) |
| TC-07 | AC-05 | Access matrix rows (gateway + service) for the new route; closed game 409 `STATE_CONFLICT` unchanged rows | access matrices + IT |
| TC-08 | AC-07 | Singles game, 2 READY / 1 + 1 / 201, 2 rows; 2 + 2 / 400 | unit + IT |
| TC-09 | AC-08 | After TC-01 / `GET board` shows the match on court 1 "PLANNED"; `POST suggestions` excludes the 4 and court 1 | IT |
| TC-10 | AC-01 | `next_match_no` rises 1 → 2 → 3 over two confirms on two courts; match numbers 1 and 2 | IT |
| TC-11 | AC-06 | `lineup.ts`: select/deselect, swap within team, across teams, across courts, with bench, bench↔bench no-op, saved court not selectable, `edited` flag | unit (Vitest, if D-6 b) |
| TC-12 | AC-06 | Review page at 320 / 768 / 1280px: swap by tap and by keyboard (Tab + Enter), Confirm, saved state, 409 text with edits kept (make a player Left on the board in another tab first) | visual (owner) |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 (OD-25) | After Confirm, are players `ASSIGNED` (match planned, host taps Start later) or `PLAYING` at once? | (a) keep §11: `ASSIGNED` + `PLANNED`, Start on the board · (b) Confirm = start: players `PLAYING`, match `ACTIVE`, no Start step | **(a)**: §0 steps 3–4 (highest precedence) require the guest to see `PLANNED` and the host to start the match. One extra tap; the board can make Start one tap per court. (b) would need the owner to override §0 |
| D-2 | Request shape for a singles game | (a) same `teams` object, 1 id per team · (b) a separate `players` list | **(a)**: one contract for both formats |
| D-3 | `courtNo` above this game's `court_count` | (a) 409 `MATCH_CONFLICT` ("court not available") · (b) 400 | **(a)**: the draft criteria and §12 treat it as an availability rule; 400 stays for shape errors |
| D-4 | Reason tags and warnings after the host swaps players on a court | (a) hide them for that court and show "You changed this court."; level chips stay visible · (b) recompute the wide-gap warning in the browser | **(a)**: no matching rule copied into React (§15 "Avoid putting matching logic … in a React component"); the level chips still show the gap |
| D-5 | Ask "Are you sure?" before Confirm? | (a) no dialog, one tap saves · (b) `ConfirmDialog` | **(a)**: confirm is undone by Cancel (next feature); fewer taps at the court |
| D-6 (OD-08) | Unit tests for the swap reducer | (a) no frontend test runner; reducer covered by the visual check only · (b) add **Vitest** as a dev dependency, `npm test` script, `lineup.test.ts`; version read from the npm registry and shown to you before pinning | **(b)**: the swap rule has many cases and "unit tests" are part of a done feature; dev-only, not shipped |
| D-7 | §6 lists "Start match" as the review screen's primary action, §16 says "Cancel/Confirm" | (a) review page has Confirm; Start lives on the board court card (next feature) · (b) Start on the review page | **(a)**: matches D-1 (a) and §16 (Part II wins over Part I) |
| A-1 | Assumption: a Ready player may be confirmed even if the suggester did not pick them | — | §11 "chooses four players manually", §14 "replace any player" |
| A-2 | Assumption: no JPA entities; writes in `JdbcClient` like the existing services | — | the playmatch service has no JPA today; listed as an old-vs-convention spot in `implement.md` |

## 11. Out of scope

- Start / finish / cancel, Start button on the court card → Match start / finish / cancel (F11).
- Building a court from scratch when the suggester proposed none (e.g. a free court with too few Ready players) → not in §16's review states; later if the pilot asks.
- Player's own assignment page showing the planned match → My game (F12).
- Board polling → later.
