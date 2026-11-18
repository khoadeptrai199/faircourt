# F03 design — Account login, logout, `GET /me`

Status: `APPROVED` (2026-11-18, by owner: "tôi duyệt như bạn đề xuất" for D-1…D-6 incl. the internal route and the copy table). Security hardening added the same day on the owner's instruction to follow OWASP standards: see section 12 (it changes D-3 and adds items to the approved design).

## 1. Summary

A pilot account (seeded host or player) signs in with email and password. The **gateway** keeps the signed-in state in its own HTTP session (HttpOnly, SameSite=Lax cookie); **auth-service** only checks the password. After login the home page's disabled "Host login" button works, and the browser can ask "who am I" and sign out.

Reusable pieces added: gateway `RouteAccess.ACCOUNT` (route needs a signed-in account), a gateway client for auth-service (`RestClient`, 2 s / 5 s timeouts, AD-15), the login page, a small session hook in the frontend (`useAccount`), and `apiPost` in `api/client.ts` (adds the CSRF header).

## 2. References

- §0: seeded host `host@faircourt.local` / `FairCourtDemo!1`; cookie `Secure` from `COOKIE_SECURE`; CSRF stays on.
- §11, §13: `POST /auth/login`, `POST /auth/logout`, `GET /me` — "Cookie-backed account session; `GET /me` returns account identity. Public signup is not available."
- §15: "password hashes through Spring Security's password encoder; account sessions via HttpOnly, SameSite=Lax cookies … CSRF token on cookie-authenticated writes". Lockout / abuse controls are a rollout gate before public signup (L-11), not part of this feature.
- AD-14 (gateway owns the account session), AD-15 (internal routes, short timeouts, no retry of writes), `system-design.md` §5: "the gateway sends the credentials to `auth-service`, receives a `userId`, and stores it in its own HTTP session".

## 3. API contract

### Public routes (through the gateway)

| Route | Caller | Request | Response | Errors |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | anyone, CSRF token required | `{"email","password"}`; email 1–254 chars, password 1–72 chars | `200 {"userId","email"}`; sets the session cookie; the session id changes at login | 400 `VALIDATION_FAILED`; 401 `UNAUTHENTICATED` (same body for unknown email and wrong password); 403 `CSRF_TOKEN_INVALID`; 503 |
| `POST /api/v1/auth/logout` | signed-in account, CSRF token required | no body | `204`; session invalidated | 401 `UNAUTHENTICATED` when not signed in; 403 `CSRF_TOKEN_INVALID` |
| `GET /api/v1/me` | signed-in account | — | `200 {"userId","email"}` | 401 `UNAUTHENTICATED` |

