# S02 — Identity

| Field | Value |
|---|---|
| Status | `PLANNED` |
| Goal | Pilot accounts can sign in; a visitor can check in as a guest and sees "Waiting for host approval"; each person can read only their own attendance state |
| Gate | §17 step 2: "anonymous sees only public data; guest sees only own state" |
| Blueprint | §3, §4, §11, §12, §13, §15, §16 |
| Started / finished | — |

## Features

| ID | Feature | Status |
|---|---|---|
| F03 | [Account login](account-login/README.md) | `IN_REVIEW` |
| F04 | [Guest check-in](guest-check-in/README.md) | `BACKLOG` |
| F05 | [Own attendance status + member join](my-attendance-status/README.md) | `BACKLOG` |

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

## Notes and retro
