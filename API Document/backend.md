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
Right now only `GET /actuator/health`, `GET /api/v1/auth/csrf`, `GET /api/v1/public/games/{publicCode}`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` and `GET /api/v1/me` are reachable; every other route is denied (anonymous → `401 UNAUTHENTICATED`, so unknown routes are not revealed).

| Method and route | Caller | Contract (§13) | Status |
|---|---|---|---|
| `GET /auth/csrf` | Anyone | Public CSRF bootstrap for browser writes (§0). Response `{"headerName":"X-CSRF-TOKEN","token":"…"}`. | Implemented (API foundation, 2026-11-17) |
| `GET /public/games/{publicCode}` | Anyone | Public title, venue, localizable times, court count, status; no names or match IDs. Also singles/doubles, minimum sets, price per person (OD-18…OD-20). | Implemented (Public game page, 2026-11-18) |
| `POST /auth/login` | Pilot-provisioned account | Cookie-backed account session. Public signup is not available. | Implemented (Account login, 2026-11-18) |
| `POST /auth/logout` | Signed-in account | Ends the account session. | Implemented (Account login, 2026-11-18) |
| `GET /me` | Signed-in account | Returns account identity. | Implemented (Account login, 2026-11-18) |
| `POST /games` | Signed-in account | Create a game; creator becomes host. | Not started |
| `POST /public/games/{publicCode}/guests` | Visitor | Submit name/assessment; create guest `PENDING`, set private guest cookie. | Not started |
| `POST /games/{gameId}/join` | Signed-in player | Join once with member profile; return own attendance. | Not started |
| `GET /games/{gameId}/me` | Member or scoped guest | Own attendance state, optional own `matchNo`, `courtNo`, `matchState`; no other players. | Not started |
| `GET /host/games/{gameId}/board` | This game's host | Full attendance list, private bands, current matches and courts. | Not started |
| `PATCH /host/games/{gameId}/attendance/{attendanceId}` | Host | Approve guest, set Ready/Resting/Left with state validation. | Not started |
| `POST /host/games/{gameId}/suggestions` | Host | Compute one or more proposed courts; read-only, no match persistence. | Not started (pure matcher exists, see below) |
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
| `POST /auth/logout` | signed-in account, CSRF token required | no body | `204`, session destroyed, header `Clear-Site-Data: "cache", "cookies"` |
| `GET /me` | signed-in account | — | `200 {"userId","email"}`; `401` when nobody is signed in |

Login errors: `400 VALIDATION_FAILED` (bad body, password over 72 bytes, not JSON); `401 UNAUTHENTICATED` (wrong password **or** unknown email, identical body); `403 CSRF_TOKEN_INVALID`; `429 TOO_MANY_REQUESTS` + `Retry-After` (seconds); `503 SERVICE_UNAVAILABLE` (auth-service down).

Throttling: counted per email (trimmed, case-insensitive, also for emails that do not exist). 5 failures → locked 60 s, doubling with each further failure up to 15 min; attempts older than 1 hour are forgotten (longer than the longest lock); a success clears the count. While locked even the right password gets 429. Counters are in the gateway's memory.

Internal (never reachable through the gateway): `POST /internal/v1/auth/verify` on auth-service, body `{"email","password"}`, answers `200 {"userId","email"}` or `401`.

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
