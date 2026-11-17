# Project conventions (format and patterns for every task)

One set of naming, structure and pattern rules for the whole repository, so code written in different sessions looks the same. Background and diagrams: `planning/architecture/system-design.md`. Decisions marked `AD-xx` are in `planning/architecture/decisions.md`; while an AD is `PROPOSED`, follow it only inside a feature whose design the owner approved.

When this file and the blueprint differ, the blueprint wins. When this file and an older habit in the code differ, follow this file for new code and list the old spot in the feature's `implement.md` instead of refactoring it unasked.

## Language and docs

- English for code, comments, commit messages, UI copy, README, `API Document/`, `planning/` (owner decision 2026-11-17).
- Dates `YYYY-MM-DD`; timestamps ISO-8601.
- Cite the blueprint as `§<n>` where a rule is not obvious from the code. Do not paste long spec text into comments.
- IDs: features `F<nn>`, acceptance criteria `AC-<nn>` (global form `F04/AC-02`), tasks `T<nn>`, test cases `TC-<nn>`, open decisions `OD-<nn>`, architecture decisions `AD-<nn>`, later backlog `L-<nn>`. Never renumber or reuse an ID.

## Backend (Java 25, Spring Boot 4.1.2)

### Structure (microservices, owner decision 2026-11-17; AD-11…AD-18)

- Services and what they own (AD-11):

  | Service (folder `backend/services/<name>`) | Schema | Tables |
  |---|---|---|
  | `gateway` | — | — (route table, account session, CSRF, guest cookie, request ID) |
  | `auth-service` | `auth` | `app_user`, `guest_access` |
  | `player-service` | `player` | `player_profile`, `skill_assessment` |
  | `playmatch-service` | `playmatch` | `play_session`, `attendance`, `play_match`, `match_player` |
  | `rating-service` (S05) | `rating` | `match_feedback` |

- Put a new class in the service that owns the table it writes. A service never reads or writes another service's schema; it calls that service's `/internal/v1/**` route (AD-12, AD-15). No new service without approval.
- Shared code lives only in `backend/libs/`: `common-web` (error model, request ID, identity token, test support) and `matching` (pure, no Spring/JPA). No entities or business rules in libs (AD-18).
- Java packages: `com.faircourt.<service>.<area>`, e.g. `com.faircourt.playmatch.match`. Flat packages per area (no `controller/`, `service/` sub-packages) until an area exceeds ~15 classes; then split by feature, not by layer.
- Until F18 is implemented, the code is still the single app in `backend/src/` with the old §15 packages (AD-01, superseded); do not add new features there.

### Naming

| Kind | Pattern | Example |
|---|---|---|
| Controller | `<Audience><Resource>Controller` (audience omitted for signed-in callers) | `PublicSessionController`, `HostMatchController`, `AttendanceController` |
| Command service (writes, `@Transactional`) | `<Resource>Service` | `MatchService`, `AttendanceService` |
| Read projection (`JdbcClient`, read-only) | `<View>Query` | `PublicSessionQuery`, `HostBoardQuery`, `MyAttendanceQuery` |
| JPA entity | table name in PascalCase, no suffix | `PlaySession`, `Attendance`, `PlayMatch`, `MatchPlayer` |
| Repository | `<Entity>Repository` | `PlayMatchRepository` |
| Request DTO | `<Action><Resource>Request` record | `ConfirmMatchRequest`, `GuestCheckInRequest` |
| Response DTO | `<Audience><Resource>Response` record | `PublicSessionResponse`, `MyAttendanceResponse`, `HostBoardResponse` |
| Value object | domain noun record | `MatchLineup`, `GuestAnswers` |
| State enum | `<Thing>State`, constants equal to the DB `CHECK` values | `AttendanceState.READY` |

### Rules

- Controllers are thin: validate (`@Valid`), take `CallerIdentity`, call one service/query method, map to the response record. No business rules, no repositories in controllers.
- Never return a JPA entity from a controller. Never reuse a response record for a different audience (§13). Public and player responses are built by `*Query` SQL that selects only allowed columns (AD-02).
- Identity comes only from `CallerIdentity`. The gateway resolves the account session or guest cookie and forwards a signed identity token; a service builds `CallerIdentity` only from that token, never from other headers, path or body (§13, AD-14). IDs in the path or body identify the target resource, never the caller.
- Service-to-service calls: `RestClient` with short timeouts to `/internal/v1/**`; a down dependency → `ApiException(SERVICE_UNAVAILABLE)` (503); writes are never retried automatically (AD-15). Every new internal route is listed in its feature design and approved first.
- A write that touches several services is orchestrated by the service that owns the main record, with an explicit compensation step and a test for the failure path (AD-16).
- Every host route calls `AccessPolicy.requireHost(...)` in the service, in addition to the route rule (§15).
- Expected failures: `throw new ApiException(ErrorCode.X, "safe message")`. No `null` returns for "not found", no `ResponseEntity` error building in controllers, no catching exceptions just to log them (AD-04).
- State changes go through the state enum's transition check (AD-03).
- One `@Transactional` method per command; no `@Transactional` on controllers or queries that write nothing. Locks via repository methods with `@Lock` or `SELECT … FOR UPDATE` (AD-07).
- Time: inject `Clock`; use `Instant` in domain code; never call `Instant.now()` / `LocalDateTime.now()` without the clock. JSON timestamps are ISO-8601 UTC.
- IDs: `UUID.randomUUID()` in the service (§12).
- Logging: SLF4J; never log tokens, password hashes, cookies, or other players' personal data; the `requestId` comes from MDC.
- Records for DTOs and value objects. No Lombok (not a dependency).
- Formatting: Palantir Java Format via Spotless (`docker compose run --rm --build backend-format`). Do not hand-format against it.

