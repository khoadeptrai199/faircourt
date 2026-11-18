# Privacy, identity and authorization (blueprint §3, §11, §12, §13, §15, §16)

These rules apply to every layer. "Keep player data private and host mutations host-only, even if a shortcut would make the demo easier" (§0).

## Permission contract (§3)

| Action / information | Anyone | Joined player | Court host |
|---|---:|---:|---:|
| View public game page (time, venue, number of courts, basic status) | Yes | Yes | Yes |
| Check in as a player | Via join flow | Yes | Can assist |
| View **own** match number, court number and start status | No | Yes | Yes |
| View another player's assignment, private skill evidence or full ready pool | No | No | Yes |
| Generate suggestions; assign, swap, start or finish a match | No | No | **Only host** |

"Court host" = creator of **this** game, checked via `game.host_user_id` — never a global `ADMIN` flag (§11).

## Identity

- Never accept `hostUserId`, `playerId` or `attendanceId` from the client as proof of identity. Derive identity from the signed-in principal or the scoped guest credential (§13).
- A nickname or the shared public code never authenticates anyone (§3, §12).
- Members: server-side session cookie after `POST /api/v1/auth/login`. Public signup does not exist in MVP (§11, §13). Implemented in the gateway (Account login, 2026-11-18): cookie `fc_sid` (HttpOnly, SameSite=Lax, `Secure` from `COOKIE_SECURE`; `__Host-fc_sid` on HTTPS), idle timeout 30 min + absolute 8 h, session id changed at login, session destroyed at logout, login throttled per email (5 failures, then 60 s doubling to 15 min, HTTP 429), one answer for wrong password and unknown email, `Cache-Control: no-store`, JSON-only login, CSRF required, `next` redirect only to same-site paths. Standard followed: OWASP Authentication, Session Management and CSRF cheat sheets (design §12 of the feature).
- Guests (§12):
  - Generate 32 random bytes; send only in an **HttpOnly, SameSite=Lax** cookie.
  - Store only the **SHA-256** of the secret in `guest_access.token_hash`. The raw secret never goes into the DB or logs.
  - Credential is scoped to its recorded game + attendance and expires shortly after the game.
  - One active guest identity per browser per game; re-joining with a valid cookie returns the existing attendance.
  - React never reads or stores the secret (§16).
- Cookie `Secure` flag comes from `COOKIE_SECURE` (`false` on local plain HTTP, `true` on HTTPS) (§0).

## CSRF and CORS

- CSRF stays **enabled** for cookie-authenticated writes. Never disable it globally to make forms pass (§0).
- `GET /api/v1/auth/csrf` is a public CSRF bootstrap; the exact header name must be documented in `API Document/` and the feature's `implement.md` (§0; `README.md` stays empty by owner request 2026-11-17).
- No open CORS wildcard (§15). Frontend and API share one origin (Vite proxies `/api` locally) (§0, §12).

## Separate DTOs — never reuse across audiences (§7, §13, §16)

| Endpoint | May contain | Must NOT contain |
|---|---|---|
| `GET /public/games/{publicCode}` | title, venue, times, court count, status | names, bands, attendance/match IDs, roster, guest tokens |
| `GET /games/{id}/me` | own `attendanceState`, optional own `matchNo`, `courtNo`, `matchState` | opponents, partners, bands, other players, full board |
| `GET /host/games/{id}/board` | names, private bands, all attendance, matches, courts | — (host only; never returned to a player) |

The player page calls its own `/me` endpoint. It must **not** load the host board and hide fields in CSS (§16).

## Enforcement

- Spring Security route rules are the first gate; **service-level** ownership check `(principalUserId, gameId)` runs for **every** host mutation (§15).
- Status codes (§13): `401` missing/expired identity, `403` authenticated but no permission, `404` unknown or private resource not visible.
- Feedback: only a participant of that **finished** match, one answer per player/match (§13).
- Store only necessary personal data (§7).

## Required tests (§7, §17 checklist)

- Anonymous public response contains no names, bands, match IDs, guest credentials.
- Another account gets `403` on host routes.
- Member and guest can read their own assignment; neither can read another player's assignment or the host board.
- Anonymous request and a different signed-in player cannot read the guest's assignment or call host writes (§0 step 5).
