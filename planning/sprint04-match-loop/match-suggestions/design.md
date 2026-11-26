# F09 design — Match suggestions (read-only)

Status: `APPROVED` (2026-11-26, by owner: D-1…D-6 as recommended, copy table)

## 1. Summary

The host taps "Suggest matches" on the host board and sees one proposal per free court: Team A vs Team B, simple reason tags and warnings. The proposals come from the existing pure `MatchSuggester` (`backend/libs/matching`) fed with live data. **Nothing is saved**: the route only reads. Swapping and confirming come with Match confirm (F10).

Reusable pieces:

1. **`SuggestionService`** (playmatch): host check → one read-only snapshot of this game → one batch call to player-service for bands (existing `PlayerLevelsClient`) → `MatchSuggester` → response. Same shape as `HostBoardService`.
2. **`SuggestionSnapshotQuery`** (playmatch, `JdbcClient`, read-only): eligible ready pool, free courts, recent partners. This is the anti-corruption layer between the tables and the pure matcher (blueprint's `BoardSnapshotAssembler`).
3. **Matcher change for singles** (owner decision OD-20 = each game is singles or doubles): `BoardSnapshot` gets the game's format; singles proposals have 2 players per court (D-3). Doubles behavior stays exactly as today.
4. **Match review page** `/host/games/:gameId/review` (§16), read-only in this feature; F10 adds tap-to-swap and Confirm on the same page.

## 2. Blueprint references

- §3: "Only the host sees the full … board and can tap **Suggest next matches**."
- §5.1: "Eligible pool = checked-in `READY` players who are not in another unfinished match; use currently free courts."
- §5.2: "choose players with fewer games this session first, then those who have been ready longer."
- §5.3–5.4: group adjacent bands; "Unknown level is temporarily treated as band 2 for calculation and flagged to the host. Flag any wide level spread."; three splits, smallest team gap, then penalize recent repeat partners.
- §5.5: "Show the suggested court and **why**. Host may swap players or regenerate. Only confirming a proposal saves a planned match."
- §6: "This match may have a wide skill gap."; Match review screen: "Court 1: Team A vs Team B, four player cards, simple reason tags, swap control".
- §11: "Suggestion itself writes nothing." "A closed session allows read-only history and no new assignments."
- §13: `POST /host/sessions/{sessionId}/suggestions` — "Host — Compute one or more proposed courts; read-only, no match persistence." Example response with `courtNo`, `teams.A/B` (attendance ids), `reasonCodes`, `warnings`.
- §14: engine contract (pure function, `BoardSnapshot`: ready attendance ids, nullable bands, `availableSince`, `gamesPlayed`, last three partners, free courts); slots `4 × min(freeCourts, floor(readyCount / 4))`; sort `gamesPlayed ASC, availableSince ASC, UUID`; `spread ≥ 2` → `WIDE_SKILL_GAP`; "If fewer than four are ready or no court is free, return no suggestion with `WAITING_FOR_PLAYERS` or `NO_FREE_COURT`."
- §16: `/host/sessions/:sessionId/review` — "Suggestion preview — One court at a time … Courts side by side … Wide gap warning, stale suggestion 409, Cancel/Confirm".
- Owner decisions: OD-19 (b) "the matcher also favours players below the minimum"; OD-20 (b) each game is singles (2 per match) or doubles (4 per match); OD-22 route words (`/host/games/...`).

## 3. API contract

### 3.1 `POST /api/v1/host/games/{gameId}/suggestions` (§13)

| Item | Value |
|---|---|
| Route | `POST /api/v1/host/games/{gameId}/suggestions` (§13, renamed per OD-22) |
| Caller | this game's host |
| Identity source | account principal → identity token v2 (`aud=playmatch`, method + path bound) |
| Access check | gateway route rule (account required, CSRF required because it is a POST); service: `AccessPolicy.requireAccount` before the id is parsed, then `requireHost(caller, gameId)` |
| Request | no body (an empty body or `{}` is accepted; any body is ignored) |
| Response | `200`, `Cache-Control: no-store` (see below) |
| Allowed response keys | host audience only; contains attendance ids only (no names, no bands, no player ids) |
| Errors | `401 UNAUTHENTICATED` no account (guest cookie only too); `404 NOT_FOUND` unknown or malformed game id; `403 FORBIDDEN` other account; `409 STATE_CONFLICT` game `CLOSED`; `503 SERVICE_UNAVAILABLE` player-service down; `403` CSRF missing (gateway) |

Response:

```json
{
  "suggestions": [{
    "courtNo": 1,
    "teams": {"A": ["attendance-uuid-1", "attendance-uuid-4"],
              "B": ["attendance-uuid-2", "attendance-uuid-3"]},
    "reasonCodes": ["SIMILAR_LEVEL", "BALANCED_TEAMS", "LONG_WAIT"],
    "warnings": []
  }],
  "noSuggestionReason": null
}
```

- `suggestions`: ordered by `courtNo`. Doubles: 2 ids per team. Singles: 1 id per team.
- `reasonCodes` ⊆ `SIMILAR_LEVEL`, `BALANCED_TEAMS`, `LONG_WAIT`; `warnings` ⊆ `WIDE_SKILL_GAP`, `UNKNOWN_LEVEL` (existing lib enums).
- `noSuggestionReason`: `null` when `suggestions` is not empty; otherwise `WAITING_FOR_PLAYERS` or `NO_FREE_COURT` (D-2; the field is not in §13's example).

Why POST for a read: §13 says POST. It writes nothing; a POST also keeps it behind CSRF and out of caches.

## 4. Data and transactions

- Tables read (playmatch schema only): `game`, `attendance`, `play_match`, `match_player`. Nothing written. **No migration.**
- One `@Transactional(readOnly = true)` method in `SuggestionSnapshotQuery` reads everything so it is one snapshot; the player-service call happens after it, outside any transaction (same as the host board).
- Eligible pool (§5.1): `attendance.state = 'READY'` **and** no `match_player` row with `released_at IS NULL` for it.
- Free courts (§5.1): `1 … game.court_count` minus `court_no` of matches in `PLANNED` / `ACTIVE`.
- Recent partners (§14): for each eligible player, the last three `FINISHED` matches (by `finished_at DESC, match id`), and in each of them the other player(s) on the **same team**. `CANCELLED`, `PLANNED`, `ACTIVE` matches are ignored. One SQL with `ROW_NUMBER() OVER (PARTITION BY attendance_id …)`; named parameters only.
- Bands: `PlayerLevelsClient.levels(playerIds of the eligible pool)`; skipped when the pool is empty. A player without a level answer counts as unknown band (logged by attendance id only, like the board).
- No cap on pool size (owner 2026-11-09: no attendance cap). §14's "from a maximum 24 `READY` players" is read as the expected size, not a rule (D-6).
- Stale suggestions are expected; F10's confirm re-reads and answers `409 MATCH_CONFLICT`.

## 5. Domain rules

- Game `CLOSED` → `409 STATE_CONFLICT` (§11 "no new assignments"). A game past its `ends_at` but still `OPEN` still gets suggestions (nothing closes games yet).
- Order of checks: no free court → `NO_FREE_COURT`; then too few ready → `WAITING_FOR_PLAYERS` (existing matcher order, so with 0 free courts and 0 ready the answer is `NO_FREE_COURT`).
- **Doubles**: unchanged `MatchSuggester` behavior (§14).
- **Singles** (D-3, new rule, owner to approve): `slots = 2 × min(freeCourts, floor(ready / 2))`; same selection order; sort selected by effective band then the same tie-breakers; consecutive pairs → courts in court-number order; Team A = first of the pair by stable UUID order, Team B = the other. `spread = |bandA − bandB|`; `spread ≥ 2` → `WIDE_SKILL_GAP`; `spread ≤ 1` → `SIMILAR_LEVEL`; `BALANCED_TEAMS` is never given (with one player per side it would repeat `SIMILAR_LEVEL`); `UNKNOWN_LEVEL` and `LONG_WAIT` as in doubles. Fewer than 2 ready → `WAITING_FOR_PLAYERS`. No "recent opponent" rule (the blueprint only penalizes repeat partners).
- **Minimum sets** (OD-19 b, D-4): the minimum is the same number for every player in a game, so "fewer matches played first" already puts every player below the minimum ahead of every player who reached it. No extra rule, no new reason code.
- `RESTING` and `PENDING`, `ASSIGNED`, `PLAYING`, `LEFT` players are never selected (only `READY`).
- A player id never appears twice across proposals (matcher invariant, tested).

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `libs/matching/.../MatchFormat.java` | new | enum `SINGLES`, `DOUBLES` | pure lib; no dependency on playmatch's enum |
| `libs/matching/.../BoardSnapshot.java` | changed | record | adds `MatchFormat format`; a 2-arg constructor keeps `DOUBLES` so the existing tests stay unchanged |
| `libs/matching/.../MatchSuggester.java` | changed | pure function | branch on format: existing doubles path untouched, new singles path (§5 above). Stays one concrete class (AD-10) |
| `libs/matching/.../MatchSuggesterTest.java` | changed | unit | adds singles cases; doubles cases untouched |
| `playmatch/match/SuggestionSnapshotQuery.java` | new | `*Query`, `JdbcClient`, read-only tx | game format/state/court count, eligible pool, free courts, recent partners |
| `playmatch/match/SuggestionService.java` | new | service (no write tx) | policy → query → levels → matcher → response |
| `playmatch/match/HostSuggestionController.java` | new | thin controller | `requireAccount`, parse id (malformed → 404), `no-store` |
| `playmatch/match/HostSuggestionResponse.java` | new | response record | `suggestions[] {courtNo, teams{A,B}, reasonCodes, warnings}`, `noSuggestionReason` |
| `playmatch/security/SecurityConfig.java` | changed | route rule | `POST /api/v1/host/games/*/suggestions` authenticated |
| `gateway/routing/GatewayRouteTable.java` | changed | route table | `host-suggestions`: `POST /api/v1/host/games/*/suggestions` → playmatch, account required |
| `GatewayAccessMatrixIT`, `PlaymatchServiceAccessIT` | changed | access matrix | rows for anonymous, guest, other account, host |
| `playmatch/.../MatchSuggestionsIT.java` | new | IT | AC-01…AC-05 |
| `API Document/backend.md`, `API Document/frontend.md`, `.claude/flow/maps/api-routes.md` | changed | docs | new route |

No new internal route (the player-levels route exists). No new dependency.

## 7. Frontend

- Host board: "Suggest matches" becomes an enabled link-button to `/host/games/:gameId/review`; "Coming soon" removed. Disabled with the existing closed note when the game is `CLOSED`.
- Route `/host/games/:gameId/review` → `features/match-review/MatchReviewPage.tsx`.
- Data: `POST suggestions` first, then `GET board` (names, levels, format). Attendance rows are never deleted, so every suggested id is on the later board. Both through `useAsync` in one loader; `api/matches.ts` gets `suggestMatches(gameId)` (POST with CSRF via `client.ts`).
- Components: `MatchProposal.tsx` (feature folder): "Court N", Team A vs Team B, each player as existing `PlayerChip` (name + level chip, "Unknown / provisional" when null), reason tags, warnings with text + icon (never color alone). Reuses `CourtCard` frame, `EmptyState`, `ErrorBanner`, `Button`.
- Buttons: "Refresh suggestions" (re-runs both requests; disabled while loading), "Back to board". **No Confirm/Start and no swap here** → F10.
- States: loading; success (proposals); empty (`WAITING_FOR_PLAYERS` → existing "Fewer than four/two players ready." by format; `NO_FREE_COURT` → new text); error: 401 → login with `next`, 403 → existing "You don't manage this game." + link home (host board decision 2026-11-26), 404 → existing not-found, 409 → closed text, 503/other → `ErrorBanner` + retry.
- Layout: phone (320–767px) one court card per row, stacked; desktop (768px+) courts side by side, 2 columns, max width 1200px (§16). Buttons ≥ 44px; "Refresh suggestions" sticky at the bottom on phone.

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|
| `matchReview.title` | Suggested matches | NEW | yes (2026-11-26) |
| `matchReview.notSaved` | Nothing is saved until you confirm a court. | NEW (§11 "Suggestion itself writes nothing") | yes (2026-11-26) |
| `matchReview.refresh` | Refresh suggestions | §16 "This lineup changed. Refresh suggestions" | yes (2026-11-26) |
| `matchReview.backToBoard` | Back to board | NEW | yes (2026-11-26) |
| `matchReview.vs` | vs | §6 "Team A vs Team B" | yes (2026-11-26) |
| `matchReview.reasonSimilarLevel` | Similar level | NEW | yes (2026-11-26) |
| `matchReview.reasonBalancedTeams` | Balanced teams | NEW | yes (2026-11-26) |
| `matchReview.reasonLongWait` | Waited longest | NEW | yes (2026-11-26) |
| `matchReview.warnWideGap` | This match may have a wide skill gap. | §6 verbatim | yes (2026-11-26) |
| `matchReview.warnUnknownLevel` | Someone's level is not known yet. | NEW | yes (2026-11-26) |
| `matchReview.noFreeCourt` | All courts are in use. | NEW | yes (2026-11-26) |
| `matchReview.closed` | This game is closed. No new matches. | NEW | yes (2026-11-26) |
| reused | Court N, Team A, Team B, Level N, Unknown / provisional, Fewer than four players ready., Fewer than two players ready., You don't manage this game., Suggest matches | existing `hostBoard.*` (approved 2026-11-25) | yes |

## 8. Security and privacy checklist

- [x] Identity only from the identity token (account); path id names the game only.
- [x] `AccessPolicy.requireHost` in the service; route rule in gateway and playmatch.
- [x] Host-only response; ids only (no names, bands, player ids). Never reused for a player or the public.
- [x] POST → CSRF checked at the gateway; no CORS change.
- [x] Logs: attendance ids only; no names.
- [x] `Cache-Control: no-store`.

## 9. Test plan

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Seed (8 READY doubles, bands 1,1,2,2,3,3,4,4, 2 free courts) → 2 proposals equal to `seededDemoGroupFormsTwoSimilarBalancedCourts`; row counts of all four tables unchanged | IT |
| TC-02 | AC-02 | 3 READY doubles → `[]` + `WAITING_FOR_PLAYERS`; all courts busy → `NO_FREE_COURT` | IT |
| TC-03 | AC-03 | Player with 4 finished matches + 1 cancelled → only partners of the last 3 finished reach the snapshot | IT (query) |
| TC-04 | AC-04 | Player in a PLANNED match, court with an ACTIVE match, PENDING/LEFT players → none of them in any proposal | IT |
| TC-05 | AC-05 | Anonymous 401, guest 401, other account 403, unknown/malformed id 404, host 200; CLOSED game 409 `STATE_CONFLICT` | access matrices + IT |
| TC-06 | AC-07 | Singles, 5 READY, 2 free courts → 2 proposals of 1 vs 1, no id twice, pairs by adjacent band; 1 READY → `WAITING_FOR_PLAYERS` | unit + IT |
| TC-07 | AC-07 | Singles bands 1 vs 3 → `WIDE_SKILL_GAP`, no `BALANCED_TEAMS`; null band → `UNKNOWN_LEVEL` | unit |
| TC-08 | AC-08 | player-service stub down → 503; row counts unchanged | IT |
| TC-09 | AC-01 | Existing doubles `MatchSuggesterTest` cases pass unchanged | unit |
| TC-10 | AC-06 | Review page at 320 / 768 / 1280px: names, level chips, reason tags, wide-gap text, unknown level, empty and error states | visual (owner) |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | Where does the host see suggestions? | (a) own review page `/host/games/:gameId/review` (§16) · (b) inside the host board | **(a)**, §16 route; F10 adds swap/confirm there |
| D-2 | Field for "no suggestion" reason (not in §13 example) | (a) `noSuggestionReason` next to `suggestions` · (b) HTTP 204 | **(a)** |
| D-3 | Singles rule (OD-20; blueprint only defines doubles) | (a) as §5: 2 per court, pair adjacent bands, no `BALANCED_TEAMS`, no repeat-opponent rule · (b) also avoid repeat opponents | **(a)** now; (b) → later backlog if the pilot asks |
| D-4 | OD-19 "favour players below the minimum" | (a) already met by "fewer matches played first" · (b) extra rule / reason tag | **(a)**, no new code |
| D-5 | Player names on the review page | (a) response has ids only (§13), page reads names from the board · (b) add names/bands to the response | **(a)**, spec shape |
| D-6 | §14 "maximum 24 READY players" | (a) not a rule, no cap · (b) cap the pool | **(a)** (owner: no attendance cap) |

## 11. Out of scope

- Tap-to-swap, manual pairing, Confirm → Match confirm (F10).
- Start / finish / cancel → Match start / finish / cancel (F11).
- Board polling (host board D-4) → later if the pilot needs it.
- Repeat-opponent rule for singles → later backlog (if D-3 (b) is wanted).
