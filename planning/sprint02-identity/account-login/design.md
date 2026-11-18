# F03 design — Account login, logout, `GET /me`

Status: `WAITING_APPROVAL` (written 2026-11-18). Waits for the owner's answers to D-1…D-6 (section 10) and the copy table (section 7).

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
| `login.title` | Host login | existing home button label | |
| `login.email` | Email | NEW | |
| `login.password` | Password | NEW | |
| `login.submit` | Sign in | NEW | |
| `login.saving` | Signing in… | NEW | |
| `login.invalid` | Email or password is incorrect. | NEW (one message for both cases) | |
| `login.required` | Enter your email and password. | NEW | |
| `account.signOut` | Sign out | NEW | |
| `account.signedInAs` | Signed in as {email} | NEW | |

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
| D-3 | Session idle timeout | 8 h / 12 h / 24 h | 8 h: covers an evening game with margin; the host signs in again the next day |
| D-4 | Identity token for services (signed token the gateway adds to forwarded requests, system-design §5) | (a) build it with the first feature whose service route needs the caller (host board) · (b) build it now | (a): nothing in this feature uses it; it needs a new secret (`INTERNAL_TOKEN_SECRET`) and the exact format is better decided with a real consumer |
| D-5 | Password length rule | 1–72 chars (BCrypt reads at most 72 bytes) / other | 1–72 characters |
| D-6 | Copy table (section 7) | approve / edit | — |

## 11. Out of scope

- Signup, password reset, "remember me", account lockout, rate limiting → rollout gate before public signup (L-11, L-17 Keycloak)
- Identity token for services → first feature that needs the caller (D-4)
- Guest credential → Guest check-in
- Navigation bar → L-20
