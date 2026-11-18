# F21 implementation — Password reset (forgot password, OTP by email)

Design: [design.md](design.md), approved by the owner 2026-11-18 ("đồng ý đề xuất" for D-1…D-9). Branch `feature/password-reset` (from `feature/account-login`). Written and tested in the Sonnet 5.5 session (implement phase); review phase should run under Opus 5.5 (owner rule 2026-11-18).

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-02, AC-06 | Migration `V3__password_reset.sql` (table `password_reset`, column `app_user.password_changed_at`) | `DONE` |
| T02 | AC-01…AC-04, AC-06, AC-07 | auth-service: `PasswordResetService`, `PasswordResetController`, request records, `PasswordRules`, `ResetMailSender`, `ClockConfig`, security rule | `DONE` |
| T03 | AC-05 | auth-service `SessionCheckController` (internal) + gateway `AuthClient.sessionCheck` + `SessionRevalidationFilter` | `DONE` |
| T04 | AC-01 | Gateway: two forwarded public POST routes, body cap on the two paths | `DONE` |
| T05 | — | `spring-boot-starter-mail`, mail settings in `application.yaml`, Compose service `mailpit` (pinned `axllent/mailpit:v1.33.1`), `.env.example` | `DONE` |
| T06 | AC-01…AC-07 | Tests: `PasswordResetIT` (14), `PasswordRulesTest` (6), `TestMailConfig`, `AuthServiceAccessIT` row, gateway `AccountLoginIT` (+3), `GatewayAccessMatrixIT` (+2) | `DONE` |
| T07 | AC-07, AC-08 | Frontend: `AuthPageShell`, `ForgotPasswordPage`, `ResetPasswordPage`, `api/passwordReset.ts`, `lib/passwordRules.ts`, copy, routes, "Forgot password?" link | `DONE` |
| T08 | — | Live check on the Docker stack (script, see evidence) | `DONE` |
| T09 | AC-08 | Visual check of both pages at 320 / 375 / 1280px | `NOT VERIFIED` (owner) |
| T10 | — | Reviews and fixes, pipeline, docs | see review.md |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/services/auth-service/src/main/resources/db/migration/V3__password_reset.sql` | added | Table and column |
| `backend/services/auth-service/.../passwordreset/{PasswordResetService,PasswordResetController,ForgotPasswordRequest,ResetPasswordRequest,PasswordRules,ResetMailSender}.java` | added | The feature |
| `backend/services/auth-service/.../account/SessionCheckController.java`, `.../common/ClockConfig.java` | added | Internal session check; injected clock |
| `backend/services/auth-service/.../security/SecurityConfig.java`, `src/main/resources/application.yaml`, `pom.xml` | changed | Open the new routes; mail settings; `spring-boot-starter-mail` |
| `backend/services/gateway/.../account/{SessionRevalidationFilter,AuthClient,RequestBodyLimitFilter}.java`, `.../routing/{GatewayRouteTable,GatewayRoutes}.java`, `.../security/GatewaySecurityConfig.java`, `src/main/resources/application.yaml` | added / changed | Session re-check, forwarded routes, body cap |
| `compose.yaml`, `.env.example` | changed | `mailpit` service, `MAIL_*` variables, `MAILPIT_PORT=9094` |
| Backend tests (see tasks) | added / changed | `StubService` answers the session check; `AuthSchemaIT` lists `password_reset` |
| `frontend/src/{api/passwordReset.ts,lib/passwordRules.ts,components/AuthPageShell.tsx,features/password-reset/*,features/login/LoginPage.tsx,routes/AppRoutes.tsx,copy/en.ts}` | added / changed | Pages |
| `API Document/{backend,frontend}.md`, `planning/...`, `CLAUDE.md` | changed | Documentation |

Deleted files: none. No applied migration edited (V1, V2 untouched).

## Commands executed

WSL Ubuntu 24.04, Docker Engine 29.5.3, 2026-11-18.

| Command | Result |
|---|---|
| `curl https://hub.docker.com/v2/repositories/axllent/mailpit/tags`; `docker buildx imagetools inspect axllent/mailpit:v1.33.1` | v1.33.1 (2026-11-07) exists; platforms `linux/amd64`, `linux/arm64`, `linux/386`. **Run only on the Windows/WSL (amd64) machine; the Mac was not run** |
| `docker compose config -q` | OK |
| `docker compose run --rm --build backend-format`; `docker compose --profile test run --rm --build backend-test mvn -B clean verify` (2 runs; the 1st failed: mail health indicator made `/actuator/health` DOWN, a table list in `AuthSchemaIT`, two test mistakes of mine) | 2nd run `BUILD SUCCESS`, **168 tests**, 0 failures |
| `npm run format`, `format:check`, `npm run build` | OK, `tsc` clean, 130 modules |
| `docker compose up -d --build auth-service gateway mailpit` | all healthy; Flyway "Migrating schema "auth" to version "3 - password reset"" applied |
| Review fixes (equal hashing in `reset`, comparison outside the lock, STARTTLS required, login time before the check, tests); `mvn -B clean verify` again | `BUILD SUCCESS`, **172 tests**, 0 failures |
| Live script again on the rebuilt services | same result as before; session A ended about 66 s after the reset; 0 log lines with the code or a password |
| Live script (throw-away account `reset-test-…@faircourt.local`, see evidence) | all steps as expected |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `PasswordResetIT`, live | `PASS` | Known and unknown email: `202 {}` both; only the known one got a mail; second request inside 60 s: same answer, no new mail; unknown email does the same hashing work (rough timing test) |
| AC-02 | `PasswordResetIT`, live | `PASS` | Right code + valid password → 204; new password signs in, old fails (401); the same code again → 401 |
| AC-03 | `PasswordResetIT` | `PASS` | Wrong, unknown email, replaced, expired → identical 401 body; after 5 wrong tries the right code is dead; 24 parallel wrong guesses leave exactly 5 tries recorded |
| AC-04 | `PasswordResetIT` | `PASS` | 1 per 60 s and 3 per hour, answer never changes, no extra row or mail; allowed again after the hour |
| AC-05 | `PasswordResetIT`, gateway ITs, live | `PASS` | `session-check` false after the reset; gateway ends the session (stub test); live: the session signed in before the reset got 401 about 66 s later; auth-service failing keeps the session; no automatic login |
| AC-06 | `PasswordResetIT`, live | `PASS` | Stored value is `{bcrypt}…`, never the code; changed mail sent; log scan: 0 lines with the code or a password |
| AC-07 | `PasswordRulesTest`, `PasswordResetIT`, live | `PASS` | 11 chars, 73 bytes, 74 bytes with 37 characters, repeated character, email, "password1234" → 400 and the code is not used up |
| AC-08 | visual | `NOT VERIFIED` | Frontend builds; pages not looked at in a browser |

Not verified: the Mac; a real SMTP server (only the local catcher and a recording test double); HTTPS; a browser; delivery to a real inbox; timing attacks beyond the rough timing test.

## Deviations from the design

- Added env vars `MAIL_SMTP_AUTH` and `MAIL_STARTTLS` (a real SMTP server needs them; the design listed five variables).
- Added `management.health.mail.enabled: false`: the mail health check made the service report DOWN when no mail server was reachable (found by a test); a mail outage must not stop login.
- Common-password list: about 60 entries plus a "one repeated character" rule (the design said about 100 entries). The list is short and ours, not a breached-password check. A password equal to the email shows the "tooCommon" message; the form only knows length, bytes and email, the server answers 400 for the common list and the form then says "Choose a less common password".
- Refactor: `LoginPage` now uses the shared `AuthPageShell` (also used by the two new pages).
- `RequestBodyLimitFilter` (4 KB) now covers the two new paths; `ClockConfig` added to auth-service; `MAILPIT_PORT` (9094) added to `.env.example`.
- `Referrer-Policy`: the design asked for it on the reset pages; done for the whole app with `<meta name="referrer" content="no-referrer">` in `frontend/index.html` (a meta tag cannot be set per page).
- Mail subjects ("Your FairCourt code", "Your FairCourt password was changed") and the time format of the changed mail ("d MMM yyyy HH:mm (Vietnam time)") are **not in the approved copy** (the design approved only the body texts): pending owner approval.
- Names: the mail class is `ResetMailSender` (the design said `MailSender` and `ResetMails`, one class holds the texts); SMTP timeouts of 5 s were added to the mail settings; `GatewayRouteTable.production` takes the auth-service URL as a second argument.
- The email goes from the forgot page to the reset page in router state (not in the URL), so it stays out of history and logs.
- "Ended within a minute" means: at most one recheck interval (60 s) after the last successful check of that session.
- Two throw-away accounts (`reset-test-17828@faircourt.local`, `reset-test-24731@faircourt.local`) (copy of the demo hash, then its password was reset by the live test) were left in the local development database; they can be deleted by the owner (a delete needs the owner's OK).
