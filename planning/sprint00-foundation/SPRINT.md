# S00 — Foundation

| Field | Value |
|---|---|
| Status | `DONE` |
| Goal | The stack starts with one command, the V1 schema and demo seed exist, and the pure matcher proposes two fair courts |
| Gate | §17 step 1: "deterministic two-court proposal, no duplicate players" |
| Blueprint | §0, §10, §12, §14, §17 |
| Started / finished | 2026-11-09 / 2026-11-12 (from README verification log) |

## Features

| ID | Feature | Status |
|---|---|---|
| F00 | [Walking skeleton](walking-skeleton/README.md) | `DONE` |

## Gate evidence

Taken from the README verification log; not re-run on 2026-11-17.

| Gate item | Result | Evidence |
|---|---|---|
| Deterministic two-court proposal | `PASS` | `MatchSuggesterTest.seededDemoGroupFormsTwoSimilarBalancedCourts`, `sameSnapshotGivesSameResultRegardlessOfInputOrder` — `BUILD SUCCESS` on Mac 2026-11-09 and WSL 2026-11-12 |
| No duplicate players | `PASS` | `simultaneousCourtsNeverReuseAnAttendanceId`; DB backstop in `DatabaseSchemaIT.databaseRejectsPlayerInTwoUnreleasedMatches` |
| Stack healthy with one command | `PASS` | README log: all 3 services healthy, health `UP` (Mac and WSL) |

## Notes and retro

- This sprint pre-dates the planning folder, so F00 has no design/implement files.
- Still open from S00: 375px / desktop visual check of the home page; Mac check of the 2026-11-12 changes (README log).
