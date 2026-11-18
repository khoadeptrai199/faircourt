# F04 — Guest check-in + guest cookie

| Field | Value |
|---|---|
| Sprint | S02 Identity |
| Status | `WAITING_APPROVAL` |
| Blueprint | §3, §4 (checklist prompts and anchors), §11 (guest `PENDING`), §12 (`guest_access`, token rules), §13 (`POST /public/sessions/{publicCode}/guests`, here `/public/games/...`; band mapping), §16 (`/s/:publicCode/join`, here `/g/:publicCode/join`); OD-02 (decided), OD-06, OD-22 |
| Depends on | F02 (Public game page), F18 (services) |
| Waiting for | Owner answers to D-1…D-10 in [design.md](design.md) (4 new internal routes, cookie lifetime, copy, abuse risk) |
| Suggested branch | `feature/guest-check-in` |
| Last updated | 2026-11-18 |

Docs: [design.md](design.md)

## Goal

A visitor opens the public link, presses Join game, enters a display name, answers four behavior questions (or says "not sure"), and sees "Waiting for host approval". The browser holds a private, HttpOnly guest cookie scoped to this one game and this one attendance.

## Scope

- In: public route `POST /api/v1/public/games/{publicCode}/guests` in the gateway; orchestration in playmatch-service (profile in player-service, `PENDING` attendance, credential in auth-service) with compensation; `GuestAnswers` and the band mapping; guest cookie; same browser joining again returns the existing attendance; the join page with a name step and four question steps; the public page's Join button becomes a link; logout no longer clears the guest cookie.
- Out: the guest reading their own status (Own attendance status), host approve/reject (Attendance management), cross-device recovery, host-assisted check-in (L-09), per-source abuse limits (L-26).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Valid submit → 201 `{"attendanceState":"PENDING","message":"Waiting for host approval"}`; cookie HttpOnly, SameSite=Lax, `Secure` = `COOKIE_SECURE`, expiry `max(endsAt, now) + 12 h`; the body has no ids | gateway IT |
| AC-02 | DB holds only the 64-hex SHA-256 of the token; the raw token is absent from DB, logs and response body | auth IT + log capture |
| AC-03 | Band boundaries 3/4, 6/7, 9/10; all `null` → band `null`; partial or out-of-range answers → 400 | unit + IT |
| AC-04 | Same browser joins the same game again → 200, same attendance, no new rows, no new cookie; unknown/expired/other-game cookie → new check-in | gateway + playmatch IT |
| AC-05 | Display name trimmed, 1–80 characters, no control characters, else 400; duplicate names allowed | IT |
| AC-06 | Unknown code → 404; `CLOSED` game → 409 `STATE_CONFLICT` | IT |
| AC-07 | Missing CSRF token → 403; form post refused; body over 4 KB refused | gateway IT |
| AC-08 | Join page: name step, four steps with answer cards, progress label, in-place validation, pending panel; usable at 320 / 375 / 1280px | visual (owner) |
| AC-09 | Failure handling: player-service down → 503 and no rows; auth-service down → attendance `LEFT` + 503 | playmatch IT with stubs |
| AC-10 | Account logout keeps the guest cookie | gateway IT |

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-02 | `gameId` in the public summary | DECIDED 2026-11-18 (F02) |
| OD-06 | Cookie lifetime after `ends_at` | Proposed in design D-1: 12 h |
| D-2…D-10 | Cookie name, orchestration and internal routes, saga failures, re-join answer, abuse (D-6), copy, identity token, name rules, logout change | Waiting for owner (design §10) |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created (BACKLOG) | Planning setup |
| 2026-11-18 | Design written for the game naming (OD-22) and the microservice orchestration (AD-16); waiting for approval | Claude |
