# F21 design — Password reset by emailed one-time code

Status: `APPROVED` (2026-11-18, by owner: "đồng ý đề xuất" for D-1…D-9, including the new dependency, the mail-catcher container, the new routes, migration V3, the copy table and the mail texts).

## 1. Summary

Two anonymous steps, both answered by auth-service (it owns accounts), reached through the gateway:

1. **Ask for a code:** the person types their email. The answer is always the same ("If that email has an account, we sent a code"). If the account exists, auth-service creates a one-time code, stores only its hash, and emails the code.
2. **Reset:** the person types the email, the code and a new password (twice in the form). If the code is right, the password changes, all codes of that account are cancelled, a "your password was changed" mail is sent, and every existing session of the account ends. The person then signs in normally (no automatic login).

Reusable pieces added: a small mail sender in auth-service (one place that sends mail; later it can move behind a notification service, AD-17), a local mail catcher container for development, and a session re-check in the gateway so a reset can end sessions that live in the gateway's memory.

## 2. References

- §15: "Before wider public signup, add account verification/recovery and abuse controls; these are a rollout gate, not a dependency for a closed pilot" — the owner asked for recovery now.
- OWASP Forgot Password: same message and timing for existing and unknown accounts; tokens/PINs from a secure random generator, single use, expiring, stored hashed; PIN 6–12 digits; rate-limit requests and guesses; **do not lock the account** because of forgot-password attacks; new password entered twice, same password policy; notify the user; no auto login; invalidate sessions; never put the password in an email.
- AD-12 (own schema), AD-15 (internal routes, short timeouts), AD-17 (events/notifications later with Kafka).

## 3. API contract

### Public routes (the gateway forwards both to auth-service; anyone; CSRF token required like every write)

| Route | Request | Response | Errors |
|---|---|---|---|
| `POST /api/v1/auth/password/forgot` | `{"email"}`; 1–254 chars | `202` always, body `{}`; no mail is sent when the email is unknown, when the 60 s cooldown has not passed, or when 3 codes were already sent in the last hour | 400 `VALIDATION_FAILED` (empty or too long email); 403 `CSRF_TOKEN_INVALID`; 503 |
| `POST /api/v1/auth/password/reset` | `{"email","code","newPassword"}`; code exactly 8 digits; new password per D-4 | `204` | 400 `VALIDATION_FAILED` (malformed or password rule broken); **401 `UNAUTHENTICATED` with the same body for wrong, expired, used, replaced and unknown-email cases**; 403 `CSRF_TOKEN_INVALID`; 503 |

The "two times" entry of the new password is checked in the form only (it has no meaning for the API).

Allowed response keys: none (empty bodies). Nothing in any answer reveals whether an email has an account.

### Internal route (auth-service; the gateway calls it; never routed publicly) — needs approval (AD-15)

| Route | Request | Response |
|---|---|---|
| `GET /internal/v1/auth/accounts/{userId}/session-check?since=<ISO instant>` | `since` = when the session logged in | `200 {"valid": true\|false}`; `false` when the password changed after `since` or the account no longer exists |

## 4. Data and transactions

New migration `auth` **V3** (new file, V1/V2 untouched):

| Change | Detail |
|---|---|
| `app_user.password_changed_at timestamptz NOT NULL DEFAULT now()` | Set at every password change; used by the session check |
| new table `password_reset` | `id uuid PK`, `user_id uuid NOT NULL REFERENCES app_user(id)`, `code_hash varchar(100) NOT NULL` (BCrypt through the existing `PasswordEncoder`, no new secret), `created_at`, `expires_at`, `failed_attempts smallint NOT NULL DEFAULT 0`, `consumed_at timestamptz NULL`; index on `(user_id, created_at DESC)` |

- Ask for a code (one `@Transactional` method): find the account by `lower(email)`; count codes created in the last hour; read the newest; if allowed, cancel older unused codes (set `consumed_at`), insert the new row. **The mail is sent after the commit, on a separate thread**, so a slow mail server cannot change the response time or reveal anything. An unknown email runs the same steps with a dummy BCrypt hash so the work and time are alike.
- Reset (one `@Transactional` method): `SELECT … FOR UPDATE` on the newest unused, unexpired code of the account; compare with BCrypt; wrong → `failed_attempts + 1` and, at 5, mark it consumed; right → set new password hash and `password_changed_at = now`, mark every code of the account consumed, then send the "changed" mail after the commit.
- Codes are never logged or returned; a test checks the log output.

## 5. Domain rules

