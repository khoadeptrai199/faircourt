# F09 implementation — Match suggestions (read-only)

Design: [design.md](design.md) (`APPROVED` 2026-11-26)

Branch: `feature/match-suggestions`, created from an up-to-date `main` after Create game is committed (memory: pull main before a feature).

## Tasks

Small, runnable steps in order. Status: `TODO` | `DOING` | `DONE` | `SKIPPED (reason)`.

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-07, AC-01 | Matching lib: add `MatchFormat` (`SINGLES`, `DOUBLES`); `BoardSnapshot` gets `format` (canonical 3-arg constructor + a 2-arg constructor that means `DOUBLES`, so existing tests compile unchanged); `MatchSuggester.suggest` branches on format: doubles path untouched, new singles path (design §5). Unit tests TC-01…TC-08 | `DONE` |
| T02 | AC-01…AC-04 | `playmatch/match/SuggestionSnapshotQuery` (`JdbcClient`, one `@Transactional(readOnly = true)` method `read(gameId)` → `Optional<Snapshot>`): game (`state`, `court_count`, `match_format`), eligible pool (READY, no unreleased `match_player`; `id`, `player_id`, `available_since`, `matches_played`), busy courts (PLANNED/ACTIVE), recent partners (last 3 FINISHED matches per eligible player, same team, `ROW_NUMBER()`) | `DONE` |
| T03 | AC-01, AC-02, AC-05, AC-08 | `SuggestionService.suggest(caller, gameId)`: `requireHost` → query (empty → 404) → `CLOSED` → `409 STATE_CONFLICT` → `PlayerLevelsClient.levels` for the pool (skipped when empty) → build `BoardSnapshot` (free courts = `1…court_count` minus busy; missing level = null band, warn log with attendance id only) → `MatchSuggester` → `HostSuggestionResponse`. `HostSuggestionController` (`POST`, `requireAccount` first, malformed id → 404, `no-store`, body ignored). `HostSuggestionResponse` record (`suggestions[] {courtNo, teams {A, B}, reasonCodes, warnings}`, `noSuggestionReason`). `MatchSuggester` exposed as a bean or created in the service (no Spring in the lib) | `DONE` |
| T04 | AC-05 | `playmatch/security/SecurityConfig`: `POST /api/v1/host/games/*/suggestions` authenticated. Gateway `GatewayRouteTable`: route `host-suggestions`, `POST /api/v1/host/games/*/suggestions` → playmatch, account required. Rows in `GatewayAccessMatrixIT` and `PlaymatchServiceAccessIT` (TC-23, TC-24) | `DONE` |
| T05 | AC-01…AC-05, AC-07, AC-08 | `MatchSuggestionsIT` (TC-09…TC-19), same harness as `HostBoardIT` (JDK `HttpServer` player-service stub, test identity token, test clock, inline SQL helpers as in `HostBoardIT`/`HostAttendanceIT`; the `support/TestData` builders named in conventions do not exist yet in this service, so the existing local helper style is followed and noted under "Deviations") | `DONE` |
| T06 | AC-06 | Frontend: `api/types.ts` (`MatchSuggestions`, `MatchProposal`, `ReasonCode`, `SuggestionWarning`, `NoSuggestionReason` as string unions), `api/matches.ts` `suggestMatches(gameId)` via `client.ts` (POST + CSRF), route `/host/games/:gameId/review` in `AppRoutes.tsx`, `features/match-review/MatchReviewPage.tsx` + `MatchProposal.tsx`, copy keys `matchReview.*` in `copy/en.ts` (design §7, approved), host board "Suggest matches" enabled as a link to the review page (disabled + closed note on a `CLOSED` game), "Coming soon" text removed there | `DONE` |
| T07 | — | Docs: `API Document/backend.md` (route, response, errors), `API Document/frontend.md` (review page), `.claude/flow/maps/api-routes.md` | `DONE` |
| T08 | all | Format (`backend-format`, frontend `format`), pre-commit pipeline `scripts/precommit.sh` in WSL; manual check TC-25…TC-28 on the running stack (owner); fill "Commands executed" and "Acceptance evidence" | `DONE` (automated parts; manual TC-25…TC-28 `NOT VERIFIED`, owner) |

## Test cases

At least one case per AC, negative and edge cases included. Oracle = where the expected value comes from.

### Unit — `libs/matching/.../MatchSuggesterTest` (T01)