### Database

- One PostgreSQL container and database, one schema per service, one shared DB user (AD-12). Each service runs Flyway only on its own schema; migrations live in `services/<name>/src/main/resources/db/migration/`.
- No foreign keys or joins across schemas; a reference to another service's row is a plain UUID column.
- New schema change = new file `V<n>__<snake_case_description>.sql` in the owning service. Never edit an applied migration (`backend-db.md`).
- SQL in `*Query` classes as Java text blocks with named parameters (`:sessionId`), never string concatenation of values.

### Tests

- Unit tests: `<Class>Test.java`, no Spring context, same package as the class.
- Integration tests: `<Feature or Topic>IT.java`, annotated `@IntegrationTest`, against the service's own schema in `faircourt_test` (failsafe runs them in `mvn verify`). Identity is given as a test identity token, not by calling the gateway.
- Gateway tests replace downstream services with a JDK `HttpServer` stub started in the test (no extra test dependency).
- Every service has a schema boundary test: its SQL and migrations must not mention another service's schema.
- Test method names describe behavior in camelCase sentences (existing style: `zeroGamePlayersAreSelectedAheadOfOneGamePlayers`).
- AssertJ assertions; given / when / then separated by blank lines.
- Every new route adds rows to the gateway's `GatewayAccessMatrixIT` (route rule) and to the owning service's `AccessMatrixIT` (service-level check) for each caller kind in the §3 table (AD-09, AD-14).
- Every non-host response gets a `JsonAllowlist` assertion (AD-09).
- Every mutation has a test for its 409 case that also checks row counts are unchanged.
- Test data through `support/TestData` builders, not copy-pasted SQL.

## API (`/api/v1`)

- Public routes exactly as §13 (+ `GET /auth/csrf`), each added to the gateway route table. A new route needs an owner-approved OD/AD first (`/new-endpoint`).
- Internal routes `/internal/v1/**` exist only between services; the gateway never routes them; each one is approved in its feature design (AD-15).
- JSON fields camelCase; enum values UPPER_SNAKE strings identical to the DB values; timestamps ISO-8601 with offset.
- Errors: `{"code","message","requestId"}` with codes from `ErrorCode` (system-design §9), the same body from the gateway and every service.
- Status codes per §13: 400 / 401 / 403 / 404 / 409, plus 503 `SERVICE_UNAVAILABLE` when a needed service does not answer.

## Frontend (React 19, TypeScript 7, Vite 8, Tailwind 4)

### Structure (§16 + AD-08)

```text
src/api/         client.ts (only fetch caller), errors.ts, types.ts, <resource>.ts
src/hooks/       useAsync.ts, useMutation.ts, usePolling.ts
src/components/  shared UI used by 2+ features
src/features/<kebab-name>/  <Name>Page.tsx + components used only by that feature
src/routes/      route table and guards
src/lib/         pure helpers (time.ts, status.ts)
src/copy/        en.ts — every user-facing string
src/styles/      tokens.css, globals.css
```

### Naming

| Kind | Pattern | Example |
|---|---|---|
| Component file | `PascalCase.tsx`, one exported component per file | `CourtCard.tsx` |
| Page | `<Name>Page.tsx` in its feature folder | `features/host-board/HostBoardPage.tsx` |
| Hook | `useCamelCase.ts` | `usePolling.ts` |
| Other modules | `camelCase.ts` | `time.ts` |
| Feature folder | kebab-case (§16) | `match-review/` |
| Types | `PascalCase`; API types mirror backend record names without the `Response` suffix | `PublicSession`, `MyAttendance` |

### Rules

- Only `src/api/` calls `fetch`. Components and pages call functions from `api/<resource>.ts`.
- Types for API data live in `api/types.ts`. Use string-literal unions for enums (`type AttendanceState = 'PENDING' | …`), not TypeScript `enum`.
- Every fetch uses `useAsync` (`loading / empty / success / error`); every mutation uses `useMutation` (`idle / saving / success / conflict / error`). Render with an exhaustive `switch` on `status` (§16).
- Buttons are disabled while saving; the backend still validates (§16).
- Status is shown only through `StatusBadge` + `lib/status.ts` (text + color, never color alone, §6).
- User-facing strings come from `copy/en.ts`. Blueprint copy is used verbatim; any new string is listed in the feature design's copy table and needs owner approval before use.
- Styling: Tailwind utilities with the token names from `tokens.css` (`bg-action`, `text-ink`, …). No raw hex colors in components (SVG brand art may use tokens via `currentColor` or CSS variables).
- Accessibility per component: real `<button>`/`<a>`, visible focus, labels, ≥ 44px targets, works without drag-and-drop.
- No global state library; no new npm package without approval (`frontend-matching.md`).
- Relative imports; no path aliases unless approved.
- Formatting: Prettier (`frontend/.prettierrc.json`: no semicolons, single quotes).

## Git

- The owner commits; Claude only stages (`CLAUDE.md`), unless the owner asks Claude to commit/push for that piece of work.
- Feature folders and branches use the feature's plain kebab-case name, no ID (owner decision 2026-11-17): folder `planning/sprint<NN>-<name>/<feature-name>/`, branch `feature/<feature-name>`. The `F<nn>` ID stays only inside the planning files (board, sprint table, feature header) for cross-references; when talking to the owner use the feature name.
- Suggested commit message: `F<nn>: <imperative summary>` + body listing blueprint § and `AC-xx` covered. No `Co-Authored-By: Claude` or other Claude attribution line: the owner must be the only author (owner decision 2026-11-17).
- Pre-commit pipeline must be green before staging for commit (`CLAUDE.md` 2026-11-16).
