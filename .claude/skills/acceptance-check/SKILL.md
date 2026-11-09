---
name: acceptance-check
description: Run the FairCourt end-to-end acceptance demonstration from blueprint §0 (six steps) and the §17 scope checklist against the running local stack, recording PASS / FAIL / NOT VERIFIED with evidence for each item. Use before calling any milestone done.
argument-hint: "[all | step <1-6> | checklist]"
---

# /acceptance-check

Scope: **$ARGUMENTS** (default: `all`)

Prerequisite: stack running and healthy (use `/run-local status`). If not running, stop and report.

## Ground rules

- A result is **PASS** only with evidence captured in this run (HTTP status + relevant body excerpt, test output, or screenshot). Otherwise **NOT VERIFIED**.
- Never delete data to make a check pass. Step 6 restarts with `docker compose down` (no `-v`).
- Use separate cookie jars to simulate separate browsers, e.g. `curl -c/-b /tmp/<scratch>/host.txt`, `guest.txt`, `anon` (no jar), `other.txt`.
- For writes, first `GET /api/v1/auth/csrf` with that jar and send the CSRF header **exactly as documented in README.md**. If README does not document it, report that as a FAIL of the §0 contract.
- Public code and demo credentials come from README / seed output (`host@faircourt.local` / `FairCourtDemo!1`, §0). Do not invent IDs.

## §0 end-to-end steps

| # | Check | Evidence to capture |
|---|---|---|
| 1 | Anonymous `GET /api/v1/public/sessions/{code}` shows time, venue, 2 courts; response has **no** names, bands, attendance/match IDs, tokens | status, full JSON, grep for any name/UUID fields |
| 2 | Host logs in; board shows the seeded READY players. Separate jar joins as guest via `POST /public/sessions/{code}/guests`; response says `Waiting for host approval`, sets HttpOnly cookie | board count, guest response body, `Set-Cookie` flags |
| 3 | Host approves guest, requests suggestions (nothing persisted), optionally swaps guest in, confirms one 4-player match on Court 1 → `201` `PLANNED`. Guest `/me` shows **only** own `matchNo`, `courtNo: 1`, `PLANNED` | confirm response, guest `/me` body |
| 4 | Host starts then finishes the match; guest `/me` status changes accordingly; host can suggest again; the four are never in two unfinished matches | state transitions, second suggestion |
| 5 | Anonymous and a different signed-in account cannot read the guest's assignment or call host write routes (expect 401/403/404 per §13). Stale/invalid confirm → `409` with `{"code","message","requestId"}` and no partial rows | status codes, DB row count before/after |
| 6 | `docker compose down` then `up -d` (no `-v`): session and finished match still exist; host and guest pages load; guest cookie still valid | before/after queries |

The "different signed-in account" in step 5 requires a second provisioned account. If the seed has none, report NOT VERIFIED and ask the owner — do not add an account without approval.

## Automated checks (§0)

Run and record exit status of: backend tests (matcher unit + PostgreSQL integration + authorization), frontend build, and any browser/HTTP smoke test present in the repo. Check 375px phone and desktop layouts visually only if browser tooling is available; otherwise NOT VERIFIED.

## §17 scope checklist

Evaluate each line of the "Scope acceptance checklist" at the end of the blueprint, reusing evidence from above.

## Report

```text
Machine: <os/arch>
| Item | Result | Evidence |
|------|--------|----------|
...
Commands executed: <list>
Failures to fix: <list>
Not verified (and why): <list>
```

Do not call the work done while any FAIL remains (§0).
