# S03 — Host board

| Field | Value |
|---|---|
| Status | `ACTIVE` |
| Goal | The session's host sees the full board (Pending, Ready, Assigned, Playing, Resting, Left; courts), approves guests, moves players between Ready/Resting/Left, and can create a new session |
| Gate | §17 checklist line 2: "Host can create a session, approve a guest and see the full board; another account gets 403 on host routes" (completes §17 step 2 "host guest approval") |
| Blueprint | §3, §6, §11, §13, §15, §16 |
| Started / finished | 2026-11-25 / — |

## Features

| ID | Feature | Status |
|---|---|---|
| F06 | [Host board](host-board/README.md) | `DONE` |
| F07 | [Attendance management](attendance-management/README.md) | `DONE` |
| F08 | [Create game](create-game/README.md) | `DONE` |

## Why this order

F06 introduces `AccessPolicy` and the host read model with no writes. F07 adds the first state-machine writes (single row). F08 is independent and simple; it can move earlier if a sprint has room.

## Demo at sprint end

1. Host opens the board: 8 Ready members + 1 Pending guest; two free courts.
2. Host approves the guest → Ready; sets one player Resting and back.
3. `player1` (not host) calls the board → 403; the guest calls it → 403.
4. Host creates a second session (API or form, per OD-05) and opens its board.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|

## Notes and retro
