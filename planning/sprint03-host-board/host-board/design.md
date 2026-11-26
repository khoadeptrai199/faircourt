# F06 design — Host board

Status: `APPROVED` (2026-11-25, by owner: OD-04 = a, D-1 = a, D-3 = a, all other recommendations and the copy table)

## 1. Summary

The host of one game opens `/host/games/:gameId` and sees the whole game: every attendance row (Pending, Ready, Assigned, Playing, Resting, Left) with name, private level and level status, wait time and sets played, and every court 1…`court_count` with its current match or "Free". Nobody else can read it. The page only reads; every action on the board comes with later features (Attendance management, Match suggestions, Match confirm, Match start/finish/cancel, Host skill assessment).

This is the **first host route** and the **first route a service answers for a known caller**, so it also ships three reusable pieces that every later host feature uses:

1. **Identity token** (AD-14, deferred to this feature by the Account login design D-4): the gateway adds a short-lived signed header to forwarded requests of a signed-in account; a service rebuilds `CallerIdentity` only from that header. Shared code in `common-web` (AD-18).
2. **`AccessPolicy.requireHost(caller, gameId)`** in playmatch-service (§15): the service-level owner check, run by every host route.
3. **Board read model**: `HostBoardQuery` (playmatch, own schema) + one batch call to player-service for private levels (system-design §7, "Host board, suggestions: one batch call for the bands").

Plus the entry point to the board (OD-04, recommendation: a "Games you host" list).

## 2. Blueprint references

- §3: "Only the host sees the full `Pending`, `Ready`, `Assigned`, `Playing`, `Resting`, and `Left` board and can tap **Suggest next matches**."
- §3 acceptance: "a visitor remains visibly provisional to the host".
- §3 table: "View another player's assignment, private skill evidence or full ready pool — Anyone: No, Joined player: No, Court host: Yes". "Here 'court host' is the person running **this play session**".
- §6: "**Host board** — Full Pending, Ready, Assigned, Playing, Resting, Left pool; court cards; waiting-time chips; host only — Primary action `Suggest matches`". "On mobile web: … the host gets a separate `Manage` entry. On desktop: a wider host board with courts beside the ready pool."
- §6: "**Host-board composition:** top strip with tonight's session; one large green 'Suggest matches' button; horizontal court cards with A/B teams and level chips; below, people waiting longest."
- §11: "Host authority is checked by `play_session.host_user_id`, not a global `ADMIN` flag." "The UI must show `ASSIGNED` separately from `PLAYING`."
- §13: "`GET /host/sessions/{sessionId}/board` — This session's host — Full attendance list, private bands, current matches and courts." "`GET /host/.../board` may contain names and private bands; it must never be reused as a player response." "If all four answers are unknown … set `current_band = null`, and show an **Unknown / provisional** badge to the host."
- §13: "`401` missing/expired identity, `403` authenticated but no permission, `404` unknown or private resource not visible".
- §15: "The host access policy takes `(principalUserId, sessionId)` and checks the persisted owner for **every** host mutation; Spring Security route rules provide an initial gate, and service-level ownership does the resource-specific check."
- §16: "`/host/sessions/:sessionId` — Host board — Phone: Court carousel above player status lists; sticky Suggest button — Desktop: Courts in 2–4 columns beside Ready pool — Empty/error: No ready players, fewer than four, occupied courts, request failure". "`403` shows 'You don't manage this session' and offers the public page." "max content width 1200px for host".
- §16 component inventory: `CourtCard`, `PlayerChip` (host only), `WaitIndicator` (host only), `StatusBadge`, `EmptyState`, `ErrorBanner`.

Routes renamed by OD-22: `/host/sessions/...` → `/host/games/...`.

## 3. API contract

### 3.1 `GET /api/v1/host/games/{gameId}/board` (§13)

