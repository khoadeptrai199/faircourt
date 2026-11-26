# Backend API — FairCourt MVP

Source: [FairCourt_Project_Blueprint.md](../FairCourt_Project_Blueprint.md) §0 (API/auth), §3, §13.
This file only restates the blueprint; if they differ, the blueprint wins.
Naming (owner decision 2026-11-18): the blueprint's "session" (the play event) is called **game** here and in the code, so `/sessions/...` routes are `/games/...` and `sessionId` is `gameId`. A single contest is a **match** (code) or **set** (UI). "Session" below only means the login/HTTP session.

## Conventions (§13)

- Every route is prefixed with `/api/v1`.
- Private endpoints use UUIDs in the path; `publicCode` is the shareable random code.
- Timestamps are ISO-8601 with timezone.
- Identity comes from the signed-in account or the scoped guest cookie. `hostUserId`, `playerId` or `attendanceId` sent by the client is never proof of identity.
- CSRF stays enabled for cookie-authenticated writes; `GET /api/v1/auth/csrf` is the public bootstrap (§0). The browser app calls it once, then sends the returned `token` in the request header named by `headerName` on every write (`POST`, `PUT`, `PATCH`, `DELETE`). **Header name: `X-CSRF-TOKEN`** (observed in `CsrfBootstrapIT` and with `curl` through the Vite proxy, 2026-11-17). The token is kept in the gateway's HTTP session (`fc_sid`, HttpOnly, SameSite=Lax), so JavaScript never reads a cookie. A write without a valid token → `403 CSRF_TOKEN_INVALID`.
- Every response carries an `X-Request-Id` header created by the gateway (a client-sent one is replaced); the same ID is in error bodies and in every log line of the gateway and the service.
- The browser only talks to the gateway (port 9090, or through the Vite proxy on 9091). Routes under `/internal/**` exist only between services and are never reachable from outside.

## Endpoints

