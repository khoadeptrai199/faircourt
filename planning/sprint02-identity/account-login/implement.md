# F03 implementation — Account login, logout, `GET /me`

Design: [design.md](design.md), approved by the owner 2026-11-18 ("tôi duyệt như bạn đề xuất"). Security hardening per OWASP added the same day on the owner's instruction (design section 12). Branch `feature/account-login` (created from `feature/public-game-page` after the Look and feel commit).

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01…AC-03 | auth-service: `POST /internal/v1/auth/verify` (`AccountVerifyController`, `AccountVerifyService`, request/response records), security rule, dummy-hash rule | `DONE` |
| T02 | AC-01…AC-05 | gateway: `AccountController` (login, logout, `/me`), `AuthClient`, `RouteAccess.ACCOUNT`, route table lines, security config (account rule, session context, absolute timeout), `Clock` bean | `DONE` |
| T03 | OWASP | `LoginThrottle` (429 + `Retry-After`), `ErrorCode.TOO_MANY_REQUESTS`, `AbsoluteSessionTimeoutFilter`, session cookie `fc_sid`, `no-store`, `Clear-Site-Data` | `DONE` |
| T04 | AC-01…AC-07 | Tests: `AccountVerifyIT`, `AuthServiceAccessIT` row, `AccountLoginIT`, `AuthServiceDownIT`, `LoginThrottleTest`, `AbsoluteSessionTimeoutFilterTest`, `GatewayAccessMatrixIT` row | `DONE` |
| T05 | AC-06 | Frontend: `apiPost` + in-memory CSRF token, `api/account.ts`, `useMutation`, `useAccount`, `TextField`, `safeNextPath`, `LoginPage`, home header (Host login link / email + Sign out), route `/login`, copy | `DONE` |
| T06 | — | Live check on the Docker stack (curl) | `DONE` |
| T07 | AC-06 | Visual check of `/login` and the home header at 320 / 375 / 1280px | `NOT VERIFIED` (owner) |
| T08 | — | Security review, fixes, pipeline, docs | `DONE` (see review.md) |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/auth-service/.../account/{AccountVerifyController,AccountVerifyService,AccountVerifyRequest,AccountIdentityResponse}.java` | added | Internal verify route |
| `backend/services/auth-service/.../security/SecurityConfig.java` | changed | Opens only `POST /internal/v1/auth/verify` |
| `backend/services/gateway/.../account/{AccountController,AccountResponse,LoginRequest,AuthClient,LoginThrottle,AbsoluteSessionTimeoutFilter}.java` | added | Login, logout, me; call to auth-service; throttle; absolute timeout |
| `backend/services/gateway/.../common/ClockConfig.java` | added | Injected `Clock` (conventions: time) |
| `backend/services/gateway/.../routing/{RouteAccess,GatewayRouteTable}.java`, `.../security/GatewaySecurityConfig.java` | changed | `ACCOUNT` access kind, 3 routes, session context repository, timeout filter |
| `backend/services/gateway/src/main/resources/application.yaml` | changed | `faircourt.services.auth-url`, `faircourt.account.absolute-timeout: 8h`, session idle timeout 30 m, cookie name `fc_sid` (`SESSION_COOKIE_NAME`) |
| `backend/libs/common-web/.../{ErrorCode,ApiException,GlobalExceptionHandler}.java` | changed | `TOO_MANY_REQUESTS` (429), `Retry-After` support |
| Gateway and auth-service tests (see tasks) | added / changed | `StubService` now also stands in for auth-service's verify route; `CsrfBootstrapIT` expects `fc_sid` |
| `frontend/src/api/{client,account,types}.ts`, `frontend/src/hooks/{useMutation,useAccount}.ts`, `frontend/src/lib/redirect.ts`, `frontend/src/components/TextField.tsx`, `frontend/src/features/login/LoginPage.tsx`, `frontend/src/features/home/HomePage.tsx`, `frontend/src/routes/AppRoutes.tsx`, `frontend/src/copy/en.ts` | added / changed | Login page, account menu |
| `API Document/{backend,frontend}.md`, `planning/...`, `.claude/rules/privacy-auth.md`, `CLAUDE.md` | changed | Documentation |

Deleted files: none. No new dependency (`pom.xml` and `package.json` unchanged). No migration.

## Commands executed

WSL Ubuntu 24.04, Docker Engine 29.5.3, 2026-11-18.

| Command | Result |
|---|---|
| `docker compose run --rm --build backend-format` | OK |
| `docker compose --profile test run --rm --build backend-test mvn -B clean verify` (1st, after a test-compile fix: `OptionalLongAssert` has no `hasValueSatisfying`) | `BUILD SUCCESS`; gateway 44 tests, auth-service 14 (`AccountLoginIT` 19, `AuthServiceDownIT` 1, `LoginThrottleTest` 7, `AbsoluteSessionTimeoutFilterTest` 3, `GatewayAccessMatrixIT` 8, `AccountVerifyIT` 7, `AuthServiceAccessIT` 3) |
| `docker compose run --rm --no-deps frontend sh -c "npm run format && npm run format:check && npm run build"` | OK, `tsc` clean |
| `docker compose up -d --build gateway auth-service`, then `curl` against `http://localhost:9090/api/v1` | see evidence |
| `privacy-reviewer` + `scope-guardian` (read-only agents) | findings in review.md; 7 fixed, 1 recorded as L-26, others need owner approval |
| Fixes: attempt counted before the check, 4 KB login body cap, CSRF token regenerated at login, redirect filter fixed, blank-password mapping, window 1 h; `mvn -B clean verify` again (a test of mine had a wrong expectation and exposed that a maximum lock reset the count: fixed) | `BUILD SUCCESS`, **142 tests**, 0 failures |
| `npm run format`, `format:check`, `npm run build` | OK |
| Node script with 15 redirect attack strings (temporary file, deleted) | all go to `/` except `/%5cevil.com` which stays a path on this site |
| Live on the Docker stack after the fixes: oversized body; CSRF token before/after login; 50 parallel wrong guesses at one email | 400; token changed, old token logout 403, new token 204; exactly 5 × 401 and 45 × 429 |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `AccountLoginIT`, `AccountVerifyIT`, live `curl` | `PASS` | Seeded host `host@faircourt.local`: login 200 `{"userId":"66c17b8b-…","email":"host@faircourt.local"}`; `Set-Cookie: fc_sid=…; Path=/; HttpOnly; SameSite=Lax` (no `Secure`: `COOKIE_SECURE=false` locally); `/me` returns the same; keys only `userId`, `email` |
| AC-02 | `AccountVerifyIT`, `AccountLoginIT`, live | `PASS` | Wrong password and unknown email: identical 401 body (only `requestId` differs); a hash is matched for unknown emails (dummy hash; timing test is a rough check) |
| AC-03 | `AccountLoginIT`, live | `PASS` | After logout `/me` → 401; logout 204 with `Clear-Site-Data: "cache", "cookies"` and `Cache-Control: no-store` |
| AC-04 | `AccountLoginIT`, live | `PASS` | Login and logout without token → 403 `CSRF_TOKEN_INVALID`, auth-service not called; form post → refused |
| AC-05 | `AccountLoginIT` | `PASS` | Session id differs before and after login; an old pre-login id from another client gets 401 |
| AC-06 | visual | `NOT VERIFIED` | Frontend builds; page not looked at in a browser |
| AC-07 | `GatewayAccessMatrixIT`, `AccountLoginIT`, `AuthServiceAccessIT` | `PASS` | anonymous `/me` 401; anonymous logout 401; `/internal/v1/auth/verify` through the gateway 401/403 and never forwarded; in auth-service only `POST` on that exact path is open |
| Throttle | `LoginThrottleTest`, `AccountLoginIT`, live | `PASS` | Live: attempts 1–5 → 401, 6th and 7th → 429 with `Retry-After: 60`; the real host account after 5 wrong guesses got 429 even with the right password (the demo host was locked for 60 s by this test) |
| No secrets in logs | live `docker compose logs` | `PASS` | no password, hash or tried email in gateway/auth-service logs; the only match is the existing local seed banner that prints the demo host email (not the password) |

