# Project conventions (format and patterns for every task)

One set of naming, structure and pattern rules for the whole repository, so code written in different sessions looks the same. Background and diagrams: `planning/architecture/system-design.md`. Decisions marked `AD-xx` are in `planning/architecture/decisions.md`; while an AD is `PROPOSED`, follow it only inside a feature whose design the owner approved.

When this file and the blueprint differ, the blueprint wins. When this file and an older habit in the code differ, follow this file for new code and list the old spot in the feature's `implement.md` instead of refactoring it unasked.

## Language and docs

- English for code, comments, commit messages, UI copy, README, `API Document/`, `planning/` (owner decision 2026-11-17).
- Dates `YYYY-MM-DD`; timestamps ISO-8601.
- Cite the blueprint as `§<n>` where a rule is not obvious from the code. Do not paste long spec text into comments.
- IDs: features `F<nn>`, acceptance criteria `AC-<nn>` (global form `F04/AC-02`), tasks `T<nn>`, test cases `TC-<nn>`, open decisions `OD-<nn>`, architecture decisions `AD-<nn>`, later backlog `L-<nn>`. Never renumber or reuse an ID.

## Backend (Java 25, Spring Boot 4.1.2)

### Structure

- Packages = §15 modules: `common`, `identity`, `sessions`, `players`, `matching`, `matches`. Put a new class in the module that owns the table it writes (AD-01). No new top-level package without approval.
- Dependency direction: `matches → players → sessions → identity → common`; `matches → matching`; `matching` imports nothing from other modules and no Spring/JPA types.
- Flat packages per module (no `controller/`, `service/` sub-packages) until a module exceeds ~15 classes; then split by feature, not by layer.

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
- Identity comes only from `CallerIdentity` (principal or guest cookie). IDs in the path or body identify the target resource, never the caller (§13).
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

- New schema change = new file `V<n>__<snake_case_description>.sql`. Never edit an applied migration (`backend-db.md`).
- SQL in `*Query` classes as Java text blocks with named parameters (`:sessionId`), never string concatenation of values.

### Tests

- Unit tests: `<Class>Test.java`, no Spring context, same package as the class.
- Integration tests: `<Feature or Topic>IT.java`, annotated `@IntegrationTest`, against `faircourt_test` (failsafe runs them in `mvn verify`).
- Test method names describe behavior in camelCase sentences (existing style: `zeroGamePlayersAreSelectedAheadOfOneGamePlayers`).
- AssertJ assertions; given / when / then separated by blank lines.
- Every new route adds rows to `AccessMatrixIT` for each caller kind in the §3 table (AD-09).
- Every non-host response gets a `JsonAllowlist` assertion (AD-09).
- Every mutation has a test for its 409 case that also checks row counts are unchanged.
- Test data through `support/TestData` builders, not copy-pasted SQL.

## API (`/api/v1`)

- Routes exactly as §13 (+ `GET /auth/csrf`). A new route needs an owner-approved OD/AD first (`/new-endpoint`).
- JSON fields camelCase; enum values UPPER_SNAKE strings identical to the DB values; timestamps ISO-8601 with offset.
- Errors: `{"code","message","requestId"}` with codes from `ErrorCode` (system-design §9).
- Status codes per §13: 400 / 401 / 403 / 404 / 409.

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

- The owner commits; Claude only stages (`CLAUDE.md`).
- Suggested branch per feature: `feature/F<nn>-<slug>`.
- Suggested commit message: `F<nn>: <imperative summary>` + body listing blueprint § and `AC-xx` covered, ending with the attribution lines required by the session.
- Pre-commit pipeline must be green before staging for commit (`CLAUDE.md` 2026-11-16).
