# F21 — Password reset (forgot password, OTP by email)

| Field | Value |
|---|---|
| Sprint | S02 Identity |
| Status | `IN_REVIEW` |
| Blueprint | §15 ("account verification/recovery … before wider public signup"), §7, §13 (new routes, owner-approved addition); OWASP Forgot Password, Authentication, Session Management cheat sheets |
| Depends on | F03 (Account login) |
| Waiting for | Owner approval of the items in review.md (mail subjects and time format, shorter common-password list, extra env vars) and acceptance of the residual risks; owner looks at both pages at 320 / 375 / 1280px (AC-08 `NOT VERIFIED`); ideally a second review under Opus 5.5 |
| Suggested branch | `feature/password-reset` |
| Last updated | 2026-11-18 |

Docs: [design.md](design.md) · [implement.md](implement.md) · [review.md](review.md)

## Goal

A person who forgot their password asks for a one-time code by email, enters the code and a new password, and can sign in again. Everything an attacker could abuse (guessing codes, finding out which emails have accounts, flooding someone's inbox, keeping an old session alive) is blocked or limited.

## Scope

- In: "Forgot password?" link on the login page; page to ask for a code; page to enter the code and the new password; emails (code, and "your password was changed"); auth-service tables and routes; ending all existing sessions of the account after a reset; local mail catcher for development.
- Out: signup, email change, "remember me", MFA, SMS, security questions, CAPTCHA, admin-initiated reset (backlog L-11, L-17).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Asking for a code for a known and an unknown email gives the same answer (status, body, about the same time); only the known email receives a mail | IT + mail catcher |
| AC-02 | A correct code and a valid new password change the password; the old password stops working; the code cannot be used twice | IT |
| AC-03 | A wrong, expired, used or replaced code all give the same answer; after 5 wrong tries the code is dead | IT |
| AC-04 | At most 3 codes per email per hour and 1 per minute (extra requests get the same answer, no mail) | IT |
| AC-05 | After a reset every existing session of that account is ended within a minute; no automatic login | IT |
| AC-06 | A "your password was changed" mail is sent; the code is stored only as a hash; no code or password in logs | IT + log check |
| AC-07 | New password rules enforced (D-4); same rules on server and form | IT + visual |
| AC-08 | Both pages usable at 320 / 375 / 1280px | visual (owner) |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-18 | Owner asked: "nên thêm cái reset password khi người dùng forgot password. dĩ nhiên là có xác thực OTP qua mail" while approving Account login. Feature created; design written, waiting for approval | Owner, conversation 2026-11-18 |
| 2026-11-18 | **APPROVED** by the owner: "đồng ý đề xuất" for D-1…D-9. Implemented (backend 168 tests, live check on Docker); two review agents started; status `IN_REVIEW` | Owner, conversation 2026-11-18 |
| 2026-11-18 | Reviews run, defects fixed (review.md); live check repeated on the rebuilt stack; open for the owner: G3, G4, G6 approvals, residual risks S2/S6, visual check | review.md |