Not verified: HTTPS behaviour (`Secure` flag, `__Host-` cookie) — only plain HTTP was run; a real browser; the Mac; load or timing attacks beyond the rough timing test.

## Deviations from the design (all from the owner's security instruction of 2026-11-18, listed in design §12)

- D-3 changed: idle timeout **30 min** (was proposed 8 h) plus an **absolute 8 h**. A host idle for 30 minutes must sign in again.
- Added: login throttle (429), `TOO_MANY_REQUESTS`, `Retry-After`; cookie `fc_sid` and `SESSION_COOKIE_NAME`; `no-store`; `Clear-Site-Data` (drops every cookie of the site: today only the session cookie exists, check again when the guest cookie arrives); JSON-only login; password limit in **bytes** (72) instead of characters.
- Added files not listed in the design: `ClockConfig`, `AbsoluteSessionTimeoutFilter`, `TextField`, `lib/redirect.ts`.
- New copy `login.tooManyAttempts` is **pending owner approval**.
- Added after the security review: `RequestBodyLimitFilter` (4 KB login body), `LoginThrottle` counts the attempt before the check and keeps attempts for 1 hour, CSRF token regenerated at login, auth-service accepts a blank-looking password (it is just a wrong password).
- Not done: password spraying across many emails (L-26); HTTPS run; browser check; Origin/Referer check.
