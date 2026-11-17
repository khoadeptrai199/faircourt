# F02 design — Public session page + frontend foundation

Status: `WAITING_APPROVAL` (2026-11-17). Blocked by OD-01, OD-02, OD-13 and copy approval.

## 1. Summary

First vertical slice through every layer: browser → Vite proxy → security → controller → read query → PostgreSQL → page. The backend part is one read-only endpoint. The frontend part also builds the foundation later pages reuse: router, API client (GET), typed errors, the `useAsync` page-state hook, time formatting, the copy module and five base components.

## 2. Blueprint references

- §3: "View public session page (time, venue, number of courts, basic status) | Anyone: Yes".
- §11: "A random public code opens a read-only session summary. It exposes no roster, skill bands, matches or guest tokens."
- §13: "`GET /public/sessions/{publicCode}` | Anyone | Public title, venue, localizable times, court count, status; no names or match IDs."
- §16: "`/s/:publicCode` | Public summary | Hero with time/venue, court count, one Join button | Centered card, max width ~720px | Unknown code → friendly 404; closed → read-only".
- §12: "display times in `Asia/Ho_Chi_Minh` while storing UTC instants."
- §6: Public session page primary action `Join session`.

## 3. API contract

| Item | Value |
|---|---|
| Route | `GET /api/v1/public/sessions/{publicCode}` |
| Caller | anyone |
| Identity source | none |
| Access check | none (route `permitAll` for GET only) |
| Request | path `publicCode`: trimmed, upper-cased, 1–20 chars of `A–Z0–9`; anything else → 404 without a DB query |
| Response | `200 {"title":"…","venue":"…","startsAt":"2026-11-17T11:00:00Z","endsAt":"2026-11-17T14:00:00Z","courtCount":2,"state":"OPEN"}` |
| Allowed response keys | `title, venue, startsAt, endsAt, courtCount, state` (+ `sessionId` only if OD-02 option a is approved) |
| Errors | 404 `NOT_FOUND` for unknown or malformed code |

Times are ISO-8601 in UTC (`Z`); the page converts them for display. Upper-casing the code lets a typed lowercase code work; codes are generated uppercase (`PublicCodeGenerator`).

## 4. Data and transactions

Read-only single-row query, no transaction needed beyond the default read:

```sql
SELECT title, venue, starts_at, ends_at, court_count, state
FROM play_session WHERE public_code = :code
```

The SQL lists only public columns (AD-02: privacy by construction). No migration. No JPA entity yet; the first entity arrives with the first write (F04).

## 5. Domain rules

- `state` is `OPEN` or `CLOSED` (§12). Closed → the page is read-only (§16). There is no route to close a session (OD-10); tests set `CLOSED` directly in the test DB.

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `sessions/PublicSessionController.java` | new | Thin controller | Validates/normalizes the code, calls the query, 404 if empty |
| `sessions/PublicSessionQuery.java` | new | Read projection (`JdbcClient`) | Returns `Optional<PublicSessionResponse>` |
| `sessions/PublicSessionResponse.java` | new | Audience DTO record | Public audience only; never reused |
| `identity/SecurityConfig.java` | changed | Route table | `GET /api/v1/public/sessions/*` → `permitAll` (§13 caller: anyone) |

## 7. Frontend

### Dependency (OD-01)

Recommended: `react-router`, exact version taken from the npm registry at implementation time, pinned in `package.json` and the lockfile, added to the README version table. If OD-01 chooses the in-house option, `routes/` holds a small path matcher instead and nothing else in this design changes.

### Files