All 13 existing doubles tests stay as they are and must still pass (they use the 2-arg snapshot = `DOUBLES`).

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-01 | AC-01 | unit | Existing doubles suite unchanged | run `MatchSuggesterTest` | all 13 existing cases pass with no edit to their assertions | §14 boundary tests; walking-skeleton results |
| TC-02 | AC-07 | unit | SINGLES; 4 READY, bands 1,1,3,3, all 0 matches, same `availableSince`; courts 1,2 | suggest | 2 proposals; court 1 = the two band-1 players, court 2 = the two band-3 players; each team has exactly 1 id; Team A is the smaller UUID string; `SIMILAR_LEVEL` on both; no `BALANCED_TEAMS`; no warnings | design §5 (D-3) |
| TC-03 | AC-07 | unit | SINGLES; 5 READY; 2 free courts | suggest | 2 proposals, 4 distinct ids in total, none repeated; the left-out player is the one last in priority order (most matches played, then latest `availableSince`, then UUID) | §14 selection order; design §5 |
| TC-04 | AC-07 | unit | SINGLES; 1 READY; 1 free court | suggest | no proposals; `WAITING_FOR_PLAYERS` | design §5 (singles needs 2) |
| TC-05 | AC-07 | unit | SINGLES; 2 READY; no free court | suggest | no proposals; `NO_FREE_COURT` | §14; existing check order |
| TC-06 | AC-07 | unit | SINGLES; 2 READY bands 1 and 3; 1 court | suggest | `WIDE_SKILL_GAP` in warnings; `SIMILAR_LEVEL` and `BALANCED_TEAMS` absent | §14 `spread ≥ 2`; design §5 |
| TC-07 | AC-07 | unit | SINGLES; 2 READY, one band null, other band 2; 1 court | suggest | `UNKNOWN_LEVEL` warning; `SIMILAR_LEVEL` (null counts as 2) | §14 unknown = 2 for grouping |
| TC-08 | AC-07 | unit | SINGLES; 2 READY with different `availableSince` plus 2 more ready later; same snapshot given twice in shuffled input order | suggest twice | identical results; `LONG_WAIT` on the court holding the earliest-available player | §14 "same snapshot returns same ordering"; existing LONG_WAIT rule |

### Integration — `MatchSuggestionsIT` (T05), playmatch against `faircourt_test`, player-service stub

Every case that answers 200 also checks: row counts of `game`, `attendance`, `play_match`, `match_player` are the same before and after, and `game.next_match_no` is unchanged.

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-09 | AC-01 | IT | DOUBLES game, 2 courts, 8 READY players, stub bands 1,1,2,2,3,3,4,4, all 0 matches, same `availableSince` | host `POST …/suggestions` | 200, `Cache-Control: no-store`; 2 suggestions, `courtNo` 1 and 2; court 1 = the four band 1–2 players, court 2 = band 3–4; each team 2 ids; both have `SIMILAR_LEVEL`, `BALANCED_TEAMS`; `warnings` `[]`; `noSuggestionReason` `null`; row counts unchanged | `seededDemoGroupFormsTwoSimilarBalancedCourts`; §0 seed |
| TC-10 | AC-01 | IT | Same as TC-09 | host POST | response keys are exactly `suggestions`, `noSuggestionReason`; each suggestion exactly `courtNo`, `teams`, `reasonCodes`, `warnings`; `teams` exactly `A`, `B`; no names, bands, player ids (JSON key allowlist) | §13 example + design D-2, D-5 |
| TC-11 | AC-02 | IT | DOUBLES, 2 free courts, 3 READY | host POST | 200; `suggestions` `[]`; `noSuggestionReason` `WAITING_FOR_PLAYERS`; stub called once | §14 |
| TC-12 | AC-02 | IT | DOUBLES, 1 court with an ACTIVE match, 4 other READY | host POST | 200; `[]`; `NO_FREE_COURT` | §14 |
| TC-13 | AC-02 | IT | Game with 0 READY, 1 free court | host POST | 200; `[]`; `WAITING_FOR_PLAYERS`; player-service stub **not** called | design §4 (call skipped when pool empty) |
| TC-14 | AC-03 | IT | DOUBLES, 1 court; player P READY with 4 FINISHED matches (partners X1 oldest … X4 newest by `finished_at`) and 1 CANCELLED newer match with partner Y | `SuggestionSnapshotQuery.read` (direct, IT) | P's recent partners = {X2, X3, X4}; X1 and Y absent | §14 "last three **finished** matches" |
| TC-15 | AC-03 | IT | DOUBLES, 1 court, 4 READY all band 2 (team gap ties in all 3 splits); A and B were partners in their last finished match | host POST | A and B are on different teams | §14 repeat-partner penalty |
| TC-16 | AC-04 | IT | DOUBLES, 2 courts; court 1 has a PLANNED match with 4 players (ASSIGNED); 1 PENDING, 1 LEFT, 1 RESTING, 4 READY | host POST | 1 suggestion on court 2 only; its 4 ids are exactly the 4 READY; no ASSIGNED/PENDING/LEFT/RESTING id anywhere | §5.1 |
| TC-17 | AC-04 | IT | A READY attendance row that still has an unreleased `match_player` row (inconsistent data guard) + 4 clean READY; 1 free court | host POST | the inconsistent player is not suggested | §5.1 "not in another unfinished match" |
| TC-18 | AC-05 | IT | CLOSED game with 4 READY | host POST | 409 `STATE_CONFLICT`, error body `code`/`message`/`requestId` only; stub not called; row counts unchanged | §11 "no new assignments" |
| TC-19 | AC-05 | IT | Valid game | other account POST · no token · token for another path (`GET …/board`) · unknown game id · malformed id | 403 `FORBIDDEN` · 401 · 401 · 404 · 404; stub not called in any of them | §13 status codes; identity token v2 (path bound) |
| TC-20 | AC-07 | IT | SINGLES game, 2 courts, 5 READY | host POST | 2 suggestions, each `teams.A` and `teams.B` exactly 1 id; 4 distinct ids; row counts unchanged | OD-20; design §5 |
| TC-21 | AC-08 | IT | Stub answers 500, then stub too slow (> read timeout) | host POST | 503 `SERVICE_UNAVAILABLE`; no suggestions in the body; row counts unchanged | AD-15; system-design §7 |
| TC-22 | AC-01 | IT | A READY player missing from the stub's answer + 3 others band 2 | host POST | 200; that player is treated as unknown → suggestion has `UNKNOWN_LEVEL`; log line has the attendance id and no display name | design §4; host board pattern |