| Item | Value |
|---|---|
| Route | `GET /api/v1/host/games/{gameId}/board`; gateway forwards to playmatch-service (same path) |
| Caller | this game's host |
| Identity source | account session at the gateway → identity token → `CallerIdentity.Account` in playmatch (never a path or body id) |
| Access check | gateway: route kind `HOST` = signed-in account (first gate); playmatch: `AccessPolicy.requireHost(caller, gameId)` (§15) |
| Request | no body; `gameId` UUID (malformed → 404) |
| Response | `200`, `Cache-Control: no-store`, `HostBoardResponse` below |
| Allowed response keys | host only; not reused for any other audience (§13). Fields listed below are the full set |
| Errors | anonymous → `401 UNAUTHENTICATED` (gateway); guest cookie only → `401 UNAUTHENTICATED` (D-3); signed in but not this game's host → `403 FORBIDDEN`; unknown game → `404 NOT_FOUND`; player-service down → `503 SERVICE_UNAVAILABLE` (no partial board) |

```json
{
  "asOf": "2026-11-25T12:30:00Z",
  "game": {
    "id": "uuid", "publicCode": "FRI7Q2K9", "title": "Friday Badminton", "venue": "…",
    "startsAt": "…", "endsAt": "…", "courtCount": 2, "state": "OPEN",
    "matchFormat": "DOUBLES", "minSets": 6
  },
  "players": [{
    "attendanceId": "uuid", "displayName": "Mai", "kind": "GUEST",
    "state": "PENDING", "band": null, "bandStatus": "SELF_REPORTED",
    "checkedInAt": "…", "availableSince": "…", "matchesPlayed": 0
  }],
  "courts": [
    {"courtNo": 1, "match": null},
    {"courtNo": 2, "match": {"matchId": "uuid", "matchNo": 3, "state": "PLANNED",
                             "teams": {"A": ["attendance-uuid"], "B": ["attendance-uuid"]}}}
  ]
}
```

