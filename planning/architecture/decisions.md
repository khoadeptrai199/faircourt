# Architecture decision log

Status values: `PROPOSED` → `ACCEPTED` (owner, with date) or `REJECTED`. A later decision may `SUPERSEDE` an earlier one; never delete a row.
Details for each decision are in [system-design.md](system-design.md). Product open questions are `OD-xx` in [ROADMAP.md](../ROADMAP.md#5-open-decisions).

| ID | Decision | Status | Affects | Rationale | Design section |
|---|---|---|---|---|---|
| AD-01 | Keep the §15 module packages; a module writes only its own tables via its own service; dependencies point `matches → players → sessions → identity → common`, `matching` depends on nothing | PROPOSED | all backend features | Clear ownership without microservices; no cycles | §3 |
| AD-02 | CQRS-lite: JPA entities for writes; `JdbcClient` read queries that return per-audience response records | PROPOSED | F02, F05, F06, F09, F12 | Privacy by construction; no N+1; entities stay in persistence (§15) | §6 |
| AD-03 | State transitions defined in one enum per state machine; entity methods use it; illegal → 409 `STATE_CONFLICT` | PROPOSED | F07, F10, F11 | §11 rules in one place | §7 |
| AD-04 | One `ApiException` + `ErrorCode` catalog; `GlobalExceptionHandler` plus JSON security entry point / access-denied handler; `requestId` per request in MDC and error body | PROPOSED | F01 and every endpoint | One error shape (§13); frontend maps codes to copy | §9 |
| AD-05 | Caller identity as a sealed `CallerIdentity` (Anonymous / Account / Guest) resolved once per request; guest cookie authenticated by a filter on guest-capable paths | PROPOSED | F03, F04, F05, F12, F13 | Exhaustive handling; identity never from client IDs (§13) | §5 |
| AD-06 | CSRF stays on; token served by `GET /api/v1/auth/csrf` (body: header name + token); the client keeps it in memory and refetches after login/logout. Token repository choice is decided in the F01 design (D-1) | PROPOSED | F01, F03, F04 | §0 requires a bootstrap route and documented header | §10, F01 design |
| AD-07 | Match commands: one `@Transactional` method each; confirm locks the `play_session` row (`FOR UPDATE`); unique-index violations roll back and map to 409 | PROPOSED | F10, F11 | §12, §15 integrity | §8 |
| AD-08 | Frontend adds `hooks/`, `lib/`, `copy/` folders to the §16 layout; `api/client.ts` is the only `fetch` caller; discriminated-union async state; exhaustive status maps | PROPOSED | F02 and every page | §16 page-state matrix enforced by types; one place for copy review | §10 |
| AD-09 | Privacy tests: one `AccessMatrixIT` (rows per route × caller) and JSON key allowlists for every non-host response | PROPOSED | every endpoint | Mirrors the §3 permission table; catches leaks when DTOs change | §12 |
| AD-10 | `MatchSuggester` stays one concrete pure class; a `MatchingPolicy` strategy interface is introduced only when a second policy is approved | PROPOSED | F09, later L-06 | Avoid speculative abstraction; the snapshot is already the seam | §11, §13 |