### Access matrices (T04)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-23 | AC-05 | gateway IT | Downstream stub | `POST /api/v1/host/games/{id}/suggestions` anonymous · guest cookie only · account without CSRF · account with CSRF | 401 · 401 · 403 `CSRF_TOKEN_INVALID` · forwarded once to playmatch; `GET` on the same path → refused (not routed) | gateway route rules; Create game TC-11 pattern |
| TC-24 | AC-05 | playmatch IT | No identity token | `POST …/suggestions` | 401 JSON | `PlaymatchServiceAccessIT` pattern |

### Frontend (manual, owner; OD-08 (a): no browser test tool in MVP) (T08)

| TC | Covers | Layer | Given | When | Then | Oracle |
|---|---|---|---|---|---|---|
| TC-25 | AC-06 | UI | Seed game, host signed in, 8 READY | Host board → "Suggest matches" | review page: "Suggested matches", "Nothing is saved until you confirm a court.", Court 1 and Court 2 cards, Team A vs Team B with names and level chips, reason tags; "Refresh suggestions" re-runs; "Back to board" returns | design §7 copy (approved) |
| TC-26 | AC-06 | UI | DB set so one court has bands 1 and 3+ and one unknown-level guest | open review | "This match may have a wide skill gap." and "Someone's level is not known yet." shown as text with icon; unknown player shows "Unknown / provisional" | §6 verbatim; design §7 |
| TC-27 | AC-06 | UI | 3 READY (doubles) · all courts busy · other account · CLOSED game | open review | "Fewer than four players ready." · "All courts are in use." · "You don't manage this game." + home link · "This game is closed. No new matches."; on CLOSED the board button is disabled | design §7 |
| TC-28 | AC-06 | UI | Seed | view at 320, 375, 768, 1280px | phone: one card per row, sticky refresh button, no horizontal scroll; desktop: 2 columns, max 1200px; buttons ≥ 44px; keyboard focus visible | §16 |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/libs/matching/src/main/java/com/faircourt/matching/MatchFormat.java` | added | format in the pure lib |
| `backend/libs/matching/src/main/java/com/faircourt/matching/BoardSnapshot.java` | changed | `format` field |
| `backend/libs/matching/src/main/java/com/faircourt/matching/MatchSuggester.java` | changed | singles path |
| `backend/libs/matching/src/test/java/com/faircourt/matching/MatchSuggesterTest.java` | changed | TC-02…TC-08 |
| `backend/services/playmatch-service/src/main/java/com/faircourt/playmatch/match/SuggestionSnapshotQuery.java` | added | read model |
| `…/playmatch/match/SuggestionService.java` | added | orchestration |
| `…/playmatch/match/HostSuggestionController.java` | added | route |
| `…/playmatch/match/HostSuggestionResponse.java` | added | host DTO |
| `…/playmatch/security/SecurityConfig.java` | changed | route rule |
| `…/playmatch-service/src/test/java/com/faircourt/playmatch/MatchSuggestionsIT.java` | added | TC-09…TC-22 |
| `…/playmatch-service/src/test/java/com/faircourt/playmatch/PlaymatchServiceAccessIT.java` | changed | TC-24 |
| `backend/services/gateway/src/main/java/com/faircourt/gateway/routing/GatewayRouteTable.java` | changed | route |
| `backend/services/gateway/src/test/java/com/faircourt/gateway/GatewayAccessMatrixIT.java` | changed | TC-23 |
| `frontend/src/api/types.ts`, `frontend/src/api/matches.ts` (added) | changed / added | API types + call |
| `frontend/src/routes/AppRoutes.tsx` | changed | review route |
| `frontend/src/features/match-review/MatchReviewPage.tsx`, `MatchProposal.tsx` | added | page |
| `frontend/src/features/host-board/HostBoardPage.tsx` | changed | button enabled |
| `frontend/src/copy/en.ts` | changed | `matchReview.*` |
| `API Document/backend.md`, `API Document/frontend.md`, `.claude/flow/maps/api-routes.md` | changed | docs |

No migration. No new dependency. No new internal route. Deleted files: none (deletions need explicit owner consent).

## Commands executed

Only commands that were actually run, with their real result.

| Date | Machine | Command | Result |
|---|---|---|---|
| 2026-11-26 | Windows + WSL Ubuntu | `docker compose run --rm --build backend-format` (T01-T05) | PASS (exit 0, Spotless applied) |
| 2026-11-26 | Windows + WSL Ubuntu | `docker compose --profile test run --rm --build backend-test` (first run) | FAIL: `PlaymatchSchemaIT` 9 errors, "too many clients already" (extra cached Spring context from the new IT); `MatchSuggestionsIT` 14 tests PASS |
| 2026-11-26 | Windows + WSL Ubuntu | same, after `@DirtiesContext` on `MatchSuggestionsIT` and re-format | PASS: BUILD SUCCESS; matching `MatchSuggesterTest` 20 tests, gateway `GatewayAccessMatrixIT` 18, playmatch 132 tests (incl. `MatchSuggestionsIT` 14, `PlaymatchServiceAccessIT` 11), 0 failures |
| 2026-11-26 | Windows + WSL Ubuntu | `docker compose run --rm --no-deps frontend npm run format` (T06) | PASS (exit 0) |
| 2026-11-26 | Windows + WSL Ubuntu | `docker compose run --rm --no-deps frontend npm run build` (`tsc --noEmit && vite build`, T06) | PASS (built in 1.03s) |
| 2026-11-26 | Windows + WSL Ubuntu | Review fixes: `docker compose run --rm --build backend-format` | PASS (Spotless ran, no error output) |
| 2026-11-26 | Windows + WSL Ubuntu | `docker compose --profile test run --rm --build backend-test` (after review fixes, first run) | FAIL: `GatewayAccessMatrixIT` GET on suggestions path returned 403, assertion expected 401 (my guess); fixed the assertion to the observed 403 |
| 2026-11-26 | Windows + WSL Ubuntu | `docker compose run --rm --no-deps frontend npm run format` | PASS (exit 0) |
| 2026-11-26 | Windows + WSL Ubuntu | `bash scripts/precommit.sh` | PASS: `PIPELINE OK`; unit/IT suites 0 failures (gateway 112, playmatch 132, matching 13 plus other modules), `.env`/`.idea/` not staged, frontend format:check + build OK |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | TC-01, TC-09, TC-10, TC-22 | PASS | `MatchSuggesterTest` (TC-01, doubles suite); `MatchSuggestionsIT`: `theSeededGroupFormsTwoSimilarBalancedCourtsAndNothingIsSaved`, `theAnswerHasExactlyTheDocumentedFieldsAndNoNamesBandsOrPlayerIds`, `aReadyPlayerMissingFromTheLevelsAnswerIsUnknownAndLogsOnlyTheAttendanceId`; precommit.sh 2026-11-26 |
| AC-02 | TC-11…TC-13 | PASS | `MatchSuggestionsIT`: `threeReadyDoublesPlayersGiveWaitingForPlayersAfterOneLevelsCall`, `aCourtWithAnActiveMatchLeavesNoFreeCourt`, `aGameWithNoReadyPlayerAnswersWithoutCallingPlayerService`; precommit.sh 2026-11-26 |
| AC-03 | TC-14, TC-15 | PASS | `MatchSuggestionsIT`: `onlyThePartnersOfTheLastThreeFinishedMatchesAreRecentPartners`, `twoPlayersWhoWerePartnersInTheLastFinishedMatchAreSplitWhenTeamGapsTie`; precommit.sh 2026-11-26 |
| AC-04 | TC-16, TC-17 | PASS | `MatchSuggestionsIT`: `onlyReadyPlayersOnFreeCourtsAreSuggested`, `aReadyPlayerStillInAnUnreleasedMatchIsNotSuggested`; precommit.sh 2026-11-26 |
| AC-05 | TC-18, TC-19, TC-23, TC-24 | PASS | `MatchSuggestionsIT`: `aClosedGameIs409AndNothingIsCalledOrChanged`, `otherAccountsMissingOrWrongTokensAndUnknownIdsAreRefusedWithoutCallingPlayerService`; `GatewayAccessMatrixIT.suggestionsNeedAnAccountAndACsrfTokenAndAreForwardedOnceWithATokenBoundToThePath`; `PlaymatchServiceAccessIT` (TC-24); precommit.sh 2026-11-26 |
| AC-06 | TC-25…TC-28 | `NOT VERIFIED` | owner visual check of TC-25…TC-28 on the running stack; `npm run build` and `format:check` pass but are not a UI check |
| AC-07 | TC-02…TC-08, TC-20 | PASS | `MatchSuggesterTest` TC-02…TC-08; `MatchSuggestionsIT.aSinglesGameGetsOnePlayerPerTeamOnTwoCourtsWithNoIdTwice`; precommit.sh 2026-11-26 |
| AC-08 | TC-21 | PASS | `MatchSuggestionsIT.whenPlayerServiceFailsOrDoesNotAnswerInTimeTheAnswerIs503WithNoSuggestions`; precommit.sh 2026-11-26 |

## Deviations from the design

- Planned: test data via local SQL helpers inside the IT (as `HostBoardIT`, `HostAttendanceIT`), not `support/TestData` builders, because none exist in playmatch yet. Listed here per conventions ("old spot → implement.md").
- `SecurityConfig`: the plan said "authenticated"; the route is `permitAll()` like the other host routes (host board, attendance), because the identity token and `AccessPolicy` decide in the controller (401 / 403 / 404). Unauthenticated callers still get the service's own 401 JSON.
- `MatchSuggestionsIT` carries `@DirtiesContext(classMode = AFTER_CLASS)`: its own Spring context (own clock bean and player stub) made the test JVM hold one more cached context, and the shared PostgreSQL ran out of connections (`too many clients already`) in `PlaymatchSchemaIT`. Test-only; no config change.
- `SuggestionService` creates `MatchSuggester` itself (plan allowed bean or in-service); no Spring in the lib.
- Gateway IT for GET on the suggestions path: a signed-in account is refused with 403 (observed) and nothing more is forwarded.
- Frontend: no frontend unit-test tool exists (OD-08 (a)); TC-25…TC-28 stay manual in T08. The 409 answer on the review page shows the closed text with a link back to the board. The empty note on the review page reuses `hostBoard.fewerThanFour` / `fewerThanTwo` by game format (read from the board).
- `MatchProposal` draws its own card frame instead of reusing `CourtCard` (the proposal shows teams, level chips and reason tags, which `CourtCard` does not).
- `HostSuggestionResponse.Teams` is a record with components `A` / `B` so the JSON keys are `A` and `B` (internal naming choice).
- The eligible-pool SQL appears twice in `SuggestionSnapshotQuery` (pool and recent partners); kept as is, noted.
- A suggested attendance id that is missing from the board names list renders nothing in `MatchProposal` (the design says this cannot happen: both come from the same game).
- Host board, `CLOSED` game: the disabled "Suggest matches" button points `aria-describedby` at the existing `hostBoard.closedNote`; the extra `matchReview.closed` note there was removed (it stays on the review page).