Status = what exists in this repository today (not what the blueprint promises).
Right now only `GET /actuator/health`, `GET /api/v1/auth/csrf`, `GET /api/v1/public/games/{publicCode}`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/me`, `POST /api/v1/auth/password/forgot`, `POST /api/v1/auth/password/reset`, `POST /api/v1/public/games/{publicCode}/guests`, `POST /api/v1/games/{gameId}/join`, `GET /api/v1/games/{gameId}/me`, `GET /api/v1/host/games/{gameId}/board`, `GET /api/v1/host/games`, `PATCH /api/v1/host/games/{gameId}/attendance/{attendanceId}`, `POST /api/v1/host/games/{gameId}/suggestions` and `POST /api/v1/games` are reachable; every other route is denied (anonymous → `401 UNAUTHENTICATED`, so unknown routes are not revealed).

| Method and route | Caller | Contract (§13) | Status |
|---|---|---|---|
| `GET /auth/csrf` | Anyone | Public CSRF bootstrap for browser writes (§0). Response `{"headerName":"X-CSRF-TOKEN","token":"…"}`. | Implemented (API foundation, 2026-11-17) |
| `GET /public/games/{publicCode}` | Anyone | Public title, venue, localizable times, court count, status; no names or match IDs. Also singles/doubles, minimum sets, price per person (OD-18…OD-20). | Implemented (Public game page, 2026-11-18) |
| `POST /auth/login` | Pilot-provisioned account | Cookie-backed account session. Public signup is not available. | Implemented (Account login, 2026-11-18) |
| `POST /auth/logout` | Signed-in account | Ends the account session. | Implemented (Account login, 2026-11-18) |
| `GET /me` | Signed-in account | Returns account identity. | Implemented (Account login, 2026-11-18) |
| `POST /auth/password/forgot` | Anyone | Asks for a one-time code by email (owner-approved addition, 2026-11-18). | Implemented (Password reset, 2026-11-18) |
| `POST /auth/password/reset` | Anyone | Code + new password (owner-approved addition, 2026-11-18). | Implemented (Password reset, 2026-11-18) |
| `POST /games` | Signed-in account | Create a game; creator becomes host. | Implemented (Create game, 2026-11-26) |
| `POST /public/games/{publicCode}/guests` | Visitor | Submit name/assessment; create guest `PENDING`, set private guest cookie. | Implemented, backend (Guest check-in, 2026-11-18) |
| `POST /games/{gameId}/join` | Signed-in player | Join once with member profile; return own attendance. | Implemented (Own attendance status, 2026-11-20; row updated 2026-11-25, details in that feature's design) |
| `GET /games/{gameId}/me` | Member or scoped guest | Own attendance state, optional own `matchNo`, `courtNo`, `matchState`; no other players. | Implemented for the attendance state (Own attendance status, 2026-11-20); `assignment` is always `null` until the own-assignment feature |
| `GET /host/games/{gameId}/board` | This game's host | Full attendance list, private bands, current matches and courts. | Implemented (Host board, 2026-11-25) |
| `GET /host/games` | Signed-in account | Games the caller hosts (owner-approved addition, OD-04, 2026-11-25). | Implemented (Host board, 2026-11-25) |
| `PATCH /host/games/{gameId}/attendance/{attendanceId}` | Host | Approve guest, set Ready/Resting/Left with state validation. | Implemented, Ready and Left only (Attendance management, 2026-11-26) |
| `POST /host/games/{gameId}/suggestions` | Host | Compute one or more proposed courts; read-only, no match persistence. | Implemented (Match suggestions, 2026-11-26) |
| `POST /host/games/{gameId}/matches` | Host | Confirm one proposed or manually edited four-player pairing as `PLANNED`. | Not started |
| `PATCH /host/matches/{matchId}/start` | Owning host | Planned → active; four attendance rows Assigned → Playing. | Not started |
| `PATCH /host/matches/{matchId}/finish` | Owning host | Active → finished; release players, set Ready and update wait/game counters. | Not started |
| `PATCH /host/matches/{matchId}/cancel` | Owning host | Planned/active → cancelled; release players and restore Ready. | Not started |
| `POST /games/{gameId}/me/feedback` | Participant | Body includes `matchId` and fit answer; only a participant of that finished match; one answer per player/match. | Not started |
| `POST /host/games/{gameId}/assessments` | Host | Record observation and update current private band. | Not started |

Outside `/api/v1`: `GET /actuator/health` — **implemented**, public, reports `UP` after Flyway and DB connection (§0).

"Host" always means the creator of **this** game (`game.host_user_id`), never a global admin flag (§11).

### `GET /public/games/{publicCode}` (implemented)

Anyone, GET only. `publicCode` is trimmed and upper-cased; anything that is not 1–20 characters `A–Z0–9`, or no game with that code → `404 NOT_FOUND`. A write on this path never reaches the service (gateway: `403 CSRF_TOKEN_INVALID` without a token, otherwise `401`). Service down → `503 SERVICE_UNAVAILABLE`.

```json
{
  "gameId": "db8e1f53-2930-3def-902c-04e9a57758c1",
  "title": "Friday Badminton",
  "venue": "Court House D7",
  "startsAt": "2026-11-17T11:00:00Z",
  "endsAt": "2026-11-17T14:00:00Z",
  "courtCount": 2,
  "state": "OPEN",
  "matchFormat": "DOUBLES",
  "minSets": 6,
  "pricePerPerson": {
    "male": { "minVnd": 75000, "maxVnd": 85000 },
    "female": { "minVnd": 65000, "maxVnd": 75000 }
  }
}
```

- These are the only fields (checked by an allowlist test). No names, bands, roster, match IDs or guest tokens.
- `gameId` is the opaque internal id later routes use (`/games/{gameId}/...`); it grants nothing (owner decision 2026-11-18).
- `state`: `OPEN` | `CLOSED` (closed = read-only). `matchFormat`: `SINGLES` | `DOUBLES`. `minSets`: 1–20.
- `pricePerPerson` is `null` when no price is set; `male` or `female` is `null` when that line is not set; when only one bound of a line is set it is used as both. Shown only; players pay the host outside the app.

### Account login: `POST /auth/login`, `POST /auth/logout`, `GET /me` (implemented, 2026-11-18)

Answered by the gateway; the signed-in state is the gateway's HTTP session. Cookie `fc_sid` (HttpOnly, SameSite=Lax, `Secure` when `COOKIE_SECURE=true`; on HTTPS name it `__Host-fc_sid` with `SESSION_COOKIE_NAME`). Idle timeout 30 minutes, absolute timeout 8 hours. The session id changes at login. All three answers carry `Cache-Control: no-store`.

| Route | Caller | Request | Response |
|---|---|---|---|
| `POST /auth/login` | anyone, CSRF token required | JSON only: `{"email","password"}`; email 1–254 characters, password 1–72 **bytes** | `200 {"userId","email"}` + session cookie |
| `POST /auth/logout` | signed-in account, CSRF token required | no body | `204`, session destroyed, header `Clear-Site-Data: "cache"` and only the account cookie `fc_sid` expired (since Guest check-in: the directive `"cookies"` would also delete the guest cookie) |
| `GET /me` | signed-in account | — | `200 {"userId","email"}`; `401` when nobody is signed in |

Login errors: `400 VALIDATION_FAILED` (bad body, password over 72 bytes, not JSON); `401 UNAUTHENTICATED` (wrong password **or** unknown email, identical body); `403 CSRF_TOKEN_INVALID`; `429 TOO_MANY_REQUESTS` + `Retry-After` (seconds); `503 SERVICE_UNAVAILABLE` (auth-service down).

Throttling: counted per email (trimmed, case-insensitive, also for emails that do not exist). 5 failures → locked 60 s, doubling with each further failure up to 15 min; attempts older than 1 hour are forgotten (longer than the longest lock); a success clears the count. While locked even the right password gets 429. Counters are in the gateway's memory.

Internal (never reachable through the gateway): `POST /internal/v1/auth/verify` on auth-service, body `{"email","password"}`, answers `200 {"userId","email"}` or `401`.

### Password reset: `POST /auth/password/forgot`, `POST /auth/password/reset` (implemented, 2026-11-18)

Anyone; the gateway forwards both to auth-service; CSRF token required; JSON only; request body capped at 4 KB. Nothing in any answer says whether an email has an account.

| Route | Request | Response |
|---|---|---|
| `POST /auth/password/forgot` | `{"email"}` (1–254 chars) | `202 {}` **always**. A mail with an 8-digit code is sent only if the account exists, at least 60 s passed since the last code and fewer than 3 codes were sent in the last hour; otherwise nothing is sent and the answer is the same |
| `POST /auth/password/reset` | `{"email","code","newPassword"}`; code exactly 8 digits; password 12–72 **bytes**, not the email, not a very common password | `204`; `401 UNAUTHENTICATED` (same body for wrong, expired, used, replaced and unknown-email); `400 VALIDATION_FAILED` (malformed code or refused password) |

Code: 8 random digits, valid 10 minutes, one use, 5 wrong tries then dead, a newer code cancels the older ones, stored only as a BCrypt hash. After a reset: every code of the account is cancelled, a "your password was changed" mail is sent, no automatic login, and every existing session of the account ends within about a minute (the gateway asks auth-service once per minute per session). The account is never locked by reset attempts.

Internal (never reachable through the gateway): `GET /internal/v1/auth/accounts/{userId}/session-check?since=<instant>` on auth-service → `{"valid":true|false}` (false when the password changed after `since` or the account does not exist).

Mail: sent by auth-service over SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`). Locally the Compose service `mailpit` catches every mail; read them at http://localhost:9094. Plain text, no links, never a password.