Allowed response keys for `/me` and login: `userId`, `email` (the signed-in person's own data only; no password hash, no other account). Answered by the gateway from its session, so `/me` makes no call to auth-service (D-2).

### Internal route (auth-service, never routed by the gateway's public table) — needs approval (AD-15)

| Item | Value |
|---|---|
| Route | `POST /internal/v1/auth/verify` |
| Caller | the gateway only (no host port on services; Compose network only) |
| Request | `{"email","password"}` |
| Response | `200 {"userId","email"}` if the credentials are right; `401 UNAUTHENTICATED` otherwise |
| Side effects | none (read-only) |

## 4. Data and transactions

- Table read: `auth.app_user` (`id`, `email`, `password_hash`). No migration.
- Lookup uses `lower(email)` so it matches `ux_user_email_ci`.
- Password check: Spring Security `PasswordEncoder.matches`. When the email is unknown the code still runs a match against a fixed dummy hash, so a missing account and a wrong password take about the same time (no account enumeration by timing).
- No writes, so no transaction and no lock.

## 5. Domain rules

- One account = one email; no signup, no password change here.
- Session: created at login, id replaced at login (session fixation), invalidated at logout; idle timeout set by D-3.
- The CSRF token is tied to the session. Login and logout change it, so the frontend fetches a fresh token with `GET /auth/csrf` after each of them (risk table in ROADMAP).
- A signed-in account is not a host by itself: "host of this game" is checked later per game (`game.host_user_id`, §11), never by a global flag.

## 6. Backend components

| File | Service | Notes |
|---|---|---|
| `AccountVerifyController`, `AccountVerifyService`, `AccountVerifyRequest`, `AccountIdentityResponse` (`com.faircourt.auth.account`) | auth-service | Controller thin; service holds the dummy-hash rule |
| `SecurityConfig` | auth-service | Open `POST /internal/v1/auth/verify` (everything else stays denied) |
| `AuthClient` (`com.faircourt.gateway.account`) | gateway | `RestClient` to `faircourt.services.auth-url` (default `http://auth-service:8080`); maps 401 → 401, connect/read failure → 503 |
| `AccountController`, `LoginRequest`, `AccountResponse` | gateway | login, logout, `/me`; login calls `AuthClient`, then stores the security context in the HTTP session and changes the session id |
| `RouteAccess.ACCOUNT`, `GatewayRouteTable`, `GatewaySecurityConfig` | gateway | New access kind: signed-in account. Routes added to the table with `GatewayRoute.local(…)` |
| `HeaderSanitizer` | gateway | unchanged (still strips cookies and identity headers) |
| `PlaymatchServiceAccessIT`-style rows | auth-service, gateway | Section 9 |

Spring Security 7 details (how to save the context in the session and change the session id for a JSON login) are looked up in the official reference before coding; nothing is copied from older tutorials (ROADMAP risk table).

## 7. Frontend

### Files

| File | Purpose |
|---|---|
| `src/api/client.ts` | add `apiPost`; reads the CSRF header name and token from `GET /auth/csrf` and sends it on writes; fetches a new token after login/logout |
| `src/api/account.ts` | `login`, `logout`, `getMe` |
| `src/hooks/useAccount.ts` | one fetch of `/me` per page load (`useAsync`); 401 means "signed out", not an error |
| `src/hooks/useMutation.ts` | `idle / saving / success / conflict / error` (conventions) |
| `src/features/login/LoginPage.tsx` | the form; route `/login` (D-1) |
| `src/features/home/HomePage.tsx` | "Host login" becomes a link to `/login`; when signed in it shows the account email and "Sign out" |
| `src/routes/AppRoutes.tsx` | route `/login` |

### States

Login form: idle → saving (button disabled, label "Signing in…") → success (go to the page in `?next=`, else home) / error 401 (message under the form, password cleared, focus kept) / error 503 (`ErrorBanner` with Try again). Fields validated in place (empty email or password); the backend validates again.

### Layout

Same look as the public game page (Look and feel): centered card up to 480px on desktop, full width on phones, fields ≥ 44px high, labels above inputs, visible focus, the card uses the court-lines hero strip.

### Copy (all NEW, need owner approval)

| Key | Text | Source | Approved |
|---|---|---|---|
| `login.title` | Host login | existing home button label | yes (2026-11-18) |
| `login.email` | Email | NEW | yes (2026-11-18) |
| `login.password` | Password | NEW | yes (2026-11-18) |
| `login.submit` | Sign in | NEW | yes (2026-11-18) |
| `login.saving` | Signing in… | NEW | yes (2026-11-18) |
| `login.invalid` | Email or password is incorrect. | NEW (one message for both cases) | yes (2026-11-18) |
| `login.required` | Enter your email and password. | NEW | yes (2026-11-18) |
| `account.signOut` | Sign out | NEW | yes (2026-11-18) |
| `account.signedInAs` | Signed in as {email} | NEW | yes (2026-11-18) |
| `login.tooManyAttempts` | Too many attempts. Try again later. | NEW, added with the login throttle (section 12); same text as the API message for 429 | yes (2026-11-18) |

## 8. Security and privacy checklist

- [ ] Identity comes only from the gateway session, never from request body, path or other headers
- [ ] One 401 message for unknown email and wrong password; equal work in both cases
- [ ] Cookie HttpOnly, SameSite=Lax, `Secure` from `COOKIE_SECURE`; session id changed at login
- [ ] CSRF required for login and logout; no CORS change
- [ ] Password and hash never logged, never in an error body; the request log line has no body
- [ ] `/internal/v1/auth/verify` is not in the gateway route table (test: `GET`/`POST /api/v1/…` and `/internal/…` through the gateway never reach auth-service)
- [ ] The password is sent only gateway → auth-service on the private Compose network (no TLS there; same trust as the database link)

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Seeded host logs in → 200 `{userId,email}`; `Set-Cookie` has HttpOnly, SameSite=Lax; `/me` returns the same | gateway IT with a stub auth-service |
| TC-02 | AC-02 | Unknown email and wrong password → identical 401 body | gateway IT + auth-service IT |
| TC-03 | AC-03 | Logout → `/me` → 401 | gateway IT |
| TC-04 | AC-04 | Login and logout without token → 403 `CSRF_TOKEN_INVALID`; the stub is not called | gateway IT |
| TC-05 | AC-05 | Session id before ≠ after login | gateway IT |
| TC-06 | AC-07 | Access matrix: anonymous `/me` 401; account 200; anonymous logout 401; `/internal/v1/auth/verify` through the gateway 401 and never forwarded | `GatewayAccessMatrixIT` |
| TC-07 | AC-01, AC-02 | `verify` with the real PostgreSQL: right password 200, wrong 401, unknown 401, mixed-case email 200, response keys ⊆ `userId,email` (`JsonAllowlist`), no password/hash in body | `AccountVerifyIT` (auth-service) |
| TC-08 | AC-02 | Unknown email still runs the password match (dummy hash) | unit test |
| TC-09 | AC-06 | Login page at 320 / 375 / 1280px: empty fields, wrong password, success → home, 503 | visual (owner) |
| TC-10 | AC-04 | Auth-service down → login → 503 `SERVICE_UNAVAILABLE` | gateway IT |
| TC-11 | — | Pipeline | pipeline |

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | Login screen route and where the person lands afterwards (OD-03) | (a) page `/login?next=…`, back to `next` or home · (b) a dialog on the home page | (a): link-able, works with the browser Back button, reused by later "sign in to continue" cases |
| D-2 | `GET /me` fields and who answers | (a) `userId` + `email`, answered by the gateway from its session · (b) also ask auth-service each time | (a): no extra call; display name comes later with the player profile |
| D-3 | Session idle timeout | 8 h / 12 h / 24 h | 8 h: covers an evening game with margin; the host signs in again the next day | **Changed after approval, see section 12.**
| D-4 | Identity token for services (signed token the gateway adds to forwarded requests, system-design §5) | (a) build it with the first feature whose service route needs the caller (host board) · (b) build it now | (a): nothing in this feature uses it; it needs a new secret (`INTERNAL_TOKEN_SECRET`) and the exact format is better decided with a real consumer |
| D-5 | Password length rule | 1–72 chars (BCrypt reads at most 72 bytes) / other | 1–72 characters |
| D-6 | Copy table (section 7) | approve / edit | — |

## 11. Out of scope

- Signup, password reset, "remember me", account lockout, rate limiting → rollout gate before public signup (L-11, L-17 Keycloak)
- Identity token for services → first feature that needs the caller (D-4)
- Guest credential → Guest check-in
- Navigation bar → L-20

## 12. Security hardening added after approval (owner, 2026-11-18)

Owner instruction: "nhớ đảm bảo các security, tôi không muốn bị lổ hổng nào xuất hiện … nên tham khảo các chuẩn". Standards read on 2026-11-18: OWASP Cheat Sheet Series — Authentication, Session Management, Cross-Site Request Forgery Prevention. What the standards say and what this feature does:

| Standard (OWASP) | Done in this feature | Where |
|---|---|---|
| One generic message for wrong id, wrong password, unknown account; same work whether or not the account exists | One 401 body for all cases; a dummy BCrypt hash is matched when the email is unknown | `AccountVerifyService`, `AccountVerifyIT`, `AccountLoginIT` |
| Limit attempts, count per account (not per IP), lengthen the wait after each failure | **New:** per email; every attempt is counted *before* the password is checked (so parallel guesses cannot slip through: 50 parallel guesses let exactly 5 pass, live test); 5 attempts, then 60 s lock doubling to 15 min; attempts are forgotten after 1 hour (longer than the longest lock, so a maximum lock is not wiped); answer 429 + `Retry-After`; applies to unknown emails too; at most 10,000 emails tracked | `LoginThrottle`, `LoginThrottleTest` |
| Do not silently truncate passwords; support long passphrases | Password limit is **72 bytes** (BCrypt), longer is refused with 400, never cut. OWASP asks for ≥ 64 characters supported: 72 bytes covers 64 ASCII characters | gateway + auth-service |
| Compare hashes in constant time | `PasswordEncoder.matches` | `AccountVerifyService` |
| Log authentication failures and lockouts, no secrets | One WARN line per failure and per lock, with the request ID, no email, no password | `AccountController` |
| Session cookie: Secure, HttpOnly, SameSite | HttpOnly, SameSite=Lax, Secure from `COOKIE_SECURE` (plain HTTP locally; **must be `true` on HTTPS**) | `application.yaml` |
| Generic cookie name, `__Host-` prefix on HTTPS | Name `fc_sid` (not `JSESSIONID`); on HTTPS set `SESSION_COOKIE_NAME=__Host-fc_sid` | `application.yaml` |
| New session id after login | `changeSessionId` at login; test proves the old id is useless | `AccountLoginIT.anOldSessionIdIsUselessAfterLogin` |
| Idle timeout 15–30 min (low risk) and absolute timeout 4–8 h, both on the server | **Changed D-3:** idle **30 min** (was proposed 8 h), absolute **8 h** | `application.yaml`, `AbsoluteSessionTimeoutFilter` |
| Invalidate on the server at logout; clear client state | Session invalidated; `Clear-Site-Data: "cache", "cookies"` | `AccountController.logout` |
| `Cache-Control: no-store` on responses with session data | On login, `/me`, logout (and Spring Security's default headers `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`) | `AccountController`, live `curl` |
| CSRF token on state-changing requests; login CSRF; reject simple content types | CSRF required on login and logout; login accepts `application/json` only; session id changes at login | `AccountLoginIT` |
| Origin/Referer check as defence in depth | **Not done:** needs a trusted-origin setting and the Vite proxy changes the origin; SameSite=Lax + token + JSON-only already block the cases | — |
| Serve login only over TLS | Local pilot runs plain HTTP; **HTTPS with `COOKIE_SECURE=true` is a gate before any real deployment** (backlog L-01) | docs |
| MFA, CAPTCHA, blocked-password lists | Out of scope for the closed pilot (backlog L-11, L-17) | — |
| Unvalidated redirects | `next` after login accepted only as a same-site path; checked again after the browser normalises it (`/.//evil.com` is refused) | `frontend/src/lib/redirect.ts` (checked with 15 attack strings in Node) |
| Do not let anonymous routes read huge bodies | Login body capped at 4 KB, also for chunked bodies, answered 400 before parsing | `RequestBodyLimitFilter` |
| Regenerate the CSRF token at authentication | The pre-login CSRF token is removed at login; the old token is refused afterwards | `AccountController`, `AccountLoginIT` |

Other additions: `RouteAccess.ACCOUNT` and a `Clock` bean in the gateway; `ErrorCode.TOO_MANY_REQUESTS` (429) and `Retry-After` support in `ApiException` / `GlobalExceptionHandler` (shared library); frontend `apiPost` with the CSRF token kept only in memory, `useMutation`, `useAccount`, `TextField`.

Residual risks the owner should know (not fixed here):

- **Password spraying is not slowed down:** the lock is per email, so one common password tried against many different emails is never throttled (each try costs auth-service about 100 ms of BCrypt, so it is also a CPU denial-of-service route). A fix needs a per-source or global limit and a trusted way to know the client address behind the proxy: recorded as backlog L-26, needs an owner decision.
- **The lock can be used against the host:** someone who knows the host's email can keep that account locked with about one request every 15 minutes (the lock after the first maximum lock does not reset: attempts are remembered for an hour). OWASP names this trade-off; "forgot password" does not exist in the MVP. The host can wait, or the gateway can be restarted (the counters are in memory).
- The counters live in one gateway process: a restart clears them, and a second gateway instance would not share them. Fine for the pilot.
- The password travels gateway → auth-service in plain HTTP on the private Compose network (same trust as the database link). Anyone who can run a container on that network could call `POST /internal/v1/auth/verify` directly and is not throttled. Mitigation later: a signed service-to-service token (design D-4) and TLS.
- Sessions are in the gateway's memory: a gateway restart signs everyone out.
- No account lockout notification, no login history for the person.