| File | Purpose |
|---|---|
| `src/routes/AppRoutes.tsx` | `/` → `HomePage`, `/s/:publicCode` → `PublicSessionPage`, `*` → not-found `EmptyState` |
| `src/App.tsx` (changed) | Renders the routes instead of `HomePage` directly |
| `src/api/client.ts` | `apiGet<T>(path, signal?)`: same-origin `fetch`, JSON parse, throws `ApiError` on non-2xx. Write helpers are added in F04 without changing GET callers |
| `src/api/errors.ts` | `class ApiError extends Error { status; code; requestId }`; `isApiError()` |
| `src/api/types.ts` | `PublicSession`, `SessionState = 'OPEN' \| 'CLOSED'`, `ApiErrorBody` |
| `src/api/sessions.ts` | `getPublicSession(code, signal)` |
| `src/hooks/useAsync.ts` | `AsyncState<T>` union `loading \| empty \| success \| error`, `reload()`, aborts on unmount via `AbortController` |
| `src/lib/time.ts` | `formatSessionWindow(startsAt, endsAt)` with `Intl.DateTimeFormat` and `timeZone: 'Asia/Ho_Chi_Minh'` |
| `src/lib/status.ts` | `SESSION_STATUS: Record<SessionState, {label, tone}>` (attendance/match states added later) |
| `src/copy/en.ts` | All strings for this page (table below) |
| `src/components/Button.tsx` | Primary/secondary, ≥ 44px high, disabled state, focus ring |
| `src/components/StatusBadge.tsx` | Text + color, never color alone |
| `src/components/EmptyState.tsx` | Title, body, optional action |
| `src/components/ErrorBanner.tsx` | Message + optional Retry; shows `requestId` small for support |
| `src/features/public-session/PublicSessionPage.tsx` | Reads `:publicCode`, `useAsync(getPublicSession)`, exhaustive `switch` on state |
| `src/features/public-session/SessionHero.tsx` | Title, venue, time window, court count, status badge, Join action |

### States

| State | Shows |
|---|---|
| loading | Hero skeleton or loading text |
| success, `OPEN` | `SessionHero` + Join session button (behavior per OD-13) |
| success, `CLOSED` | `SessionHero` + closed note; no Join |
| 404 | `EmptyState` "Session not found" + link to home |
| other error | `ErrorBanner` + Retry |

### Layout

Phone (< 768px): full-width hero card, 16px padding, page title 28px, body 16px, Join button full width. Desktop: centered card, max width 720px, 24px padding (§16, tokens from `tokens.css`). Existing `HomePage` stays as is.

### Copy

| Key | Text | Source | Approved |
|---|---|---|---|
| `publicSession.join` | Join session | §6 | yes (blueprint) |
| `publicSession.courts` | `{n} courts` / `1 court` | NEW | |
| `publicSession.statusOpen` | Open | NEW | |
| `publicSession.statusClosed` | Closed | NEW | |
| `publicSession.closedNote` | This session is closed. | NEW | |
| `publicSession.timeZoneNote` | Vietnam time | NEW | |
| `publicSession.notFoundTitle` | Session not found | NEW (§16 "friendly 404") | |
| `publicSession.notFoundBody` | Check the link from your host. | NEW | |
| `common.backHome` | Back to home | NEW | |
| `common.loading` | Loading… | NEW | |
| `common.errorTitle` | Something went wrong. | NEW | |
| `common.retry` | Try again | NEW | |
| `common.comingSoon` | Coming soon | existing home page copy | yes (2026-11-12) |

Time format example (NEW): `Tue 17 Nov · 18:00–21:00`.

## 8. Security and privacy checklist

- [x] No identity needed; the public code never authenticates anyone (§3)
- [x] SQL selects only public columns; response checked against an allowlist
- [x] GET only; no write route opened
- [x] No cookies required for this page

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01 | Seeded session code → 200; keys ⊆ allowlist; values equal the row; times end in `Z` | `PublicSessionIT` |
| TC-02 | AC-02 | Unknown code, 21-char code, code with symbols → 404 `NOT_FOUND` body | `PublicSessionIT` |
| TC-03 | AC-01 | Lowercase version of a real code → 200 | `PublicSessionIT` |
| TC-04 | AC-04 | Row set to `CLOSED` → `state: "CLOSED"` | `PublicSessionIT` |
| TC-05 | AC-07 | Anonymous GET → 200; anonymous POST to the same path → 401/403 (not opened) | `AccessMatrixIT` |
| TC-06 | AC-03, AC-05, AC-06 | Browser at 320, 375 and 1280px: open, closed, not-found, backend stopped | visual (record result or `NOT VERIFIED`) |
| TC-07 | AC-07 | Pipeline: spotless, `mvn verify`, Prettier check, `npm run build` | pipeline |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| OD-01 | Router | react-router / in-house | react-router |
| OD-02 | `sessionId` in this response | a / b / c (ROADMAP §5) | a |
| OD-13 | Join before F04 | disabled + "Coming soon" / hidden | disabled + "Coming soon" |
| — | Copy table | approve / edit | — |

## 11. Out of scope

- Join flow → F04 (guest), F05 (member)
- Bottom navigation `Tonight` / `My game` / `Manage` → F16
- Home page code entry field → not in the blueprint; not planned
