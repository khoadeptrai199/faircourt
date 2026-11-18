# F21 review — Password reset

Date: 2026-11-18. Reviewers: `privacy-reviewer` (adversarial security) and `scope-guardian` (read-only agents) on the working tree of `feature/password-reset`. Both ran while the session used Sonnet 5.5; the owner's rule is that reviews run under Opus 5.5, so a second pass under Opus is advisable before the feature is called `DONE`.

## privacy-reviewer — 0 high, 3 medium, 6 low (no account takeover and no way around the code found)

| # | Severity | Finding | Resolution |
|---|---|---|---|
| S1 | medium | Timing leak on `reset`: a known email ran two BCrypt steps, an unknown one only one → emails could be found | Fixed: both cases now run the new-password hash and the code comparison, outside any lock; timing test added (`resetForAnUnknownEmail…`) |
| S2 | medium | An attacker can keep a victim's recovery closed (ask for a code, burn it with 5 wrong tries, repeat; at most 3 codes per hour) | **Not fixed, accepted risk (owner accepted 2026-11-18)** (follows from "no per-IP limit"; the login is never locked, only recovery is slowed). Related backlog L-26 |
| S3 | medium | With a real SMTP server STARTTLS was only optional and the server name was not checked | Fixed: with `MAIL_STARTTLS=true` TLS is required and `ssl.checkserveridentity` is on |
| S4 | low | BCrypt ran while the code row lock and a database connection were held | Fixed: comparison outside the lock; the row is locked only for the short write |
| S5 | low | Small timing difference in `requestCode` (a known account runs a transaction, an unknown one a lookup) | Accepted: BCrypt dominates; strict parity would need dummy queries |
| S6 | low | Session end fails open when auth-service errors (stolen session survives while auth-service is down; up to 60 s after a reset) | Accepted and written down; idle 30 min and absolute 8 h still apply |
| S7 | low | Login race: a login whose password check passed just before a reset could get a session newer than the password change | Fixed: the login time is taken before the password is checked |
| S8 | low | After a session is ended the next write gets 403 `CSRF_TOKEN_INVALID` | Accepted: the frontend retries once with a fresh token |
| S9 | low | Mailpit has no password and its SMTP port is open inside Compose | Accepted for local use; Compose comment now says "LOCAL DEVELOPMENT ONLY, never deploy" |

Tests added from the reviewer's list: reset timing, a code of one account cannot be used for another, CRLF in the email field cannot reach the mail (no Bcc), `no-store` on every answer of both routes. Not added: reset racing a code request, revalidation boundary at exactly the interval, an access-matrix row for `/internal/v1/auth/**` (the existing login row already covers `/internal/**` never being routed).

## scope-guardian — 1 high (docs), 5 medium, 7 low; nothing outside the approved design except what is recorded

| # | Severity | Finding | Resolution |
|---|---|---|---|
| G1 | high | Planning docs stale, no `implement.md` | Fixed: status, decision log, copy "Approved" column, `implement.md` with commands and evidence |
| G2 | medium | `Referrer-Policy` required by the design was missing | Fixed: `<meta name="referrer" content="no-referrer">` in `frontend/index.html` (whole app) |
| G3 | medium | Mail subjects and the changed-mail time format are not in the approved copy | Recorded; owner approved 2026-11-18 the subjects "Your FairCourt code" / "Your FairCourt password was changed" and the format "18 Nov 2026 18:30 (Vietnam time)" |
| G4 | medium | Common-password list is about 60 entries (design: about 100) plus a repeated-character rule; email-equal password shows the "tooCommon" message | Recorded in implement.md; owner approved 2026-11-18 |
| G5 | medium | "checked 2026-11-18" for the Mailpit image had no evidence | Evidence (`docker buildx imagetools inspect`) in implement.md |
| G6 | medium | Extra env vars `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAILPIT_PORT`, 5 s SMTP timeouts, `management.health.mail.enabled: false` | Recorded as deviations; owner approved 2026-11-18 |
| G7 | low | `ClockConfig`, body-limit filter with several paths, `GatewayRouteTable` signature, `ResetMailSender` name, `LoginPage` → `AuthPageShell` refactor | Recorded in implement.md |

No applied migration edited, no deletion, no new dependency except `spring-boot-starter-mail`, Mailpit pinned and on `127.0.0.1:9094`, `README.md` untouched.

## Gate

No open defect. G3, G4, G6 approved and risks S2, S6 accepted by the owner on 2026-11-18. Open: visual check of both pages; ideally a second review under Opus 5.5.
