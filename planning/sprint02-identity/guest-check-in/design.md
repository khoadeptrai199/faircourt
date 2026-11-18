# F04 design — Guest check-in + guest cookie

Status: `WAITING_APPROVAL` (written 2026-11-18). Waits for the owner's answers to D-1…D-10 (section 10), including 4 new internal routes and the copy table. Security standard: OWASP Session Management and Authentication cheat sheets (the guest cookie is a session credential) plus the lessons of Account login and Password reset. Naming: the play event is a **game** (OD-22).

## 1. Summary

A visitor opens the public link `/g/<code>`, presses **Join game**, enters a display name, answers four questions about observable play (or says "not sure"), and sees "Waiting for host approval". The browser receives a private, HttpOnly **guest cookie** that proves "this browser is this guest of this game"; the public code never proves anything.

Three services each own a piece, so one user action writes in three places (first saga in the project, AD-16):

| Service | Owns | Does |
|---|---|---|
| player-service | `player_profile`, `skill_assessment` | Creates the guest profile and the self-reported assessment; maps the four answers to a provisional band |
| playmatch-service | `game`, `attendance` | Checks the game (exists, open), creates the `PENDING` attendance, **orchestrates** the other two |
| auth-service | `guest_access` | Issues and resolves the guest credential (32 random bytes, only SHA-256 stored) |

The gateway is the only part that touches cookies: it reads the guest cookie, asks auth-service who it belongs to, and sets the cookie from the credential that playmatch returns.

## 2. References

- §3, §11: guest joins from the public page; `PENDING` until host approval; never use a nickname or the shared code alone as identity.
- §12: "generate 32 random bytes, send it only in an HttpOnly, SameSite=Lax guest cookie, store SHA-256 of the secret in `guest_access`, and expire it shortly after the session… scoped to its recorded session/attendance… one browser has one active guest identity; joining again with a valid guest cookie returns the existing attendance."
- §13: `POST /public/sessions/{publicCode}/guests` (here `/public/games/{publicCode}/guests`, OD-22): body `{"displayName","answers":{serve,rally,movement,positioning}}`; `201` with body `{"attendanceState":"PENDING","message":"Waiting for host approval"}`; all four answers `null` allowed → band `null`; otherwise total 0–3 → 1, 4–6 → 2, 7–9 → 3, 10–12 → 4; "all four scored or all unknown".
- §4, §6, §16: observable-behavior prompts and anchors; four-step checklist with large answer cards and a progress label; `/s/:publicCode/join` (here `/g/:publicCode/join`).
- AD-15 (internal routes), AD-16 (saga with compensation), system-design §7.

## 3. API contract

### Public route (gateway, answered by the gateway itself)

| Item | Value |
|---|---|
| Route | `POST /api/v1/public/games/{publicCode}/guests` |
| Caller | anyone; CSRF token required; JSON only; body capped at 4 KB |
| Request | `{"displayName":"Mai","answers":{"serve":2,"rally":1,"movement":1,"positioning":1}}` (all four 0–3, or `"answers":null` / all four `null`); `displayName` trimmed, 1–80 characters, no control characters |
| Response (new guest) | `201 {"attendanceState":"PENDING","message":"Waiting for host approval"}` + `Set-Cookie` guest cookie |
| Response (same browser again) | `200` with the existing attendance: `{"attendanceState":"<state now>","message":…}`; no new rows, no new cookie |
| Allowed response keys | `attendanceState`, `message` only; no ids, no names, no band |
| Errors | 400 `VALIDATION_FAILED` (name, partial answers, values outside 0–3); 403 `CSRF_TOKEN_INVALID`; 404 `NOT_FOUND` (unknown code); 409 `STATE_CONFLICT` (game `CLOSED`); 503 `SERVICE_UNAVAILABLE` |

`message` is "Waiting for host approval" for `PENDING` (blueprint text) and `null` for other states; the page chooses its own copy from `attendanceState`.

### Guest cookie