- Code: 8 random digits from `SecureRandom` (10^8 possibilities), valid 10 minutes, one use, at most 5 wrong tries, a newer code cancels the older ones.
- Requests: 1 per 60 seconds and 3 per hour per email, enforced in the database (survives restarts); over the limit the answer is the same `202`, no mail (no inbox flooding, no information).
- Guessing limit overall: 5 tries per code × 3 codes per hour = at most 15 guesses per hour against 10^8: negligible. The account is **not** locked by any of this (OWASP).
- New password (D-4): 12–72 bytes (BCrypt reads at most 72, never cut), not equal to the email, not one of the passwords in a short built-in list of very common ones (about 100 entries, our own list, no new dependency). Same rule in the form.
- Sessions: a session whose login time is before `password_changed_at` is ended. The gateway asks auth-service at most once a minute per session (`session-check`); if auth-service is down the session is kept (the 30 min idle / 8 h absolute limits still apply) and the check is retried.
- Mails: plain text, no links (so no host-header or referrer risks), never contain a password. Code mail: the 8 digits, "valid for 10 minutes", "if you did not ask for this, ignore this mail". Changed mail: "your password was changed at <time, Vietnam time>; if this was not you, ask the host". Sender address and server come from configuration.

## 6. Backend components

| File | Service | Notes |
|---|---|---|
| `V3__password_reset.sql` | auth-service | Section 4 |
| `PasswordResetController`, `PasswordResetService`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `PasswordRules` | auth-service | Controller thin; rules in the service |
| `MailSender` (interface-free class over Spring `JavaMailSender`), `ResetMails` (texts) | auth-service | Sends on a separate thread; failure is logged without the code and does not change the answer |
| `SessionCheckController` | auth-service | The internal route |
| `SecurityConfig` | auth-service | Opens `POST /api/v1/auth/password/forgot|reset` (service side) |
| `GatewayRouteTable` + `faircourt.services.auth-url` | gateway | Two forwarded PUBLIC POST routes to auth-service |
| `SessionRevalidationFilter` + `AuthClient.sessionCheck` | gateway | At most once per minute per session; ends the session when `valid` is false |
| `compose.yaml`, `.env.example` | repo | Mail catcher container; mail settings (D-3) |
| `pom.xml` (auth-service) | auth-service | `spring-boot-starter-mail` (version from the Spring Boot BOM) |

## 7. Frontend

| File | Purpose |
|---|---|
| `src/features/password-reset/ForgotPasswordPage.tsx` | Route `/forgot-password`: email field → "code sent" message and a link/button to the next step |
| `src/features/password-reset/ResetPasswordPage.tsx` | Route `/reset-password`: email, code, new password, repeat password → success message with a "Sign in" link |
| `src/api/passwordReset.ts` | `requestCode`, `resetPassword` |
| `src/features/login/LoginPage.tsx` | "Forgot password?" link |
| `src/lib/passwordRules.ts` | Same rules as the server (length, common list) for in-place messages |

States: idle / saving / success / error for both forms; wrong or expired code → one message; 429 not used (the throttle answers 202); service down → shared error banner with Try again. Buttons disabled while saving; password fields `autocomplete="new-password"`; the code field `autocomplete="one-time-code"`, numeric keyboard; password is cleared after any error; the page sends `Referrer-Policy: no-referrer` through a meta tag.

### Copy (all NEW, need owner approval)

| Key | Text | Approved |
|---|---|---|
| `login.forgot` | Forgot password? | yes (2026-11-18) |
| `forgot.title` | Reset your password | yes (2026-11-18) |
| `forgot.email` | Email | (reuses `login.email`) yes |
| `forgot.submit` | Send code | yes (2026-11-18) |
| `forgot.sent` | If that email has an account, we sent a code. It is valid for 10 minutes. | yes (2026-11-18) |
| `forgot.haveCode` | I have a code | yes (2026-11-18) |
| `reset.title` | Enter your code | yes (2026-11-18) |
| `reset.code` | Code | yes (2026-11-18) |
| `reset.newPassword` | New password | yes (2026-11-18) |
| `reset.repeat` | Repeat new password | yes (2026-11-18) |
| `reset.submit` | Change password | yes (2026-11-18) |
| `reset.invalidCode` | The code is wrong or has expired. | yes (2026-11-18) |
| `reset.mismatch` | The two passwords are different. | yes (2026-11-18) |
| `reset.tooShort` | Use at least 12 characters. | yes (2026-11-18) |
| `reset.tooLong` | Use at most 72 characters. | yes (2026-11-18) |
| `reset.tooCommon` | Choose a less common password. | yes (2026-11-18) |
| `reset.success` | Your password was changed. Sign in with the new password. | yes (2026-11-18) |
| `reset.toLogin` | Sign in | (reuses `login.submit`) |

Mail texts (section 5) also need approval: [CODE MAIL] "Your FairCourt code is {code}. It is valid for 10 minutes. If you did not ask for it, ignore this mail." / [CHANGED MAIL] "Your FairCourt password was changed at {time}. If this was not you, tell your host."

## 8. Security and privacy checklist

