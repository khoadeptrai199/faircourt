# F17 — CI pipeline

| Field | Value |
|---|---|
| Sprint | S06 Acceptance (can be pulled earlier; depends only on F01) |
| Status | `BACKLOG` |
| Blueprint | §7 ("GitHub Actions for build/tests when repository exists"), §17 ("CI runs backend tests and frontend build") |
| Depends on | F01 |
| Waiting for | OD-09 (approve the workflow file) |
| Suggested branch | `feature/F17-ci-pipeline` |
| Last updated | 2026-11-17 |

## Goal

Every push and pull request runs the same checks as the local pre-commit pipeline: backend `mvn verify` (incl. Spotless check and PostgreSQL integration tests) and frontend Prettier check + build.

## Scope

- In: one GitHub Actions workflow; PostgreSQL service container with a `faircourt_test` database; pinned action versions and images (no floating tags, §0); README badge or note.
- Out: deployment, secrets, release automation.

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | A push runs backend verify and frontend checks; a formatting error fails the run | GitHub Actions run link |
| AC-02 | Integration tests use a database whose name ends in `_test` | workflow file + run log |
| AC-03 | All images and actions pinned to exact versions | review |
