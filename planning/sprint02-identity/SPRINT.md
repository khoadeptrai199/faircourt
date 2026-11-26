# S02 — Identity

| Field | Value |
|---|---|
| Status | `DONE` |
| Goal | Pilot accounts can sign in; a visitor can check in as a guest and sees "Waiting for host approval"; each person can read only their own attendance state |
| Gate | §17 step 2: "anonymous sees only public data; guest sees only own state" |
| Blueprint | §3, §4, §11, §12, §13, §15, §16 |
| Started / finished | 2026-11-18 / 2026-11-25 |

## Features

| ID | Feature | Status |
|---|---|---|
| F03 | [Account login](account-login/README.md) | `DONE` |
| F21 | [Password reset (OTP by email)](password-reset/README.md) | `DONE` |
| F04 | [Guest check-in](guest-check-in/README.md) | `DONE` |
| F05 | [Own attendance status + member join](my-attendance-status/README.md) | `DONE` |

F21 was added at the owner's request on 2026-11-18 (forgot password with an emailed code), after F03 and before F04.

## Why this order

F03 (account session) uses Spring Security's standard session login. F04 adds the custom guest credential (32 random bytes, SHA-256 in DB, HttpOnly cookie), which is harder. F05 needs both identities to answer "who is calling" for `/me`.

## Demo at sprint end

1. Host logs in from the home page.
2. A private browser window opens the public link, joins as guest with the four questions, sees "Waiting for host approval".
3. The guest's `/my/sessions/<id>` shows `PENDING`; an anonymous `curl` of the same `/me` gets 401.
4. `player1@faircourt.local` logs in and joins; their `/me` shows `READY`.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|
| Pilot account signs in and out; CSRF on login/logout; session id changes at login | `PASS` | Account login implement.md, 2026-11-18: AC-01…AC-05, AC-07 (ITs + live `curl`); login page visual `NOT VERIFIED` there |
| Guest checks in and sees "Waiting for host approval"; guest cookie HttpOnly, token stored hashed | `PASS` | Guest check-in implement.md, 2026-11-18: AC-01, AC-02 live check + ITs |
| Each person reads only their own state; anonymous `/me` → 401; other game → 404 | `PASS` | Own attendance status review.md, 2026-11-20: AC-01…AC-04 ITs + live check |
| Visual check of the My game page (TC-21) | `NOT VERIFIED` (owner-accepted) | Owner closed the sprint on 2026-11-25 without a recorded visual check by Claude |
| Sprint closed | `DONE` | Owner, conversation 2026-11-25; all four features `DONE` |

## Notes and retro