### Guest check-in: `POST /public/games/{publicCode}/guests` (backend implemented, 2026-11-18)

Anyone; answered by the gateway (it owns the cookie); CSRF token required; JSON only; request body capped at 4 KB.

| Item | Value |
|---|---|
| Request | `{"displayName","answers":{"serve","rally","movement","positioning"}}`; `displayName` trimmed, 1–80 chars, no control characters; the four answers are each 0–3, or all `null`, or `answers` is `null` / missing (unknown level) |
| `201` | `{"attendanceState":"PENDING","message":"Waiting for host approval"}` and `Set-Cookie` guest cookie |
| `200` | The same browser again (a valid guest cookie of this game): `{"attendanceState":"<state now>","message":…}`; `message` is only set for `PENDING`; no new rows, no new cookie |
| Errors | `400 VALIDATION_FAILED` (name, partial or out-of-range answers, body over 4 KB, not JSON); `403 CSRF_TOKEN_INVALID`; `404 NOT_FOUND` (unknown or malformed code); `409 STATE_CONFLICT` (game `CLOSED`); `503 SERVICE_UNAVAILABLE` (a service of the check-in does not answer; if auth-service failed after the entry was created, the entry is withdrawn) |
| Headers | `Cache-Control: no-store`. The answer holds no id, name or band |

