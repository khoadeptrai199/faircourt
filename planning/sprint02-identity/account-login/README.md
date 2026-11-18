# F03 — Account login, logout, `GET /me`

| Field | Value |
|---|---|
| Sprint | S02 Identity |
| Status | `WAITING_APPROVAL` |
| Blueprint | §0 (demo login, cookies), §7 (access), §11 (host, regular player), §13 (`POST /auth/login`, `POST /auth/logout`, `GET /me`), §15 (auth baseline) |
| Depends on | F01 |
| Waiting for | Owner answers to D-1…D-6 in [design.md](design.md) (D-1 is OD-03) |
| Suggested branch | `feature/account-login` |
| Last updated | 2026-11-18 |
Docs: [design.md](design.md)

## Goal

Pilot-provisioned accounts (seeded host and players) sign in with email and password, get a server-side session cookie, can ask "who am I", and can sign out. The home page's "Host login" button becomes real.

## Scope

- In: login/logout/me endpoints; `UserDetailsService` over `app_user`; session cookie HttpOnly, SameSite=Lax, `Secure` from `COOKIE_SECURE`; session id changed on login; login page per OD-03; frontend client refetches the CSRF token after login/logout; `CallerIdentity.Account` resolution.
- Out: signup, password reset, remember-me, lockout/rate limiting (§15: rollout gate before public signup, L-11).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Seeded host logs in → 200; cookie flags HttpOnly, SameSite=Lax, Secure = `COOKIE_SECURE`; `GET /api/v1/me` returns the account identity | IT + `Set-Cookie` assertion |
| AC-02 | Wrong email or password → 401 `UNAUTHENTICATED` with the same message for both (no account enumeration) | IT |
| AC-03 | Logout → later `GET /me` → 401 | IT |
| AC-04 | Login/logout without CSRF token → 403 `CSRF_TOKEN_INVALID` | IT |
| AC-05 | Session id differs before and after login | IT |
| AC-06 | Login page: validation, saving, error states; after login goes where OD-03 says | visual |
| AC-07 | `AccessMatrixIT`: anonymous `GET /me` → 401; account → 200 | IT |

## Design notes

- `identity/AccountController`, `AccountPrincipal(userId, email)`, `AccountUserDetailsService` (reads `app_user` with `lower(email)`, matching `ux_user_email_ci`).
- JSON login through a controller that authenticates with the `AuthenticationManager` and stores the security context in the HTTP session explicitly (verify the exact Spring Security 7 API in the reference before coding).
- `GET /me` response fields to decide in design (candidates: `userId`, `email`; display name only if the account has a player profile). Removing the default "generated security password" log line is a side effect of providing a `UserDetailsService` (README known limitation).

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-03 | Login route and landing page | Open |
| D-2 | `GET /me` response fields | Proposed in design: userId + email |
| D-3 | Session timeout value | Proposed in design: 8 h |
| 2026-11-18 | Design written (internal route `POST /internal/v1/auth/verify`, gateway session, `/me` from the session, login page); waiting for the owner on D-1…D-6 | Claude |
