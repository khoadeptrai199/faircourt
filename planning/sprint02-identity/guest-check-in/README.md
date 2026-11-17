# F04 — Guest check-in + guest cookie

| Field | Value |
|---|---|
| Sprint | S02 Identity |
| Status | `BACKLOG` |
| Blueprint | §3, §4 (checklist prompts and anchors), §11 (guest `PENDING`), §12 (`guest_access`, token rules), §13 (`POST /public/sessions/{publicCode}/guests`, band mapping), §16 (`/s/:publicCode/join`) |
| Depends on | F02 |
| Waiting for | OD-02, OD-06 (cookie lifetime) |
| Suggested branch | `feature/guest-check-in` |
| Last updated | 2026-11-17 |

## Goal

A visitor opens the public link, enters a display name, answers four behavior questions, and lands on "Waiting for host approval". The browser holds a private, HttpOnly guest credential scoped to this session.

## Scope

- In: guest POST endpoint; `GuestAnswers` value object (all four 0–3, or all four `null`; total 0–3 → 1, 4–6 → 2, 7–9 → 3, 10–12 → 4); one transaction creating `player_profile` (GUEST), `attendance` (PENDING), `skill_assessment` (SELF, answers JSONB, band), `guest_access` (SHA-256 of 32 random bytes); cookie Set; re-join with a valid cookie for the same session returns the existing attendance; guest authentication filter (`GUEST_ACCESS_EXPIRED` on expired/revoked); join page with four `QuestionStep`s and progress; API client write path with CSRF.
- Out: cross-device recovery, host-assisted check-in (L-09), merging guests by name (forbidden by §7).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Valid submit → 201 `{"attendanceState":"PENDING","message":"Waiting for host approval"}` (+ `sessionId` if OD-02 says so); cookie HttpOnly, SameSite=Lax, Secure = `COOKIE_SECURE`, expiry per OD-06 | IT |
| AC-02 | DB holds only the 64-hex SHA-256 of the token; the raw token is absent from DB, logs and response body | IT (row + log capture) |
| AC-03 | Mapping boundaries 3/4, 6/7, 9/10; all `null` → band `null`; partial answers → 400 | unit (`GuestAnswersTest`) + IT |
| AC-04 | Same browser re-joins the same session → same attendance, no new rows | IT |
| AC-05 | Display name trimmed, 1–80 chars, else 400 | IT |
| AC-06 | Unknown code → 404; `CLOSED` session → 409 `STATE_CONFLICT` | IT |
| AC-07 | Missing CSRF token → 403 | IT |
| AC-08 | Join page: four steps with answer cards, progress label, in-place validation, pending panel | visual |

## Design notes

- `identity/GuestCredentialService` (issue, hash, resolve), `identity/GuestAuthenticationFilter`, `players/GuestCheckInService`, `players/GuestAnswers`.
- `SecureRandom` for the 32 bytes; hex SHA-256 matches `token_hash CHAR(64)`.
- Prompts from §4: serve lands in, sustain a rally, cover when partner is at the net, moving backward and recovering. Anchors from §4: still learning / sometimes / usually / consistently → 0–3. An "I'm not sure" option that produces `null` for all four is NEW copy.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-02 | `sessionId` returned to the guest? | Open |
| OD-06 | Cookie lifetime after `ends_at` | Open |
| — | Cookie name and path (e.g. limited to `/api/v1`) | Decide in design |
| — | Exact question wording and the "not sure" option | Copy approval |