Guest cookie: name `fc_guest` (`GUEST_COOKIE_NAME`; on HTTPS `__Host-fc_guest`), value = 32 random bytes base64url (43 characters), HttpOnly, SameSite=Lax, `Secure` from `COOKIE_SECURE`, Path=/, no Domain, Max-Age until the credential ends = `max(game end, now) + 12 h`. Only its SHA-256 is stored (`auth.guest_access.token_hash`). A cookie of another game, or an unknown, expired or revoked one, is ignored (the person checks in as a new guest and the cookie is replaced). The cookie never leaves the gateway: services receive the resolved attendance id as an explicit argument.

Internal (never reachable through the gateway):

| Service | Route | Called by | Request → response |
|---|---|---|---|
| playmatch | `POST /internal/v1/games/by-code/{publicCode}/guest-check-ins` | gateway | `{displayName, answers, existingAttendanceId\|null}` → `{attendanceState, created, credential:{token, expiresAt}\|null}`; 404 / 409 / 400 / 503 |
| auth | `POST /internal/v1/auth/guest-credentials` | playmatch | `{gameId, attendanceId, expiresAt}` → `201 {token, expiresAt}` |
| auth | `POST /internal/v1/auth/guest-credentials/resolve` | gateway | `{token}` → `200 {gameId, attendanceId}` or `401` (one answer for unknown, expired and revoked) |
| player | `POST /internal/v1/players/guests` | playmatch | `{displayName, gameId, answers\|null}` → `201 {playerId}`; `400` for partial or out-of-range answers |

Band mapping (blueprint §13): total of the four answers 0–3 → band 1, 4–6 → 2, 7–9 → 3, 10–12 → 4; all unknown → no band.

### Create game: `POST /games` (implemented, 2026-11-26)

Forwarded by the gateway to playmatch-service. Gateway rule: signed-in account (`HOST`) and CSRF header `X-CSRF-TOKEN`; playmatch requires an account identity token. The new game's host is always the caller; a host field in the body is ignored.

| Item | Value |
|---|---|
| Request | `{"title","venue","startsAt","endsAt","courtCount","matchFormat","minSets","pricePerPerson"}`. `title` 1–120 and `venue` 1–160 characters after trim; `startsAt` / `endsAt` ISO-8601 with offset, `endsAt > startsAt`, the game has not ended yet and lasts at most 12 hours (OD-26); `courtCount` 1–4; `matchFormat` `SINGLES` or `DOUBLES`; `minSets` 1–20; `pricePerPerson` optional: `{"male":{"minVnd","maxVnd"},"female":{…}}`, each line optional, whole VND 0–10,000,000, `minVnd ≤ maxVnd`, one bound alone is used as both |
| `201` | `{"gameId","publicCode"}`, `Location: /api/v1/host/games/{gameId}/board`, `Cache-Control: no-store`. The game starts `OPEN` |
| Errors | `401 UNAUTHENTICATED` (not signed in, guest cookie only); `403 CSRF_TOKEN_INVALID`; `400 VALIDATION_FAILED` (any invalid field; no per-field list); `500 INTERNAL_ERROR` (no free public code after 5 tries); `503 SERVICE_UNAVAILABLE` |

