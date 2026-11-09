# Privacy, identity and authorization (blueprint §3, §11, §12, §13, §15, §16)

These rules apply to every layer. "Keep player data private and host mutations host-only, even if a shortcut would make the demo easier" (§0).

## Permission contract (§3)

| Action / information | Anyone | Joined player | Court host |
|---|---:|---:|---:|
| View public session page (time, venue, number of courts, basic status) | Yes | Yes | Yes |
| Check in as a player | Via join flow | Yes | Can assist |
| View **own** match number, court number and start status | No | Yes | Yes |
| View another player's assignment, private skill evidence or full ready pool | No | No | Yes |
| Generate suggestions; assign, swap, start or finish a match | No | No | **Only host** |

"Court host" = creator of **this** play session, checked via `play_session.host_user_id` — never a global `ADMIN` flag (§11).

## Identity

- Never accept `hostUserId`, `playerId` or `attendanceId` from the client as proof of identity. Derive identity from the signed-in principal or the scoped guest credential (§13).
- A nickname or the shared public code never authenticates anyone (§3, §12).
- Members: server-side session cookie after `POST /api/v1/auth/login`. Public signup does not exist in MVP (§11, §13).
- Guests (§12):
  - Generate 32 random bytes; send only in an **HttpOnly, SameSite=Lax** cookie.
  - Store only the **SHA-256** of the secret in `guest_access.token_hash`. The raw secret never goes into the DB or logs.
  - Credential is scoped to its recorded session + attendance and expires shortly after the session.
  - One active guest identity per browser per session; re-joining with a valid cookie returns the existing attendance.
  - React never reads or stores the secret (§16).
- Cookie `Secure` flag comes from `COOKIE_SECURE` (`false` on local plain HTTP, `true` on HTTPS) (§0).

## CSRF and CORS

- CSRF stays **enabled** for cookie-authenticated writes. Never disable it globally to make forms pass (§0).
- `GET /api/v1/auth/csrf` is a public CSRF bootstrap; the exact header name must be documented in `README.md` (§0).
- No open CORS wildcard (§15). Frontend and API share one origin (Vite proxies `/api` locally) (§0, §12).

## Separate DTOs — never reuse across audiences (§7, §13, §16)

| Endpoint | May contain | Must NOT contain |
|---|---|---|
| `GET /public/sessions/{publicCode}` | title, venue, times, court count, status | names, bands, attendance/match IDs, roster, guest tokens |
| `GET /sessions/{id}/me` | own `attendanceState`, optional own `matchNo`, `courtNo`, `matchState` | opponents, partners, bands, other players, full board |
| `GET /host/sessions/{id}/board` | names, private bands, all attendance, matches, courts | — (host only; never returned to a player) |

The player page calls its own `/me` endpoint. It must **not** load the host board and hide fields in CSS (§16).

## Enforcement

- Spring Security route rules are the first gate; **service-level** ownership check `(principalUserId, sessionId)` runs for **every** host mutation (§15).
- Status codes (§13): `401` missing/expired identity, `403` authenticated but no permission, `404` unknown or private resource not visible.
- Feedback: only a participant of that **finished** match, one answer per player/match (§13).
- Store only necessary personal data (§7).

## Required tests (§7, §17 checklist)

- Anonymous public response contains no names, bands, match IDs, guest credentials.
- Another account gets `403` on host routes.
- Member and guest can read their own assignment; neither can read another player's assignment or the host board.
- Anonymous request and a different signed-in player cannot read the guest's assignment or call host writes (§0 step 5).
