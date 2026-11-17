# S04 — Match loop

| Field | Value |
|---|---|
| Status | `PLANNED` |
| Goal | Host gets explained suggestions, swaps players, confirms a court, starts and finishes it; the next suggestion respects who waited |
| Gate | §17 step 3: "correct DB state and `409` on an unavailable player/court; no partial match rows". §17 checklist: "Suggestions do not persist; confirm persists exactly one four-player, two-team planned match"; "Court and player cannot be assigned twice while unfinished; finish/cancel releases both"; "Second round accounts for time spent waiting; host can always override a suggestion" |
| Blueprint | §5, §11, §12, §13, §14, §15, §16 |
| Started / finished | — |

## Features

| ID | Feature | Status |
|---|---|---|
| F09 | [Match suggestions](match-suggestions/README.md) | `BACKLOG` |
| F10 | [Match confirm + tap-to-swap](match-confirm/README.md) | `BACKLOG` |
| F11 | [Match start / finish / cancel](match-lifecycle/README.md) | `BACKLOG` |

## Why this order

F09 is read-only: it connects the existing pure matcher to real data (snapshot assembler, partner history). F10 is the hardest feature in the MVP (multi-row transaction, row lock, 409 mapping, concurrency test). F11 reuses F10's transaction pattern.

## Demo at sprint end

1. Host taps "Suggest matches": two courts with reason tags; DB row counts unchanged.
2. Host swaps the guest into Court 1, confirms → match `PLANNED`; tries to confirm a stale suggestion → "This lineup changed. Refresh suggestions".
3. Host starts and finishes Court 1; the next suggestion prefers the players who sat out.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|

## Notes and retro