### Host attendance change: `PATCH /host/games/{gameId}/attendance/{attendanceId}` (implemented, 2026-11-26)

Forwarded by the gateway to playmatch-service. Gateway rule: signed-in account (`HOST`) and CSRF header `X-CSRF-TOKEN`; playmatch checks that the caller is this game's host (§15) and that the attendance belongs to this game. The caller is never taken from the path or body.

| Item | Value |
|---|---|
| Request | `{"state":"READY"}` (approve a pending guest) or `{"state":"LEFT"}` (reject a pending check-in, or mark a ready player as left). Any other or missing value → `400 VALIDATION_FAILED` |
| `200` | `{"attendanceId","state","availableSince"}`, `Cache-Control: no-store`; `availableSince` is never changed by this route. Same state as now → `200`, nothing written |
| Errors | `401 UNAUTHENTICATED` (not signed in, guest cookie only); `403 CSRF_TOKEN_INVALID`; `403 FORBIDDEN` (not this game's host); `404 NOT_FOUND` (unknown or malformed game or attendance id, attendance of another game); `400 VALIDATION_FAILED`; `409 STATE_CONFLICT` (transition not allowed, for example `LEFT` → `READY`; player in a planned or active match: "Cancel or finish this player's match first."; game `CLOSED`: "This game is closed."); `503 SERVICE_UNAVAILABLE` |

Allowed moves: `PENDING` → `READY` or `LEFT`; `READY` → `LEFT`. `LEFT` is final. There is no Rest action (owner 2026-11-26).

### Host suggestions: `POST /host/games/{gameId}/suggestions` (implemented, 2026-11-26)

Forwarded by the gateway to playmatch-service. Gateway rule: signed-in account (`HOST`) and CSRF header `X-CSRF-TOKEN` (POST); playmatch checks that the caller is this game's host. Read only: nothing is written. No request body (any body is ignored). Answer carries `Cache-Control: no-store`.

| Item | Value |
|---|---|
| `200` | `{"suggestions":[{"courtNo","teams":{"A":[attendanceId...],"B":[...]},"reasonCodes":[...],"warnings":[...]}],"noSuggestionReason":null\|"WAITING_FOR_PLAYERS"\|"NO_FREE_COURT"}`; ordered by `courtNo`; doubles 2 ids per team, singles 1 |
| `reasonCodes` | `SIMILAR_LEVEL`, `BALANCED_TEAMS` (doubles only), `LONG_WAIT` |
| `warnings` | `WIDE_SKILL_GAP`, `UNKNOWN_LEVEL` |
| Errors | `401 UNAUTHENTICATED`; `403 CSRF_TOKEN_INVALID`; `403 FORBIDDEN` (not this game's host); `404 NOT_FOUND` (unknown or malformed id); `409 STATE_CONFLICT` (game `CLOSED`); `503 SERVICE_UNAVAILABLE` (player-service down) |

Attendance ids only: no names, bands or player ids (the host page reads names from the board). Pool = `READY` players not in an unfinished match; free courts = courts without a `PLANNED` / `ACTIVE` match. Calls the existing internal `POST /internal/v1/players/levels` for bands.

### Host board: `GET /host/games/{gameId}/board`, `GET /host/games` (implemented, 2026-11-25)

Both forwarded by the gateway to playmatch-service. Gateway rule: a signed-in account (route kind `HOST`); the real host check runs in playmatch (`AccessPolicy`, §15). Read only, no CSRF token needed. Answers carry `Cache-Control: no-store`.

| Item | `GET /host/games/{gameId}/board` | `GET /host/games` |
|---|---|---|
| Caller | this game's host (`game.host_user_id`) | any signed-in account; answers only the caller's own games |
| `200` | `{"asOf","game":{"id","publicCode","title","venue","startsAt","endsAt","courtCount","state","matchFormat","minSets"},"players":[{"attendanceId","displayName","kind","state","band","bandStatus","checkedInAt","availableSince","matchesPlayed"}],"courts":[{"courtNo","match":null\|{"matchId","matchNo","state","teams":{"A":[attendanceId…],"B":[…]}}}]}` | `{"games":[{"id","publicCode","title","venue","startsAt","endsAt","courtCount","state"}]}`, newest start first, at most 50 |
| Order | `players` by state (Pending, Ready, Assigned, Playing, Resting, Left), then `matchesPlayed`, `availableSince`, `attendanceId` (the matcher's order, §14) | — |
| Errors | `401 UNAUTHENTICATED` (not signed in, also a guest cookie only), `404 NOT_FOUND` (unknown or malformed id), `403 FORBIDDEN` (signed in, not the host), `503 SERVICE_UNAVAILABLE` (player-service down; no partial board) | `401 UNAUTHENTICATED` |

`asOf` is the server time of the read (wait minutes are counted from it). `band` is 1–4 or `null` (shown as "Unknown / provisional"); `bandStatus` `SELF_REPORTED` / `OBSERVED` / `CONFIRMED`; `kind` `MEMBER` / `GUEST`. `courts` has exactly `courtCount` entries; `match` is the one `PLANNED` or `ACTIVE` match on that court. No user id, player id, email, token or questionnaire answer is returned. This answer is for the host only and is never reused for a player.

Identity between gateway and services (for developers): on `HOST` routes the gateway adds the header `X-Internal-Identity` = `v2.<base64url claims>.<base64url HMAC-SHA256>`, claims `{"kind":"ACCOUNT","userId","aud","method","path","iat","exp"}`, valid 30 s, signed with `INTERNAL_TOKEN_SECRET` (`.env`, at least 32 characters; the stack does not start without it). Token v2 (Attendance management, 2026-11-26) is bound to one request: `aud` = target service name, `method` and `path` = the forwarded request line (no query string). A service refuses any mismatch, and `v1` tokens, with `401`; the body is not covered. A browser-sent `X-Internal-*` header is always removed first. Services rebuild the caller only from this header; missing → anonymous, invalid or expired → `401`.

Internal (never reachable through the gateway):

| Service | Route | Called by | Request → response |
|---|---|---|---|
| player | `POST /internal/v1/players/levels` | playmatch (host board) | `{"playerIds":[…]}` (1–200) → `200 {"players":[{"playerId","kind","band","bandStatus"}]}`; unknown ids left out; `400` for an empty or too long list |

## Who may see what (§3)

| Action / information | Anyone | Joined player | Court host |
|---|:-:|:-:|:-:|
| Public game page (time, venue, courts, basic status) | Yes | Yes | Yes |
| Check in as a player | Via join flow | Yes | Can assist |
| Own match number, court number, start status | No | Yes | Yes |
| Another player's assignment, private skill evidence, full ready pool | No | No | Yes |
| Generate suggestions; assign, swap, start or finish a match | No | No | Only host |

Each audience gets its own DTO. The host board response is never reused for a player or public response (§13, §16).

## Examples (§13)

Guest check-in:

```http
POST /api/v1/public/games/FRI7Q2K9/guests
Content-Type: application/json

{"displayName":"Mai","answers":{"serve":2,"rally":1,"movement":1,"positioning":1}}
```

Response `201 Created`, guest cookie set:

```json
{"attendanceState":"PENDING","message":"Waiting for host approval"}
```

Provisional band from the four answers (0–3 each): total 0–3 → 1; 4–6 → 2; 7–9 → 3; 10–12 → 4. All four `null` → `current_band = null`, shown as **Unknown / provisional** to the host. Partial answers are rejected. This is a pilot heuristic, not a validated rating.

Host suggestions — `POST /api/v1/host/games/{id}/suggestions` response:

```json
{
  "suggestions": [{
    "courtNo": 2,
    "teams": {"A": ["attendance-uuid-1", "attendance-uuid-4"],
              "B": ["attendance-uuid-2", "attendance-uuid-3"]},
    "reasonCodes": ["SIMILAR_LEVEL", "BALANCED_TEAMS", "LONG_WAIT"],
    "warnings": []
  }]
}
```

Confirm a match — `POST /api/v1/host/games/{id}/matches` request:

```json
{
  "courtNo": 2,
  "teams": {"A": ["attendance-uuid-1", "attendance-uuid-4"],
            "B": ["attendance-uuid-2", "attendance-uuid-3"]}
}
```

Returns `201` with `matchId`, `matchNo`, `courtNo`, `state: "PLANNED"`. The server re-reads the four attendance rows and the court; if anyone is unavailable it returns `409 MATCH_CONFLICT`.

Own assignment — `GET /api/v1/games/{id}/me`:

```json
{"attendanceState":"ASSIGNED","assignment":{"matchNo":3,"courtNo":2,"matchState":"PLANNED"}}
```

`assignment` is `null` while waiting.

## Errors (§13)

```json
{"code":"MATCH_CONFLICT","message":"A player or court is no longer available.","requestId":"..."}
```

| Status | Meaning |
|---|---|
| `400` | Invalid payload |
| `401` | Missing or expired identity |
| `403` | Authenticated but no permission |
| `404` | Unknown, or private resource not visible |
| `409` | State conflict |
| `429` | Too many failed logins for one email |
| `503` | A backend service did not answer in time (microservices, owner decision 2026-11-17) |

Error codes (same body from the gateway and every service; messages are generic, never exception text):

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Invalid body, unreadable JSON, wrong parameter type |
| `UNAUTHENTICATED` | 401 | No identity; also any route that does not exist |
| `TOO_MANY_REQUESTS` | 429 | Too many failed logins for one email; has a `Retry-After` header |
| `GUEST_ACCESS_EXPIRED` | 401 | Guest cookie expired (Guest check-in) |
| `CSRF_TOKEN_INVALID` | 403 | Write without a valid CSRF token |
| `FORBIDDEN` | 403 | Identity known, permission missing |
| `NOT_FOUND` | 404 | Unknown resource |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method on an existing route |
| `MATCH_CONFLICT` | 409 | A player or court is no longer available |
| `STATE_CONFLICT` | 409 | Illegal state change; database constraint fallback |
| `INTERNAL_ERROR` | 500 | Anything unexpected |
| `SERVICE_UNAVAILABLE` | 503 | A service is down or slower than 2 s connect / 5 s read |

## Already in the code

- Service split: `gateway` + `auth-service` + `player-service` + `playmatch-service`, one database schema each, per-service Flyway `V1` and local demo seed. Flyway `V2` in each database service renames "session" to "game" (2026-11-18).
- Pure matcher `com.faircourt.matching.MatchSuggester` (library `backend/libs/matching`) with unit tests. It is not exposed over HTTP yet.
- API foundation: shared error body and codes, request ID, gateway route table (deny by default), CSRF bootstrap, `503` when a service does not answer. Services deny everything except health until a feature opens a route.
- Public game page: `GET /public/games/{publicCode}` (playmatch-service, routed by the gateway).
- Account login: `POST /auth/login`, `POST /auth/logout`, `GET /me` in the gateway; internal `POST /internal/v1/auth/verify` in auth-service; login throttle, idle and absolute session timeout.
- Guest check-in (backend): `POST /public/games/{publicCode}/guests` in the gateway, saga across playmatch, player and auth (4 internal routes), guest cookie `fc_guest`; logout no longer clears it.
- Host board: `GET /host/games/{gameId}/board` and `GET /host/games` (playmatch-service, forwarded by the gateway with the `X-Internal-Identity` token); `AccessPolicy` in playmatch; internal `POST /internal/v1/players/levels` in player-service (2026-11-25).
- Password reset: `POST /auth/password/forgot`, `POST /auth/password/reset` (forwarded to auth-service), internal session check, Flyway `V3` (table `password_reset`, column `password_changed_at`), mail through SMTP (local catcher `mailpit`).
