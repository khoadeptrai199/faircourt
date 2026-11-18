# F03 review — Account login

Date: 2026-11-18. Reviewers: `privacy-reviewer` (security, adversarial) and `scope-guardian` (read-only agents) on the working tree of `feature/account-login`. Both ran while the session used Sonnet 5.5; the owner's rule (2026-11-18) is that reviews run under Opus 5.5, so a second pass under Opus is advisable before this feature is called `DONE`.

## privacy-reviewer — 0 high, 4 medium, 5 low

| # | Severity | Finding | Resolution |
|---|---|---|---|
| S1 | medium | Open redirect: `next=/.//evil.com` normalises to `//evil.com` | Fixed: result must start with a single `/`; 15 attack strings checked in Node (all go home, `/%5cevil.com` stays a path on this site) |
| S2 | medium | Throttle checks, verifies, then records: parallel guesses pass the limit | Fixed: the attempt is counted first (`tryAcquire`), given back only when the password service gave no verdict; unit test with 64 threads; live: 50 parallel guesses → 5 passed, 45 got 429 |
| S3 | medium | Password spraying across many emails is not throttled | **Not fixed**, recorded as L-26 (needs an owner decision: per-source or global limit and how to know the client address behind the proxy) |
| S4 | medium | No size limit on the anonymous login body | Fixed: 4 KB cap in `RequestBodyLimitFilter` (also chunked); test + live check |
| S5 | low | Gateway accepts a blank password, auth-service refused it (400 → shown as 503) | Fixed: auth-service accepts any non-empty password; a 400 from auth-service is no longer "service down"; tests |
| S6 | low | Lock can be renewed with one request per 15 minutes | Found while fixing: the attempt window equalled the maximum lock, so a maximum lock reset the count. Window raised to 1 hour; trade-off written in the design |
| S7 | low | CSRF token not regenerated at login | Fixed: removed at login; old token refused; test + live |
| S8 | low | `COOKIE_SECURE=false` default would fail open on HTTPS | Accepted (blueprint §0); HTTPS + `COOKIE_SECURE=true` + `__Host-` cookie recorded as a gate before any real deployment |
| S9 | low | Demo credentials are public | Accepted: `local` profile only; recorded |

Missing tests the reviewer listed and what was done: concurrent attempts (added), oversized body (added), blank password (added), CSRF token change (added), re-login as another account (added), `safeNextPath` (no frontend test setup; checked by script, a test framework needs an owner-approved dependency), absolute timeout through the servlet chain (unit-tested for the filter only), spraying (L-26).

## scope-guardian — 2 high, 4 medium, 3 low; all are "needs owner approval" or documentation

| # | Severity | Finding | Resolution |
|---|---|---|---|
| G1 | high | Login throttle, HTTP 429 and `TOO_MANY_REQUESTS` are not in the approved design (design §11 had lockout out of scope) | Added on the owner's instruction "đảm bảo các security … tham khảo các chuẩn"; recorded in design §12; **owner to confirm** the throttle, the numbers and the 429 code |
| G2 | high | New string `login.tooManyAttempts` | Marked pending in the copy table; **owner to approve** |
| G3 | medium | Idle timeout 30 min + absolute 8 h differs from approved D-3 (8 h) | Recorded in design §12; **owner to confirm** the values |
| G4 | medium | Cookie `fc_sid` and `SESSION_COOKIE_NAME` | Recorded; works with the default, nothing to set locally |
| G5 | medium | Password limit in bytes, not characters | Recorded (D-5 amended in design §12) |
| G6 | medium | `no-store`, `Clear-Site-Data` (clears every cookie of the site, check again when the guest cookie arrives), JSON-only login | Recorded in design §12 |
| G7 | low | Extra files (`ClockConfig`, `AbsoluteSessionTimeoutFilter`, `RequestBodyLimitFilter`, `TextField`, `lib/redirect.ts`) | Listed in implement.md |
| G8 | medium | Planning docs out of date (status, no implement.md) | Fixed |
| G9 | low | Home page removed the disabled button | Allowed by design §7 |

No new dependency, no migration, no deletion, `README.md` untouched, only `POST /internal/v1/auth/verify` added as internal route.

## Gate

Open for the owner: G1, G2, G3 (explicit approval), S3 (decision on L-26). No open high or medium finding that is a defect.
