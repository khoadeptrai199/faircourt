# F14 — Host skill assessment

| Field | Value |
|---|---|
| Sprint | S05 Player loop |
| Status | `BACKLOG` |
| Blueprint | §4 (observation after 2–3 games; `SELF_REPORTED`, `OBSERVED`, `CONFIRMED`; no automatic downgrade), §12 (`skill_assessment`, `player_profile.current_band`, `band_status`), §13 (`POST /host/sessions/{sessionId}/assessments`) |
| Depends on | F06 |
| Waiting for | — |
| Suggested branch | `feature/host-assessment` |
| Last updated | 2026-11-17 |

## Goal

The host records a short private observation for a player in this session and confirms or adjusts their band; the next suggestion uses the new band.

## Scope

- In: POST with target attendance, `band` 1–4, status `OBSERVED` or `CONFIRMED`, optional `note` ≤ 300; one transaction inserting `skill_assessment` (source HOST, reviewer = host) and updating the profile; board action.
- Out: player-facing profile and review requests (§6 "Player profile — later"); automatic band changes from results (§4 forbids).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Host → 201; profile band and status updated; board shows them | IT |
| AC-02 | Non-host → 403; target not in this session → 404 | `AccessMatrixIT` |
| AC-03 | Band outside 1–4, note > 300 → 400 | IT |
| AC-04 | Note and band never appear in public or `/me` responses | `JsonAllowlist` (existing tests stay green) |

## Design notes

- `players/SkillAssessmentService`, `players/HostAssessmentController`. Can be pulled into S03 if there is room (depends only on F06).
