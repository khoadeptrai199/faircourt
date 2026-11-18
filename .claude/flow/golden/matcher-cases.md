# Golden cases — match suggester (independent oracle)

Expected results that the agent did **not** compute. `MatchSuggesterTest` (and any future matcher test) must agree with every row. Rules:

- Rows come from the owner, or are quoted from the blueprint with the section number.
- The agent never changes an expected value. If a test disagrees with a row, the disagreement goes to the owner as a finding.
- Doubles only for now: singles matching (per-game choice, owner decision 2026-11-17) has no blueprint rule yet; its rows wait for that design.

## A. Stated by the blueprint (§14 "Boundary tests", §5)

| ID | Given | Expected | Source |
|---|---|---|---|
| G-01 | 4 ready players, all the same band, 1 free court | One proposal with these 4; no `WIDE_SKILL_GAP` | §14 |
| G-02 | 4 ready players with bands `[1,2,3,4]`, 1 free court | One proposal; `WIDE_SKILL_GAP` warning even though team sums can tie (1+4 = 2+3) | §14, §5 example |
| G-03 | 4 ready players, one with unknown band | Unknown counts as band 2 for grouping only; `UNKNOWN_LEVEL` warning | §14 |
| G-04 | 8 ready players: 4 with 0 matches played, 4 with 1; 1 free court | The four 0-match players are selected | §14 |
| G-05 | Fewer than 4 ready players | No proposal; `WAITING_FOR_PLAYERS` | §14 |
| G-06 | 4+ ready players, no free court (occupied court excluded) | No proposal; `NO_FREE_COURT`; an occupied court is never proposed | §14 |
| G-07 | 8 ready players, 2 free courts | Two proposals; no attendance ID appears in both | §14 |
| G-08 | The same snapshot twice | The same proposals in the same order | §14 |
| G-09 | Slots | `slots = 4 × min(freeCourts, floor(readyCount / 4))`; more than 24 ready players is outside v1 | §14 |
| G-10 | Spread | `spread = max(bands) − min(bands)`; `WIDE_SKILL_GAP` exactly when `spread ≥ 2` | §14 |

## B. Numeric cases from the owner

To be filled by the owner: concrete players (band, matches played, ready since, last partners) and the exact courts and teams you expect. The agent leaves this table empty.

| ID | Snapshot | Expected courts and teams | Expected warnings | Author, date |
|---|---|---|---|---|