| Item | Value (decisions D-1, D-2) |
|---|---|
| Name | `fc_guest` (on HTTPS `__Host-fc_guest`, set by `GUEST_COOKIE_NAME`) |
| Value | 32 random bytes, base64url (43 characters) |
| Attributes | HttpOnly, SameSite=Lax, `Secure` from `COOKIE_SECURE`, Path=`/`, no Domain, Max-Age = until `expires_at` |
| Lifetime | `max(game.endsAt, now) + 12 h` |
| Stored | only the 64-hex SHA-256 in `auth.guest_access.token_hash` |

### Internal routes (never routed publicly; need approval, AD-15)

| Service | Route | Called by | Request → response |
|---|---|---|---|
| playmatch | `POST /internal/v1/games/by-code/{publicCode}/guest-check-ins` | gateway | `{displayName, answers, existingAttendanceId\|null}` → `{attendanceState, created: bool, credential: {token, expiresAt}\|null}`; 404 / 409 / 503. When `existingAttendanceId` is a live attendance of this game: `created=false`, no credential |
| auth | `POST /internal/v1/auth/guest-credentials` | playmatch | `{gameId, attendanceId, expiresAt}` → `{token, expiresAt}` (the raw token exists only in this answer) |
| auth | `POST /internal/v1/auth/guest-credentials/resolve` | gateway | `{token}` → `200 {gameId, attendanceId}` or `401` (unknown, expired or revoked: one answer) |
| player | `POST /internal/v1/players/guests` | playmatch | `{displayName, gameId, answers\|null}` → `{playerId}`; 400 for partial or out-of-range answers |

## 4. Data and transactions

No migration: the tables exist (`player_profile` kind `GUEST` with `user_id` null, `skill_assessment` source `SELF` with `answers` JSONB and `band`, `attendance` state `PENDING`, `guest_access`).

The orchestration in playmatch (design D-4):

1. Look up the game by code (404), refuse `CLOSED` (409). If `existingAttendanceId` is given and belongs to this game and is not `LEFT`: return it (no writes).
2. Call player-service: one local transaction creates the profile and the self assessment (band from the answers). Down → 503, nothing written anywhere.
3. One local transaction in playmatch: insert `attendance` (`PENDING`, `display_name` copied, `player_id` = the new profile).
4. Call auth-service: issue the credential for `(gameId, attendanceId, expiresAt)`. If this fails: **compensation**: set the attendance to `LEFT` (local transaction) and answer 503.
5. Return the credential to the gateway, which sets the cookie.

Failure cases the owner should know (all recorded, tested where a test is possible):

| Failure | Result |
|---|---|
| player-service down | 503, nothing created |
| attendance insert fails after the profile was created | a guest profile with no attendance remains (harmless, no data about anyone but the typed name and answers); logged |
| auth-service fails after the attendance exists | attendance marked `LEFT`, 503; if even that write fails the guest stays `PENDING` without a cookie and the host can reject the entry |
| the answer is lost on the way to the browser (cookie never set) | the guest checks in again and may appear twice; the host rejects the duplicate (OD-15) |
| two parallel requests from one browser before the first answer | two entries possible (the cookie does not exist yet); the form disables its button while saving |

## 5. Domain rules

- **GuestAnswers** (value object, player-service): all four in 0–3, or all four `null` (or the whole `answers` `null`); anything else is 400. Total 0–3 → band 1, 4–6 → 2, 7–9 → 3, 10–12 → 4; unknown → band `null` (host sees "Unknown / provisional"). It is a pilot heuristic, not a rating; status `SELF_REPORTED`.
- The name is not unique and never merges guests across games or with accounts (§7).
- One browser = one active guest identity per game: a valid cookie for this game returns the existing attendance.
- A cookie for another game, an expired, revoked or unknown cookie is ignored at check-in (the person simply checks in as a new guest); the old cookie is replaced when the new one is set.
- A guest never gets an account, never reads other people's data, and is scoped to one game and one attendance (§3, §12).
- The credential is revoked when the attendance becomes `LEFT` (Attendance management feature) and ends at `expires_at`.

## 6. Backend components