- `asOf`: server time of the read, so wait minutes do not depend on the phone's clock (D-6).
- `players`: every attendance row of the game, including `LEFT` (§3). Order: state group, then `matchesPlayed ASC`, `availableSince ASC`, `attendanceId` (the matcher's order, §14, so "people waiting longest" come first).
- `kind`: `MEMBER` | `GUEST` (player-service). `band`: 1–4 or `null`. `bandStatus`: `SELF_REPORTED` | `OBSERVED` | `CONFIRMED`.
- `courts`: exactly `courtCount` entries, `courtNo` 1…n. `match` is the one `PLANNED` or `ACTIVE` match on that court (the `ux_busy_court` index allows at most one), else `null`. `teams` lists attendance ids of the unreleased `match_player` rows; one per team for singles, two for doubles. Empty in practice until Match confirm; tested with test data.
- No `userId`, `playerId`, email, guest token or questionnaire answers.

### 3.2 `GET /api/v1/host/games` — games I host (NEW, only if OD-04 = a)

| Item | Value |
|---|---|
| Route | `GET /api/v1/host/games`; gateway forwards to playmatch-service (same path) |
| Caller | any signed-in account (answers only the caller's own games) |
| Identity source | identity token → `CallerIdentity.Account` |
| Access check | gateway `HOST` kind (signed in); playmatch filters `host_user_id = caller.userId` — no other check needed |
| Request | none |
| Response | `200`, `no-store`: `{"games":[{"id","publicCode","title","venue","startsAt","endsAt","courtCount","state"}]}`, `starts_at DESC`, at most 50 |
| Errors | anonymous / guest only → `401`; player has no hosted games → `200` with `[]` |

### 3.3 Internal route `POST /internal/v1/players/levels` (NEW, AD-15, needs approval)

| Item | Value |
|---|---|
| Route | playmatch-service → player-service, never routed by the gateway |
| Request | `{"playerIds": ["uuid", …]}`, 1–200 ids, no duplicates required |
| Response | `200`, `no-store`: `{"players":[{"playerId","kind","band","bandStatus"}]}` — one entry per known id; unknown ids are left out |
| Errors | invalid body → 400; player-service down → caller maps to 503 |
| Why POST | a game can have many players; ids in a body instead of a long query string. It writes nothing |

### 3.4 Identity token (internal header, NEW plumbing; AD-14)

| Item | Value |
|---|---|
| Header | `X-Internal-Identity` (prefix `X-Internal-` is already stripped from browser requests by `HeaderSanitizer`) |
| Added by | gateway, on **forwarded** routes only, when the request has a signed-in account |
| Format | `v1.<base64url(JSON claims)>.<base64url(HMAC-SHA256(secret, "v1." + claims))>` — JDK `javax.crypto.Mac`, no new dependency (D-1) |
| Claims | `{"kind":"ACCOUNT","userId":"uuid","iat":<epoch s>,"exp":<iat+30>}` |
| Secret | env `INTERNAL_TOKEN_SECRET` (name from Account login D-4), at least 32 bytes, in `.env` / `.env.example`, passed by `compose.yaml` to gateway and every service that reads identity; service refuses to start without it |
| Verified by | `common-web` `IdentityTokenVerifier` + `CallerIdentity` method argument resolver: missing header → `CallerIdentity.Anonymous`; bad signature, wrong version, expired, malformed → `401 UNAUTHENTICATED` (logged as one line without the token) |
| Guest kind | not issued in this feature (no forwarded guest route yet); the claim format leaves room for `"kind":"GUEST","attendanceId","gameId"` |

## 4. Data and transactions

- **No migration.** Reads only.
- playmatch (`playmatch` schema): `game` (one row by id), `attendance` (all rows of the game), `play_match` + `match_player` (`state IN ('PLANNED','ACTIVE')`, `released_at IS NULL`). Index `ix_attendance_pool` covers the attendance read; the game is read by primary key.
- player (`player` schema): `player_profile` by id list (`id = ANY(:ids)`).
- No transaction needed for reads; `HostBoardQuery` runs its SQL in one `@Transactional(readOnly = true)` method so the three reads see one snapshot (PostgreSQL `READ COMMITTED` per statement otherwise). The player-service call happens **after** the playmatch reads, outside any transaction.
- `GET /host/games`: `game` by `host_user_id`; index `ix_game_host_start` covers it.

## 5. Domain rules and state transitions

- No state changes.
- Host = `game.host_user_id = caller.userId` (§11). Not a role flag. The host may also be a player in the same game (OD-11 = b); then they appear in `players` like anyone else.
- Order of `403` vs `404`: game does not exist → 404; exists but caller is not its host → 403 (§13: 403 "authenticated but no permission"; the §16 page "You don't manage this session" needs it). Knowing that a random UUID exists gives nothing away.
- A `CLOSED` game still shows its board (read-only history, §11).
- Wait time shown only for `READY` players: `asOf − availableSince`, whole minutes (OD-14 = minutes only).
- Level label: `band` 1–4 → "Level n"; `null` → "Unknown / provisional" (§13). A `SELF_REPORTED` level gets a "Provisional" tag so a visitor stays visibly provisional (§3).
- An attendance whose `player_id` is missing in the player-service answer (should not happen) is shown with `band = null`, `kind = null`, `bandStatus = null` → "Unknown / provisional"; logged as a warning with the attendance id only.

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `libs/common-web/.../identity/CallerIdentity.java` | new | sealed interface `Anonymous` / `Account(userId)` (`Guest` later) | AD-05 amended by AD-14 |
| `libs/common-web/.../identity/IdentityToken.java` | new | value + HMAC sign/verify, `Clock` injected | no new dependency |
| `libs/common-web/.../identity/CallerIdentityArgumentResolver.java` | new | `HandlerMethodArgumentResolver`, registered by `CommonWebAutoConfiguration` | only source of identity in services |
| `libs/common-web/.../CommonWebAutoConfiguration.java` | changed | registers resolver + verifier when `faircourt.internal-token.secret` is set | |
| `gateway/.../routing/RouteAccess.java` | changed | new kind `HOST` = signed-in account at the gateway; the real host check is in the service | |
| `gateway/.../routing/IdentityTokenFilter.java` | new | before-filter on forwarded routes, after `HeaderSanitizer` | adds `X-Internal-Identity` only for an account |
| `gateway/.../routing/GatewayRoutes.java`, `GatewayRouteTable.java` | changed | two routes `host-board`, `host-games` (`GET`, `HOST`, → playmatch) | |
| `gateway/.../security/GatewaySecurityConfig.java` | changed | `HOST` → `.authenticated()` | |
| `playmatch/.../access/AccessPolicy.java` | new | `requireHost(CallerIdentity, gameId)`: 401 non-account, 404 unknown game, 403 not owner | used by every later host route |
| `playmatch/.../board/HostBoardController.java` | new | thin controller, `CallerIdentity` arg | |
| `playmatch/.../board/HostBoardQuery.java` | new | `JdbcClient`, text-block SQL, named params | game, attendance, current matches |
| `playmatch/.../board/HostBoardService.java` | new | read orchestration: policy → query → levels → compose | no `@Transactional` write |
| `playmatch/.../board/HostBoardResponse.java` | new | records `HostBoardResponse`, nested `Game`, `Player`, `Court`, `Match`, `Teams` | host audience only |
| `playmatch/.../board/HostGamesController.java`, `HostGamesQuery.java`, `HostGamesResponse.java` | new (OD-04 = a) | same patterns | |
| `playmatch/.../player/PlayerLevelsClient.java` | new | `RestClient` via `ServiceClients`, 503 on failure | first non-guest client lands in a neutral package |
| `playmatch/.../security/SecurityConfig.java` | changed | open the two `/api/v1/host/games…` GET routes (identity checked by resolver + policy) | |
| `player/.../level/PlayerLevelsController.java`, `PlayerLevelsQuery.java`, request/response records | new | internal route 3.3 | |
| `player/.../security/SecurityConfig.java` | changed | open `POST /internal/v1/players/levels` | |
| `compose.yaml`, `.env.example` | changed | `INTERNAL_TOKEN_SECRET` to gateway, playmatch (and later services) | new config entry — needs approval (D-1) |

## 7. Frontend

- Routes: `/host/games/:gameId` (board, §16); entry per OD-04 (recommended: a "Games you host" section on the home page when signed in, from `GET /api/v1/host/games`).
- API: `api/host.ts` (`getHostBoard(gameId)`, `getHostedGames()`); types `HostBoard`, `HostBoardPlayer`, `HostBoardCourt`, `HostedGame` in `api/types.ts`.
- Components (feature `features/host-board/`): `HostBoardPage`, `GameStrip` (top strip: title, time, venue, courts, format, public link), `PlayerList` (one section per state). Shared (`components/`): `CourtCard` (used later by Match review), `PlayerChip` (host only: name, level chip, Provisional tag), `WaitIndicator` (host only, minutes).
- `lib/status.ts`: host-side labels for all six attendance states and the two shown match states (`PLANNED` → "Next up", `ACTIVE` → "Playing") — text + tone, never color alone (§6). `ASSIGNED` and `PLAYING` have separate labels here (§11).
- "Suggest matches": one large sticky button, **disabled** with "Coming soon" until Match suggestions (D-5).
- Refresh: an explicit "Refresh" button re-runs the read; no polling (D-4).
- States: `useAsync` → loading / success / empty / error, plus page-level variants:
  - `403` → "You don't manage this game." + link to the public page (§16 copy, "session" → "game"). **Changed by the owner 2026-11-26 (Gate 3, review.md #1):** the 403 page links to home ("Back to home"), because the 403 answer carries no public code; no new field or route. The public page link stays on the board's game strip.
  - `401` → go to `/login?next=/host/games/<id>`.
  - `404` → existing "page not found" empty state.
  - `503` / other → `ErrorBanner` with retry.
  - Success but no Ready players → note "No players ready yet."; 1–3 Ready (doubles) or 1 Ready (singles) → "Fewer than four players ready." / "Fewer than two players ready." (§16 empty states).
- Layout: phone (320–767px) — game strip, horizontally scrollable court cards (carousel = CSS scroll-snap, no library), then status sections, Suggest button sticky at the bottom. Desktop (768px+) — max width 1200px, courts in 2–4 columns on the left, Ready pool beside them, other states below (§16).

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|
| `hostBoard.title` | Host board | §6 screen name | yes (2026-11-25) |
| `hostBoard.suggest` | Suggest matches | §6 verbatim | yes (2026-11-25) |
| `hostBoard.suggestSoon` | Coming soon | existing `common.comingSoon` | yes (reused) |
| `hostBoard.refresh` | Refresh | NEW | yes (2026-11-25) |
| `hostBoard.court` | Court {n} | §3 "Court 2" | yes (2026-11-25) |
| `hostBoard.courtFree` | Free | NEW | yes (2026-11-25) |
| `hostBoard.teamA` / `teamB` | Team A / Team B | §6 "A/B teams" | yes (2026-11-25) |
| `hostBoard.statePending` | Pending | §3 state name | yes (2026-11-25) |
| `hostBoard.stateReady` | Ready | §3 | yes (2026-11-25) |
| `hostBoard.stateAssigned` | Assigned | §3 | yes (2026-11-25) |
| `hostBoard.statePlaying` | Playing | §3 | yes (2026-11-25) |
| `hostBoard.stateResting` | Resting | §3 | yes (2026-11-25) |
| `hostBoard.stateLeft` | Left | §3 | yes (2026-11-25) |
| `hostBoard.matchPlanned` | Next up | NEW | yes (2026-11-25) |
| `hostBoard.matchActive` | Playing | §3 | yes (2026-11-25) |
| `hostBoard.level` | Level {n} | NEW | yes (2026-11-25) |
| `hostBoard.levelUnknown` | Unknown / provisional | §13 verbatim | yes (2026-11-25) |
| `hostBoard.provisional` | Provisional | §3 "visibly provisional" | yes (2026-11-25) |
| `hostBoard.waiting` | Waiting {n} min | NEW (OD-14 minutes only) | yes (2026-11-25) |
| `hostBoard.setsPlayed` | {n} of {min} sets | NEW (OD-19 progress) | yes (2026-11-25) |
| `hostBoard.noReady` | No players ready yet. | §16 "No ready players" | yes (2026-11-25) |
| `hostBoard.fewerThanFour` | Fewer than four players ready. | §16 "fewer than four" | yes (2026-11-25) |
| `hostBoard.fewerThanTwo` | Fewer than two players ready. | NEW (singles, OD-20) | yes (2026-11-25) |
| `hostBoard.forbidden` | You don't manage this game. | §16 verbatim, "session" → "game" (OD-22) | yes (2026-11-25) |
| `hostBoard.toPublicPage` | Open the public page | NEW | yes (2026-11-25) |
| `home.hostedTitle` | Games you host | NEW (OD-04 = a) | yes (2026-11-25) |
| `home.hostedEmpty` | You are not hosting any games yet. | NEW (OD-04 = a) | yes (2026-11-25) |
| `home.manage` | Manage | §6 "separate `Manage` entry" | yes (2026-11-25) |

## 8. Security and privacy checklist

- [x] Identity derived from the account session only (gateway), carried to the service only by the signed token; path `gameId` names the target, never the caller.
- [x] Browser cannot forge the token: `HeaderSanitizer` already strips every `X-Internal-*` header; the HMAC secret is only in the server environment; services publish no host port.
- [x] Service-level ownership check `AccessPolicy.requireHost` on the board route (and on every later host route).
- [x] `HostBoardResponse` used only by `HostBoardController`; no `userId`, `playerId`, email, token or answers in it.
- [x] Read only → no CSRF change; no CORS change.
- [x] `no-store` on both host answers. Logs: no names, levels, tokens; the request log line has method, path, status, ms only (existing rule).
- [x] Token lifetime 30 s, so a token seen in a service log or a captured request is useless soon after.

## 9. Test plan

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Given a game with 1 PENDING guest (band null), 6 READY members, 1 RESTING, 1 LEFT and an ACTIVE doubles match on court 2 / when the host reads the board / then 200, 9 players with correct state, band, kind, bandStatus, matchesPlayed; court 1 `match: null`, court 2 lists teams A/B with 2 + 2 attendance ids | playmatch IT (player-service stubbed) |
| TC-02 | AC-01 | Singles game with an ACTIVE match → teams of one each | playmatch IT |
| TC-03 | AC-01 | Players are ordered by state group, then `matchesPlayed`, `availableSince`, id | playmatch IT |
| TC-04 | AC-02 | Account that is not host → 403 `FORBIDDEN`; no player data in body | playmatch `AccessMatrixIT` |
| TC-05 | AC-02 | Unknown game id → 404; malformed id → 404 | playmatch IT |
| TC-06 | AC-02 | No identity token → 401; expired token → 401; token signed with another secret → 401; tampered claims → 401 | playmatch IT + `IdentityTokenTest` |
| TC-07 | AC-02 | Gateway: anonymous → 401; guest cookie only → 401; signed-in account → forwarded with exactly one `X-Internal-Identity` header whose claims carry the session's userId; a browser-sent `X-Internal-Identity` is removed and replaced | gateway `GatewayAccessMatrixIT` + stub |
| TC-08 | AC-03 | `HostBoardResponse` keys = documented set exactly; no `userId`/`playerId`/email/token/answers anywhere in the JSON | `JsonAllowlist` (host set) |
| TC-09 | AC-03 | `HostBoardResponse` is referenced only by `HostBoardController` (grep check in review) | review |
| TC-10 | AC-06 | player-service down / times out → 503, no partial board | playmatch IT (stub returns 500 / no answer) |
| TC-11 | AC-06 | `POST /internal/v1/players/levels`: known + unknown ids → only known returned with kind, band, bandStatus; empty list / > 200 ids → 400 | player IT |
| TC-12 | AC-07 | `GET /host/games`: caller hosts 2 games, another account hosts 1 → caller sees exactly their 2, newest first; account hosting none → `[]` | playmatch IT |
| TC-13 | AC-01 | `IdentityToken` round trip; `exp` boundary with fixed `Clock`; version prefix other than `v1` refused | unit |
| TC-14 | — | Schema boundary test still passes for playmatch and player (no cross-schema SQL) | existing IT |
| TC-15 | AC-04 | Page states: loading, success, no Ready, fewer than four, 403 page with home link (owner 2026-11-26), 401 → login, error with retry | visual (owner) |
| TC-16 | AC-05 | Layout at 320, 375, 768, 1280px: carousel above lists on phone; courts beside Ready pool on desktop; Suggest button sticky and disabled | visual (owner) |
| TC-17 | AC-01 | Live: seed host logs in, opens the seeded game's board through the gateway → 8 READY, 2 free courts; `player1` → 403; anonymous `curl` → 401 | live (Docker) |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| OD-04 | How does the host reach the board? (§13 has no "my games" route) | (a) new `GET /api/v1/host/games` + "Games you host" on the home page when signed in · (b) public page shows a "Manage" link when the caller is the host (adds a field to the public answer) · (c) URL only (bookmark / seed log) | **(a)**: host-only data, public answer stays unchanged; matches §6 "separate Manage entry" |
| OD-14 | Wait indicator format | minutes only · highlight after N minutes | **Minutes only** (MVP; threshold is not in the blueprint) |
| D-1 | Identity token format and secret | (a) own HMAC-SHA256 token with JDK crypto, secret `INTERNAL_TOKEN_SECRET` in `.env` · (b) JWT via a library (new dependency) · (c) plain `X-Internal-User-Id` header, trusting the private network | **(a)**: no new dependency, a forged header still fails without the secret, same claims can move to JWT later |
| D-2 | Is the identity token built in this feature or as its own feature first? | (a) inside Host board (first consumer, Account login D-4) · (b) new small feature "Identity token" before Host board | **(a)**: one consumer proves the format; keeps the sprint at three features |
| D-3 | Guest cookie only (no account) on a host route | (a) 401 "sign in" — host routes need an account, same as Join (`ACCOUNT`) · (b) 403 — gateway resolves the guest cookie first | **(a)**: no extra call to auth-service; consistent with member join; a guest has no account to be host with |
| D-4 | How does the board stay fresh (new guests check in while the host watches)? | (a) "Refresh" button only · (b) poll every 15 s while visible (like the player page, §16) | **(a)** now; polling can come with Match suggestions if the pilot needs it |
| D-5 | "Suggest matches" before Match suggestions exists | (a) show it disabled with "Coming soon" · (b) hide it | **(a)**: same as the Join button before guest check-in (OD-13) |
| D-6 | Wait minutes from server or phone clock | (a) `asOf` server time in the answer · (b) phone clock | **(a)**: one extra field, no skew between phones |
| D-7 | Existing internal routes from Guest check-in and My attendance still take the caller id in the query/body (accepted debt) | (a) leave as is; switch them to the token in a later cleanup · (b) switch them now | **(a)**: keeps this feature focused; record as later cleanup |
| — | Assumption: the `F05` "members only" rule is unchanged: a host who wants to play joins as a member (OD-11) | — | — |

## 11. Out of scope

- Approve / rest / leave buttons → Attendance management (F07)
- Suggest, review, swap, confirm → Match suggestions, Match confirm (F09, F10)
- Start / finish / cancel → Match lifecycle (F11)
- Changing a level from the board → Host skill assessment (F14)
- Create game form → Create game (F08); closing a game → L-10
- Guest identity token kind and switching old internal routes to the token → later cleanup (D-7)
- Polling / real-time board → D-4; later real-time court map L-14
