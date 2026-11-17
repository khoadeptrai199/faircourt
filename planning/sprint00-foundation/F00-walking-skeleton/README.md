# F00 — Walking skeleton

| Field | Value |
|---|---|
| Sprint | S00 Foundation |
| Status | `DONE` |
| Blueprint | §0, §10, §12, §14, §17 step 1 |
| Depends on | — |
| Waiting for | — |
| Last updated | 2026-11-17 |

This feature was delivered before the planning folder existed (commits `447b491`, `93f654d`, `b07a4b2`, `0b67fb0`), so there are no design/implement/review files. This card records what exists so the board is complete.

## Delivered (repository scan, 2026-11-17)

| Area | Files |
|---|---|
| Docker stack | `compose.yaml` (postgres, backend, frontend; `test` profile `backend-test`; `tools` profile `backend-format`), `compose.backend-dev.yaml`, `backend/Dockerfile` (deps/test/dev/build/runtime stages), `frontend/Dockerfile`, `postgres/init/01-create-test-db.sql` |
| Schema | `backend/src/main/resources/db/migration/V1__initial_schema.sql` (verbatim §12) |
| Seed | `common/LocalDemoSeeder.java`: local profile only, idempotent, one transaction; host + 8 READY members (bands 1,1,2,2,3,3,4,4) + one OPEN 2-court session |
| Matcher | `matching/` package: `MatchSuggester`, `BoardSnapshot`, `Proposal`, `ReasonCode`, `Warning`, `SuggestionResult` |
| Security baseline | `identity/SecurityConfig.java`: health public, all else `denyAll`, CSRF default on, delegating `PasswordEncoder` |
| Tests | `MatchSuggesterTest` (13), `DatabaseSchemaIT` (4), `FaircourtBackendApplicationTests` (1) |
| Frontend | Home page `/` (`features/home/HomePage.tsx`), tokens in `styles/tokens.css` |

## Acceptance criteria

| ID | Criterion | Result | Evidence |
|---|---|---|---|
| AC-01 | Deterministic two-court proposal for the seed | `PASS` | README verification log, Mac 2026-11-09 and WSL 2026-11-12: `BUILD SUCCESS` 13 + 1 + 4 tests |
| AC-02 | No attendance ID reused across simultaneous courts | `PASS` | `simultaneousCourtsNeverReuseAnAttendanceId` (same runs) |
| AC-03 | Seed idempotent across restarts | `PASS` | `DatabaseSchemaIT.seedIsIdempotent`; README log `down` + `up` "already present" (Mac) |
| AC-04 | One-command start, health UP | `PASS` | README log (Mac and WSL) |
| AC-05 | Home page at 375px and desktop | `NOT VERIFIED` | README "Known limitations" |

These results are copied from the README verification log; nothing was re-run when this card was written.

## Carried forward

- AC-05 visual check → F16.
- Mac verification of the 2026-11-12 changes (debug port, dev mode, polling flag, home page) → next time the owner works on the Mac.