| File | Service | Notes |
|---|---|---|
| `GuestCheckInController`, `GuestCheckInService`, `PlayerClient`, `AuthClient` (new, `com.faircourt.playmatch.guest`) | playmatch | Orchestration; short timeouts (2 s / 5 s), no retry of writes (AD-15); internal route; security rule |
| `GuestProfileController`, `GuestProfileService`, `GuestAnswers`, `AnswerBand` (`com.faircourt.player.guest`) | player | Profile + assessment in one transaction; band mapping |
| `GuestCredentialController`, `GuestCredentialService` (`com.faircourt.auth.guest`) | auth | `SecureRandom` 32 bytes; SHA-256 hex; resolve by hash; revoke any older active credential of the same attendance (the unique index allows one) |
| `GuestController`, `GuestCookie`, `PlaymatchClient`, `AuthClient.resolveGuest` (`com.faircourt.gateway.guest`) | gateway | Public route (a `GatewayRoute.local` line, PUBLIC, POST); reads the cookie, resolves it, calls playmatch, sets the cookie; body cap added to `RequestBodyLimitFilter` |
| `AccountController.logout` | gateway | **Changed:** logout now expires only the account session cookie and sends `Clear-Site-Data: "cache"`; the old `"cookies"` directive would also delete the guest cookie of the same browser |
| application settings | all | `faircourt.services.playmatch-url` (gateway already has it), `player-url`, `auth-url` for playmatch; `GUEST_COOKIE_NAME`, guest cookie grace (12 h) |

## 7. Frontend

### Files

| File | Purpose |
|---|---|
| `src/features/join/JoinPage.tsx` | Route `/g/:publicCode/join`: name step, four question steps, review is not needed; result panel |
| `src/features/join/QuestionStep.tsx` | One question with four large answer cards and a progress label |
| `src/api/guests.ts` | `checkInAsGuest(code, {displayName, answers})` |
| `src/features/public-game/GameHero.tsx` | The disabled "Join game" button becomes a link to `/g/<code>/join` for open games (OD-13 was "until check-in ships") |
| `src/routes/AppRoutes.tsx`, `src/copy/en.ts` | Route and strings |

States: idle → saving (buttons disabled) → success (pending panel) / error (400 → in-place message, 404 → game not found, 409 → "game is closed", 503 → shared banner with Try again). The guest cookie is never visible to JavaScript. Large cards ≥ 56 px, works at 320 px, keyboard and screen-reader friendly (radio group per question, visible focus).

Flow: step 0 name → steps 1–4 questions (Back / Next, "Question n of 4") → submit on the last answer → panel "Waiting for host approval". "I'm not sure about my level" on step 1 skips the four questions and sends all `null`. Revisiting the join page shows the form again (the status page comes with the own-attendance feature); submitting again returns the existing entry.

### Copy

| Key | Text | Source | Approved |
|---|---|---|---|
| `join.title` | Join game | blueprint §6 button text, reused | |
| `join.nameLabel` | Your name | NEW | |
| `join.nameHelp` | The host sees this name. | NEW | |
| `join.progress` | Question {n} of 4 | NEW (§16 "progress label") | |
| `join.next` / `join.back` | Next / Back | NEW | |
| `join.q.serve` | How often does your serve land in? | blueprint §4 | |
| `join.q.rally` | Can you sustain a rally with players at your usual level? | blueprint §4 | |
| `join.q.movement` | How comfortable are you moving backward and recovering? | blueprint §4 | |
| `join.q.positioning` | When your partner is at the net, do you know where to cover? | blueprint §4 | |
| `join.a.0…3` | Still learning / Sometimes / Usually / Consistently | blueprint §4 anchors (0, 1, 2, 3) | |
| `join.notSure` | I'm not sure about my level | NEW (blueprint allows unknown answers) | |
| `join.submit` | Send | NEW | |
| `join.pending` | Waiting for host approval | blueprint §13 | |
| `join.pendingBody` | The host will confirm you. Keep your phone handy. | NEW | |
| `join.nameRequired` | Enter your name. | NEW | |
| `join.closed` | This game is closed. | reuses `publicGame.closedNote` | yes |
| `join.error` | Something went wrong. | reuses `common.errorTitle` | yes |

The question order is blueprint order (serve, rally, then the other two as the §13 example); the exact order of questions 3 and 4 is part of the approval.

## 8. Security and privacy checklist