- [ ] Same answer and about the same time for known, unknown, throttled and cooled-down emails on "ask for a code"
- [ ] Code from `SecureRandom`, 8 digits, 10 minutes, single use, 5 tries, stored only as a BCrypt hash
- [ ] One 401 for every kind of bad code; the account is never locked by reset attempts
- [ ] Per-email request limits in the database (1/min, 3/hour); inbox flooding impossible
- [ ] New password rules on the server; never logged; hashed with the same encoder
- [ ] No code, password or hash in logs, errors, mail subjects, URLs or the browser history (no link, no query string)
- [ ] All sessions of the account end after a reset; no auto login; "changed" mail sent
- [ ] Reset routes need the CSRF token like every write; body size capped like login
- [ ] Mail settings only from environment variables; password never in a file
- [ ] The mail catcher is published on `127.0.0.1` only

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Known and unknown email → same status and body; mail only for the known one; response times alike | `PasswordResetIT` + mail catcher / test mail sink |
| TC-02 | AC-02 | Right code + valid password → 204; login with new password works, old fails; same code again → 401 | IT |
| TC-03 | AC-03 | Wrong / expired (clock) / used / replaced / unknown email → identical 401; 5 wrong → dead; right code after that → 401 | IT |
| TC-04 | AC-04 | 2nd request within 60 s and 4th within an hour → 202, no mail, no new row | IT |
| TC-05 | AC-05 | Sign in, reset, `session-check` false, gateway ends the session within a minute; auth-service down → session kept | gateway IT + IT |
| TC-06 | AC-06 | `code_hash` is not the code; logs contain neither code nor password; "changed" mail sent | IT |
| TC-07 | AC-07 | Password rules: 11 chars, 73 bytes, equal to email, in common list → 400 | unit + IT |
| TC-08 | — | Parallel wrong guesses (50 threads) never exceed 5 tries per code | IT |
| TC-09 | — | Mail failure → still 202 | IT |
| TC-10 | AC-08 | Pages at 320 / 375 / 1280px, errors, success | visual (owner) |
| TC-11 | — | Pipeline (backend + frontend) | pipeline |

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | Code format | 6 digits / **8 digits** / a link with a long token | 8 digits: OWASP allows 6–12; 8 costs the user little and makes guessing 100× harder than 6 |
| D-2 | Code lifetime and limits | 10 min, 5 tries, 1 per minute, 3 per hour | as listed |
| D-3 | Sending mail: new dependency `spring-boot-starter-mail` (Spring Boot BOM, no version to pick), new container for local development (a mail catcher such as Mailpit, web page on `127.0.0.1:9094`, SMTP only inside Compose; its multi-arch image is **not verified yet**, checked before pinning), new environment variables (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`) | approve / change / use another way | approve. For a real pilot the owner must choose a mail provider (any SMTP server); until then only the local catcher works. Seeded accounts use fake addresses (`@faircourt.local`), so a real reset needs real emails on the accounts |
| D-4 | New password rule | 12–72 bytes, not equal to the email, not in a built-in list of ~100 very common passwords / other | as listed (OWASP: ≥ 15 without MFA is the strong value; 12 chosen to stay usable, tell me if you want 15) |
| D-5 | Ending old sessions after a reset | (a) the gateway asks auth-service at most once a minute per session (needs the internal route above) · (b) do nothing, rely on the 30 min / 8 h limits | (a): an attacker who stole a session loses it within a minute of the reset |
| D-6 | Where the mail is sent from | (a) auth-service sends directly now · (b) wait for a notification service with Kafka (AD-17) | (a); later it moves behind the notification service without changing the routes |
| D-7 | Routes and tables above (two public routes, one internal route, migration V3) | approve / change | approve |
| D-8 | Copy and mail texts (section 7) | approve / edit | — |
| D-9 | Where in the plan | in S02 right after Account login (before Guest check-in) / later | in S02 |

## 12. Review result and residual risks (2026-11-18)

See [review.md](review.md). Fixed after the security review: equal hashing work in `reset`, comparison outside the row lock, STARTTLS required with a real server, login time taken before the password check, `no-store`, `Referrer-Policy`. Residual risks the owner should know:

- **Denial of recovery:** someone who knows an email can ask for a code and burn it with 5 wrong tries, repeatedly (at most 3 codes per hour per email), so the real owner cannot reset. The login itself is never locked. A per-source limit would help (backlog L-26).
- **Inbox flooding is limited, not zero:** up to 3 mails per hour per email.
- **Sessions after a reset end within about a minute, and not at all while auth-service is down** (idle 30 min and absolute 8 h still apply).
- **Mail travels as plain text by email** (the code is visible to anyone who can read the inbox); with a real server TLS to the mail server is required.
- **The local mail catcher shows every code to anyone who opens http://localhost:9094**: local development only, never deploy it.

## 11. Out of scope

- Signup, email verification of new accounts, changing the email address (L-11, L-17 Keycloak)
- SMS, authenticator apps, MFA, CAPTCHA
- Reset started by an admin or by the host for another person
- Notification service and Kafka (AD-17)
- Password-reset audit trail visible to the user