- [ ] Raw token only in the issue answer and the cookie; DB, logs, errors and bodies never hold it
- [ ] 32 random bytes from `SecureRandom`; hash compared in constant time; resolve answers one 401 for unknown, expired and revoked
- [ ] Cookie HttpOnly, SameSite=Lax, `Secure` from env, generic name, Path=/, expires with the credential; new cookie replaces the old
- [ ] The cookie is scoped to one game and one attendance; a cookie of another game is ignored
- [ ] CSRF token and JSON-only; body ≤ 4 KB; display name rules; control characters refused
- [ ] The cookie never reaches playmatch/player (HeaderSanitizer strips cookies; the gateway sends ids as explicit internal arguments)
- [ ] Response has no ids, band or other people's data; `Cache-Control: no-store`
- [ ] No name, answer or token in logs; request log has no body
- [ ] Logout no longer clears the guest cookie
- [ ] Abuse: flooding the host with fake guests is possible (D-6); the host can reject entries

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-03 | Band boundaries 3/4, 6/7, 9/10; all `null` → `null`; partial / out of range → refused | unit `GuestAnswersTest` |
| TC-02 | AC-01, AC-03 | Profile (GUEST, no user) + assessment (SELF, answers JSON, band) created in one transaction | player `GuestProfileIT` |
| TC-03 | AC-02 | Issue: DB holds only the 64-hex hash; raw token absent from DB and logs; resolve ok / unknown / expired / revoked; one active per attendance; constant answer | auth `GuestCredentialIT` |
| TC-04 | AC-01, AC-06, AC-09 | Orchestration with stubs: success; unknown code 404; closed 409; player down → 503 and no rows; auth down → attendance `LEFT` + 503; existing attendance returned without writes; other game's attendance id ignored | playmatch `GuestCheckInIT` |
| TC-05 | AC-01, AC-07 | Gateway: 201, cookie flags and name, Max-Age, body keys allowlist, `no-store`, CSRF 403, form post refused, 4 KB cap, 5xx mapping | gateway `GuestCheckInIT` |
| TC-06 | AC-04 | Same browser again → 200, same state, no new cookie, no new rows; expired/unknown cookie → new check-in | gateway IT + stubs |
| TC-07 | AC-05 | Name trimmed, 1–80, control characters, 81 chars → 400; two guests with the same name allowed | IT |
| TC-08 | — | Logout keeps the guest cookie, still ends the account session | gateway IT |
| TC-09 | AC-08 | Join page at 320 / 375 / 1280px, all steps, errors, pending panel | visual (owner) |
| TC-10 | — | Live check on the Docker stack: real check-in, row counts, cookie, no token in logs | manual script |
| TC-11 | — | Pipeline | pipeline |

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | Cookie lifetime (OD-06) | game end + N hours | `max(endsAt, now) + 12 h` |
| D-2 | Cookie name | `fc_guest` / other | `fc_guest`; `__Host-fc_guest` on HTTPS |
| D-3 | Orchestration by playmatch and the 4 internal routes of section 3 (new calls between services) | approve / change | approve |
| D-4 | The saga steps and the failure cases of section 4 (orphan profile, duplicates after a lost answer are accepted) | approve / change | approve |
| D-5 | Same browser joining again: `200` with the current state (not `201`); the join page does not know the cookie until the status page exists | approve / change | approve |
| D-6 | Flooding by fake guests (anonymous, no limit; the owner rule forbids an attendance cap) | (a) nothing now, the host rejects entries, per-source limits later (L-26) · (b) cap the number of `PENDING` guests per game · (c) global rate limit on this route | (a). Honest risk: someone who has the public link can add many fake `PENDING` names |
| D-7 | Copy table incl. the four questions and anchors (section 7) | approve / edit | — |
| D-8 | Identity token for services stays deferred; here the gateway passes ids to internal routes itself (no browser id is ever trusted) | approve / change | approve |
| D-9 | Name rules: trimmed, 1–80 characters, no control characters, duplicates allowed | approve / change | approve |
| D-10 | Logout change (clears only the account cookie) | approve / change | approve |

## 11. Out of scope

- The guest reading their own status (`/my/games/:gameId`) → Own attendance status
- Host approving or rejecting guests → Attendance management (OD-15)
- Revoking the credential when a guest leaves → Attendance management
- Cross-device recovery, host-assisted check-in (L-09), per-source limits (L-26)
