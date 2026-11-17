# <Project name> — Project brief & bootstrap contract

Template version 1.0 (2026-11-17). One file that tells an AI coding agent **what** to build (business and requirements) and **how** to bootstrap it (stack, environment, rules). Part A is written for the AI; Parts B and C are filled in by you.

---

## How to use this file (for the person filling it in)

1. Copy this file into an empty folder that will become the repository and rename it `PROJECT_BRIEF.md`.
2. Fill in Parts B and C. Answers may be in Vietnamese or English. Short answers are fine; concrete examples beat long prose.
3. Use the legend below. You do **not** have to answer everything: unanswered items become questions the AI asks you before it builds anything that depends on them.
4. Send the file to the AI with one line: **"Read PROJECT_BRIEF.md and follow Part A exactly."**
5. Keep the file in the repository. It stays the source of truth; the AI records decisions next to it.

### Legend

| Mark | Meaning |
|---|---|
| `[x]` / `[ ]` | Chosen / not chosen option |
| `> ...` | Your answer goes on the line(s) starting with `>` |
| `TBD` or empty | I don't know yet. The AI must ask before building anything that depends on it |
| `AI:recommend` | The AI proposes 2–3 options with reasons and a recommendation, then waits for my confirmation |
| `AI:decide` | The AI picks a sensible default, records it in `DECISIONS.md`, and does not block on it |
| **(R)** | Required before the skeleton is generated |
| **(D)** | Required before domain code (entities, rules, flows) is generated |
| (O) | Optional |

### Completeness tracker (fill as you go)

| Section | Needed for | Status (done / partial / TBD) |
|---|---|---|
| 1 Identity & bootstrap options | (R) | |
| 2 Tech stack | (R) | |
| 3 Dev environment & infra | (R) | |
| 4 Quality, testing, conventions | (R) | |
| 5 AI working agreement | (R) | |
| 6 Problem, goals, scope | (R) | |
| 7 Users, roles, permissions | (D) | |
| 8 Domain model & business rules | (D) | |
| 9 Flows, screens, integrations | (D) | |
| 10 Data, privacy, non-functional | (D) | |
| 11 Demo data, acceptance, open questions | (R) | |

---

# Part A — Instructions for the AI (bootstrap contract)

You are the coding agent for this project. Build a **working, runnable project skeleton** from this brief, run it, fix failures, and hand over something the owner can start with one command. Do not stop after generating code or describing a plan, except at the approval gates chosen in 1.6.

## A1. Source of truth

1. This file is the specification. Precedence: **Part A rules → owner's answers in Parts B/C → owner's messages in the conversation → your defaults**.
2. An answer of `TBD`, an empty answer, or a contradiction is a question for the owner, never a silent guess — unless the owner wrote `AI:decide` for that item.
3. Record every decision you make (`AI:decide` items, small implementation choices) in `DECISIONS.md` with date, decision, reason, and the brief section it belongs to.
4. If the owner answers a question in chat, also write the answer back into this file (or `DECISIONS.md`) so the next session does not depend on chat memory.

## A2. Hard rules (always on; section 5 may add more, never remove these)

1. **No fabricated facts.** Never invent versions, library APIs, test results, benchmarks, user research or "it works" claims. Versions come from official release pages / registries checked during this run, with the date checked. If something was not run or checked, write "not verified".
2. **No unrequested scope.** Build only what this brief asks for. Ideas go to a "Later" list, not into code.
3. **No destructive actions without explicit consent**: deleting or overwriting existing files, dropping data or volumes, rewriting git history, editing an already-applied migration.
4. **Never weaken security to make something pass**: no disabling CSRF/auth checks, no wildcard CORS with credentials, no committed secrets, no turned-off TLS verification.
5. **Pin everything**: exact versions in build files and lockfiles; exact container image tags; never `latest` or floating ranges.
6. **Honest handoff**: list every command actually executed with its real result, and everything not verified.

## A3. Phases and gates

| Phase | What you do | Output | Gate (see 1.6) |
|---|---|---|---|
| 0 Intake | Read the whole brief. Check completeness of every **(R)** item, contradictions, risky choices (e.g. microservices for a 3-person tool), and ambiguities that affect behavior | **Brief review** (A4) with questions | Stop and ask unless 1.6 says otherwise |
| 1 Plan | Turn the answers into an architecture and a concrete file plan; verify versions | **Bootstrap plan** (A5) | Stop for approval unless 1.6 says otherwise |
| 2 Generate | Create the repository per the approved plan and the universal skeleton (A6) | Files | — |
| 3 Verify | Install, build, test, start, health-check; fix failures; repeat | Command log | Work is not done while a required check fails |
| 4 Handoff | Report (A8); fill the planning board (A7) with the next features | Handoff message | — |

Ask questions in batches: most blocking first, at most the number set in 5.3 per round, each with options and a recommendation. Never ask what the brief already answers.

## A4. Phase 0 output — Brief review

```text
Understanding (5–10 bullets): what is being built, for whom, the core flow.

| # | Section | Type (missing R / missing D / contradiction / risk / ambiguity) | Question | Options | Recommendation |
|---|---------|------|----------|---------|----------------|

Items I will decide myself (AI:decide): <list>
Can I start Phase 1 with what I have? yes / no (blocking items: ...)
```

## A5. Phase 1 output — Bootstrap plan

1. **Summary** of the product and the skeleton level (1.5).
2. **Runtime diagram** (Mermaid): browser/clients, services/containers, database, external systems, ports.
3. **Stack and versions table**: component | exact version | official source URL | date checked | why it fits (owner skills 1.2, constraints 6.7).
4. **Repository layout** (tree) with one line per top-level folder.
5. **Backend module map**: modules, what each owns, allowed dependency direction.
6. **Domain model** (Mermaid ER diagram) and **state machines** from section 8, if the skeleton level needs them.
7. **Permission matrix as it will be enforced** (from 7.3), and where each check lives (route rule, service, DB).
8. **API style and first routes** (health, auth, and anything required by the skeleton level).
9. **Files to generate**, grouped by folder.
10. **Patterns chosen and where** (A6.9), plus patterns deliberately *not* used and why.
11. **Decisions taken under `AI:decide`.**
12. **Risks and open questions** still remaining.
13. **Proposed backlog**: sprints and features (A7) after the skeleton.

## A6. Universal skeleton (generate regardless of stack)

Adapt names to the chosen stack; skip an item only if the brief explicitly excludes it, and say so.

1. **One-command local start**: e.g. `cp .env.example .env` then `docker compose up --build -d` (or the run mode chosen in 3.2). Health checks and start ordering between services. A documented stop command that keeps data; a separately documented reset that deletes data, clearly marked.
2. **Configuration**: all settings from environment variables; `.env.example` with development-only values and comments; real `.env` git-ignored; no secrets in the repo.
3. **Cross-platform**: works unchanged on every machine in 3.1 (e.g. both `amd64` and `arm64` images; no hardcoded `platform:`; LF line endings via `.gitattributes`; dependency folders kept inside containers when source is bind-mounted).
4. **Backend**: health endpoint that reports database connectivity; one consistent JSON error format with a request ID; request ID in logs; input validation at the boundary; authentication baseline per 2.13; authorization check location per 7.3; CORS closed or same-origin; injected clock for time logic; structured package layout per 2.2.
5. **Database**: the migration tool from 2.6 owns the schema (no auto-DDL in shared environments); first migration per skeleton level; a separate test database or Testcontainers; local-only, idempotent seed per 11.1.
6. **Frontend** (if any): app shell, routing, one API client module that is the only place making HTTP calls (credentials, CSRF if cookies, typed errors), design tokens from 9.5, responsive base layout, reusable loading / empty / error components, all user-facing text in one copy module.
7. **Tests**: at least one unit test per side, one integration test against the real database engine, one HTTP-level smoke test; one documented command per suite.
8. **Quality**: formatter and linter per 4.3 with "apply" and "check" commands; optional pre-commit hook per 4.4; CI per 3.7.
9. **Patterns — proportionate, not speculative**: modular monolith by default; module owns its tables; controllers thin; business rules in services or pure functions; state transitions in one place per state machine; value objects for validated inputs; separate response DTO per audience (public / own / admin) so private fields cannot leak by reuse; adapters around external integrations; no interface with a single implementation unless a second is planned. Record each pattern used in `DECISIONS.md`.
10. **Docs**:
    - `README.md`: what it is, requirements, start/stop/reset, URLs and ports, demo accounts (local only), test/format commands, pinned versions table with dates, verification log (date, machine, command, result), known limitations.
    - `DECISIONS.md`: decision log.
    - API reference stub (or generated OpenAPI if chosen in 4.7).
    - AI guidance file(s) chosen in 1.7, containing: the hard rules (A2 + section 5), scope guard (6.5 Won't list), conventions (section 4), environment notes (section 3), and where the planning board is.
    - Planning board (A7) if chosen in 1.7.

## A7. Planning board format (if chosen in 1.7)

```text
planning/
  README.md          board: sprints + one row per feature (ID, title, sprint, status, depends on, waiting for, links)
  ROADMAP.md         sprint plan, open decisions (OD-xx), later backlog (L-xx), risks
  sprintNN-<name>/
    SPRINT.md        goal, gate, features, demo steps, gate evidence, retro
    FNN-<slug>/
      README.md      status card: goal, scope in/out, acceptance criteria AC-NN, open decisions, decision log
      design.md      API contract, data/transactions, rules, components + patterns, copy table, test plan
      implement.md   tasks, files, commands executed, evidence per AC
      review.md      reviewer findings, acceptance trace, gate result
```

Status values: `BACKLOG → DESIGN → WAITING_APPROVAL → APPROVED (owner only) → IN_PROGRESS → IN_REVIEW → DONE`, plus `CHANGES_REQUESTED` and `BLOCKED`. No code for a feature before `APPROVED`. Order sprints from easy to hard inside the dependency order: read-only before writes, anonymous before authenticated, single-row writes before multi-row transactions. Every sprint ends runnable and demoable.

## A8. Phase 4 output — Handoff

```text
Local URL(s):            ...
Demo accounts (local):   ...
Start / stop / reset:    exact commands
Test / format / build:   exact commands
Machines verified on:    <os/arch, runtime versions>  | not verified on: ...
Commands executed:       | command | result |
Acceptance (11.2):       | item | PASS / FAIL / NOT VERIFIED | evidence |
Decisions I made:        link to DECISIONS.md entries
Open questions:          ...
Known limitations:       ...
Next features:           link to planning/README.md
```

Do not call the work done while any required acceptance item fails.

---

# Part B — Project setup choices

## 1. Identity and bootstrap options (R)

**1.1 Name, slug and one-line description**
> Name:
> Slug (repo / package name, kebab-case):
> One line:

**1.2 Who builds and maintains it** (team size, roles, languages/frameworks you are comfortable with; the AI uses this to recommend a stack)
>

**1.3 Project type**
- [ ] Web app: SPA frontend + API backend
- [ ] Server-rendered web app (templates, little JavaScript)
- [ ] API / backend only
- [ ] Mobile app + API
- [ ] Background worker / CLI
- [ ] Other:
- [ ] AI:recommend

**1.4 Stage**
- [ ] Prototype / demo (speed over robustness)
- [ ] MVP / pilot with real users (correctness and privacy matter)
- [ ] Production product

**1.5 Skeleton level** (how far the AI goes in the first run)
- [ ] **L1 Bare**: repository, build, run, health check, DB connected, empty migration baseline, format/lint, test commands, docs
- [ ] **L2 Foundation** *(recommended)*: L1 + authentication and roles, error format, request ID, API client, base UI components, test harness, seed accounts
- [ ] **L3 First slice**: L2 + schema for all entities in section 8 + one complete use case end to end (name it in 11.3)

**1.6 Approval gates**
- [ ] Stop after Phase 0 (questions) and after Phase 1 (plan) *(recommended)*
- [ ] Stop after Phase 0 only
- [ ] No stops — only if every (R) item is answered and nothing is `TBD`

**1.7 Deliverables to generate** (untick what you don't want)
- [x] `README.md`, `DECISIONS.md`, `.env.example`, `.gitignore`, `.gitattributes`, `.editorconfig`
- [x] Container setup (Compose + Dockerfiles) per 3.2
- [x] Backend skeleton
- [ ] Frontend skeleton
- [x] Database migrations + local seed
- [x] Tests (unit, integration, smoke)
- [x] Formatter / linter config
- [ ] CI workflow (choose in 3.7)
- [ ] API documentation (OpenAPI / Swagger UI)
- [ ] Planning board (A7)
- AI guidance files:
  - [ ] `CLAUDE.md` + `.claude/rules/` (Claude Code)
  - [ ] `AGENTS.md` (Codex and others)
  - [ ] `.github/copilot-instructions.md`
  - [ ] `.cursor/rules/`

**1.8 Repository shape**
- [ ] One repo: `backend/`, `frontend/`, root compose *(recommended for small teams)*
- [ ] Separate repos per app
- [ ] AI:recommend
> Base package / namespace (e.g. `com.acme.app`):

**1.9 Languages**
> Code, comments, docs:
> UI default language (+ later languages):
> Language the AI uses when replying to me:

## 2. Tech stack (R)

Tick one per group, or `AI:recommend` / `AI:decide`. Write `Other:` if yours is not listed.

**2.0 Version policy**
- [ ] Latest stable / LTS releases, verified on official sources during the run *(recommended)*
- [ ] Exact versions I give here:
>

**2.1 Backend language and framework**
- [ ] Java + Spring Boot
- [ ] Kotlin + Spring Boot
- [ ] C# + ASP.NET Core
- [ ] TypeScript + NestJS
- [ ] TypeScript + Express / Fastify
- [ ] Python + FastAPI
- [ ] Python + Django
- [ ] Go (standard library / Gin / Echo)
- [ ] PHP + Laravel
- [ ] Ruby on Rails
- [ ] Other:
- [ ] AI:recommend

**2.2 Architecture style**
- [ ] Modular monolith (one deployable, modules by business area) *(recommended default)*
- [ ] Layered monolith (controller / service / repository only)
- [ ] Microservices (only with a real reason; write it here):
- [ ] Serverless functions
- [ ] AI:recommend

**2.3 API style**
- [ ] REST + JSON
- [ ] GraphQL
- [ ] gRPC
- [ ] tRPC (TypeScript end to end)
- [ ] Server-rendered only (no public API)
> URL prefix / versioning (e.g. `/api/v1`):

**2.4 Real-time updates**
- [ ] None — refresh / polling is enough
- [ ] Server-Sent Events
- [ ] WebSocket
- [ ] AI:recommend

**2.5 Database**
- [ ] PostgreSQL
- [ ] MySQL / MariaDB
- [ ] SQL Server
- [ ] SQLite (single-user / local tools only)
- [ ] MongoDB
- [ ] Other:
- [ ] AI:recommend

**2.6 Data access and migrations**
- Data access: [ ] Framework ORM (JPA, EF Core, Prisma, SQLAlchemy, ActiveRecord, …) [ ] SQL-first (JDBC/JdbcClient, Dapper, Drizzle, sqlc, …) [ ] ORM for writes + SQL for read views [ ] AI:recommend
- Migrations: [ ] Flyway [ ] Liquibase [ ] EF Core migrations [ ] Prisma Migrate [ ] Alembic [ ] Framework built-in [ ] AI:recommend
- IDs: [ ] UUID [ ] auto-increment [ ] AI:recommend
- Timestamps: [ ] store UTC, display in a zone (10.8) *(recommended)*

**2.7 Other infrastructure** (tick only what you really need now)
- [ ] Cache (e.g. Redis)
- [ ] Message queue / event bus
- [ ] Full-text search engine
- [ ] Object storage for files
- [ ] Email / SMS provider
- [ ] None of these in the first version *(recommended unless a requirement needs it)*

**2.8 Background jobs / scheduling**
- [ ] None
- [ ] In-process scheduler
- [ ] Separate worker
- [ ] AI:recommend

**2.9 Frontend framework**
- [ ] React + Vite (SPA)
- [ ] Next.js
- [ ] Vue + Vite
- [ ] Nuxt
- [ ] Angular
- [ ] SvelteKit
- [ ] Server templates (Thymeleaf, Razor, Django/Jinja, Blade, …)
- [ ] No frontend
- [ ] AI:recommend
> TypeScript strict mode: yes / no

**2.10 UI styling / component kit**
- [ ] Tailwind CSS + small in-house components
- [ ] Tailwind + shadcn/ui
- [ ] MUI
- [ ] Ant Design
- [ ] Bootstrap
- [ ] CSS Modules / plain CSS
- [ ] AI:recommend

**2.11 Frontend data, state, forms**
- Server data: [ ] small in-house fetch hooks [ ] TanStack Query [ ] framework built-in [ ] AI:recommend
- Global state: [ ] none until needed *(recommended)* [ ] Redux Toolkit [ ] Zustand [ ] Pinia [ ] AI:recommend
- Forms/validation: [ ] native [ ] React Hook Form + Zod [ ] framework built-in [ ] AI:recommend
- Routing: [ ] framework default [ ] React Router [ ] TanStack Router [ ] AI:recommend

**2.12 Mobile**
- [ ] None
- [ ] Responsive web only
- [ ] Installable PWA
- [ ] React Native / Expo
- [ ] Flutter
- [ ] Native iOS / Android

**2.13 Authentication**
- Method: [ ] server-side session cookie [ ] JWT access + refresh tokens [ ] OAuth/OIDC provider (Google, Microsoft, Keycloak, Auth0, …): ____ [ ] magic link [ ] API keys (machine clients) [ ] anonymous / guest token [ ] no login
- Who can create accounts: [ ] anyone (open signup) [ ] invite only [ ] admin-provisioned [ ] external identity provider decides
- Extras: [ ] email verification [ ] password reset [ ] MFA [ ] remember me [ ] none in v1

**2.14 Authorization model**
- [ ] Roles (RBAC)
- [ ] Ownership of a resource (e.g. "creator of this event manages it")
- [ ] Organization / tenant membership
- [ ] Attribute rules (ABAC)
- [ ] AI:recommend
> Explain in 7.3.

## 3. Development environment and infrastructure (R)

**3.1 Developer machines** (each one must run the project unchanged)
| Machine | OS / version | CPU arch | Container runtime | Notes (repo location, constraints) |
|---|---|---|---|---|
| | | | | |

**3.2 How things run locally**
- [ ] Everything in containers; no language toolchain needed on the host *(recommended)*
- [ ] Database in a container, apps on the host
- [ ] Everything on the host
- Dev extras: [ ] backend hot reload [ ] remote debugger port [ ] frontend hot reload with polling (needed on some network/WSL drives)

**3.3 Ports**
> Preferred host port range (e.g. 9090–9099):
> Ports that must not be used (other projects on the same machine):
> Bind to `127.0.0.1` only? yes / no

**3.4 IDEs / editors in use**
>

**3.5 Database admin client** (e.g. DBeaver, pgAdmin; publish the DB port to localhost?)
>

**3.6 Target hosting later** (O; the skeleton stays local unless you say otherwise)
- [ ] Local only for now
- [ ] Single VPS with Docker
- [ ] PaaS (Render, Fly.io, Railway, …)
- [ ] Cloud (AWS / Azure / GCP): ____
- [ ] Kubernetes (only with a real reason)

**3.7 CI**
- [ ] None yet
- [ ] GitHub Actions
- [ ] GitLab CI
- [ ] Azure DevOps
> CI runs: [ ] tests [ ] build [ ] format/lint check [ ] container build [ ] deploy (needs explicit authorization)

**3.8 Observability**
- [ ] Structured logs with request ID *(recommended)*
- [ ] Health + readiness endpoints *(recommended)*
- [ ] Metrics endpoint
- [ ] Error tracking service: ____
- [ ] Tracing

## 4. Quality, testing, conventions (R)

**4.1 Test levels**
- [x] Unit tests for business rules and calculations
- [x] Integration tests against the real database engine (container DB or Testcontainers)
- [x] Authorization tests: each role × each protected route
- [ ] HTTP-level end-to-end smoke test of the main flow
- [ ] Browser E2E (Playwright / Cypress)
- [ ] Accessibility checks
- [ ] Load test
> Coverage target (O):

**4.2 Formatting and linting**
- [ ] AI:recommend the standard tools for the stack *(recommended)*
- [ ] My choice:

**4.3 Pre-commit**
- [ ] Git hook (husky / lefthook / pre-commit)
- [ ] Documented pipeline command only
- [ ] None

**4.4 Git**
- Branching: [ ] trunk-based [ ] feature branches `feature/<id>-<slug>` [ ] AI:recommend
- Commit messages: [ ] Conventional Commits [ ] `<feature ID>: <summary>` [ ] free form
- Who commits: [ ] AI commits [ ] AI stages only, I commit [ ] AI never touches git

**4.5 Code principles** (edit freely)
> - Simple and explicit over clever.
> - No abstraction without a second use case.
> - Names follow the domain glossary (8.1).
> - Every business rule (8.4) has a test.

**4.6 Documentation**
- [ ] OpenAPI generated from code
- [ ] Hand-written API reference
- [ ] Architecture decision records
- [ ] Mermaid diagrams in docs

## 5. AI working agreement (R)

**5.1 Extra hard rules** (A2 always applies; add your own)
> -

**5.2 Ask or decide**
- [ ] Ask about anything that changes behavior, data, security, scope, dependencies or cost; decide internal naming/structure and record it *(recommended)*
- [ ] Ask only about scope and security; decide the rest
- [ ] Ask about everything

**5.3 Maximum questions per round**
> 10

**5.4 Reply style**
> e.g. short, tables preferred, Vietnamese replies, English code

---

# Part C — Business and requirements questionnaire

Tip: concrete examples with real names and numbers ("Mai has waited 25 minutes, Nam has played 2 games…") are the fastest way to explain business logic. The AI turns examples into tests.

## 6. Problem, goals, scope (R)

**6.1 Elevator pitch** — For `<who>` who `<need>`, `<product>` is a `<kind of product>` that `<key benefit>`. Unlike `<current way / competitor>`, it `<difference>`.
>

**6.2 Today's situation**: how is it done now, step by step? What goes wrong, how often, who suffers most?
>

**6.3 Goals and success measures** (measurable where possible)
| Goal | How we will know (metric / observation) |
|---|---|
| | |

**6.4 Non-goals** (things this product deliberately does not try to do)
>

**6.5 Scope (MoSCoW)**
| Must (first release) | Should | Could | Won't (explicitly later) |
|---|---|---|---|
| | | | |

**6.6 Timeline**: deadline, demo date, milestones, time available per week
>

**6.7 Constraints**: budget, licenses (open-source only?), company standards, existing systems to fit into, hosting limits
>

**6.8 Similar products / inspiration**: what to copy, what to avoid
>

## 7. Users, roles, permissions (D)

**7.1 Roles**
| Role | Who they are | How they identify (none / guest link / account / SSO) | Main goals | Device and context of use | Rough count |
|---|---|---|---|---|---|
| | | | | | |

**7.2 Account lifecycle**: how accounts are created, verified, recovered, deactivated, deleted; can people use it without an account?
>

**7.3 Permission matrix** — one row per action or piece of data. Values: `Yes` / `No` / `Own only` / `Same team` / `If <condition>`.
| Action or data | Anyone | <Role A> | <Role B> | <Admin> |
|---|---|---|---|---|
| | | | | |

*Example:* `View another player's assignment | No | No | Yes (host of this session)`.

**7.4 Who sees which fields** (audience-specific views; list fields that must never reach a given audience)
| Screen / response | Audience | May show | Must never show |
|---|---|---|---|
| | | | |

**7.5 Tenancy**
- [ ] One organization
- [ ] Many organizations, data fully isolated
- [ ] Ownership per resource (e.g. per event, per project)

**7.6 Admin / back-office needs**
>

## 8. Domain model and business rules (D)

**8.1 Glossary** — the words your users say, with one precise meaning each. The AI uses these names in code.
| Term | Meaning | Not to be confused with | Example |
|---|---|---|---|
| | | | |

**8.2 Entities** — copy this block per entity.

```text
Entity: <Name>
Purpose:
Created by / changed by / deleted by:
Fields:
| Field | Type | Required | Unique | Validation / rules | Example | Personal data? |
|-------|------|----------|--------|--------------------|---------|----------------|
Relationships: (e.g. "one Session has many Attendances")
Delete policy: hard delete / soft delete / never; retention:
Expected volume: (rows per day / total)
```

**8.3 Lifecycles (state machines)**
| Entity | From state | To state | Who / what triggers it | Preconditions | Side effects |
|---|---|---|---|---|---|
| | | | | | |
> Initial state(s):
> Final state(s):
> What happens on an illegal transition (error message / code):

*Example:* `Match | PLANNED | ACTIVE | host taps Start | all 4 players still assigned | players become PLAYING`.

**8.4 Business rules and invariants**
| ID | Rule | Example | If violated (message / error code) | Enforced in (UI / API / service / DB constraint) |
|---|---|---|---|---|
| BR-01 | | | | |

**8.5 Calculations, algorithms, matching, pricing** — copy per calculation.

```text
Name:
Inputs:
Output:
Steps / formula:
Tie-breakers (what decides when values are equal):
Must the same input always give the same output? yes / no
Golden examples (become unit tests):
| # | Input | Expected output | Why |
|---|-------|-----------------|-----|
```

**8.6 Decision tables** (for "if A and B but not C" logic)
| Condition 1 | Condition 2 | Condition 3 | Outcome |
|---|---|---|---|
| | | | |

**8.7 Time, money, units**: time zone for display (storage is UTC), day boundaries, currency and rounding, units of measure
>

**8.8 Concurrency and consistency** — what must **never** happen, even with double clicks or two people at once (e.g. double booking, one person in two places, paying twice)? What should the user see on conflict?
>

**8.9 Audit and history**: which changes must be traceable (who, what, when)?
>

## 9. Flows, screens, integrations (D)

**9.1 Use cases** — copy per use case; list the Must ones first.

```text
UC-01: <name>                       Priority: Must / Should / Could
Actor:
Trigger:
Preconditions:
Main flow:
  1.
  2.
Alternative / edge flows:
  1a.
Postconditions:
Acceptance criteria:
  - Given ... when ... then ...
```

**9.2 Screens**
| Route / screen | Audience | Purpose | Data shown | Primary action | Other actions | Empty / loading / error states |
|---|---|---|---|---|---|---|
| | | | | | | |

**9.3 Navigation**: menu items per role; landing page per role after sign-in
>

**9.4 Copy and tone**: voice (friendly / formal), words to avoid, texts that must appear exactly, languages
>

**9.5 Visual design**
> Brand colors (hex) and their use:
> Fonts:
> Corner radius / spacing feel:
> Light / dark mode:
> Phone-first? Minimum supported width (e.g. 320px):
> Accessibility target (e.g. WCAG 2.2 AA):
> Reference sites or screenshots:

**9.6 Notifications**
| Event | Channel (in-app / email / SMS / push) | Recipient | Content | Timing |
|---|---|---|---|---|
| | | | | |

**9.7 Scheduled / background jobs**
| Job | When | What it does | On failure |
|---|---|---|---|
| | | | |

**9.8 Integrations with other systems**
| System | Purpose | Direction (in / out) | Auth method | Data exchanged | Limits | If it is down | Sandbox available? |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

**9.9 Files and media**: types, size limits, where stored, retention, consent
>

**9.10 Reports, export, import**
>

## 10. Data, privacy, non-functional requirements (D)

**10.1 Personal and sensitive data**
| Field | Why it is needed | Who may see it | Retention | How it is deleted |
|---|---|---|---|---|
| | | | | |

**10.2 Regulations and policies that apply** (e.g. GDPR, local personal-data law, company policy); consent needs; where data must be stored
>

**10.3 Security requirements**: password policy, MFA, session length, rate limiting, audit log, anything from a security team
>

**10.4 Scale**: total users, peak concurrent users, data growth, file volume
>

**10.5 Performance targets**: page load, API response time, heavy operations
>

**10.6 Availability, backup, restore** (for later deployment)
>

**10.7 Devices and browsers**: phones/tablets/desktop, browsers, offline needs
>

**10.8 Internationalization**: languages, date/number formats, display time zone
>

## 11. Demo data, acceptance, open questions (R)

**11.1 Seed / demo data** (local only, idempotent: restarting must not duplicate it)
| Kind | How many | Details (roles, sample values) |
|---|---|---|
| Accounts | | e.g. 1 admin `admin@app.local` / dev-only password |
| Records | | |

**11.2 The skeleton is accepted when** (the AI adds the A6 basics automatically; add your own)
- [ ] One-command start works on every machine in 3.1
- [ ] Health endpoint reports UP with the database connected
- [ ] Frontend loads without a blank screen at phone and desktop width
- [ ] All test suites pass
- [ ] Restarting without deleting volumes keeps the data
- [ ] Custom:

**11.3 First vertical slice** (only for skeleton level L3): which use case from 9.1?
>

**11.4 Known open questions and assumptions to validate**
>

**11.5 Anything else the AI must know**
>

---

# Appendix — Filled examples

Short examples from a real project (doubles badminton matchmaking) to show the expected level of detail.

**7.3 Permission matrix**
| Action or data | Anyone | Joined player | Session host |
|---|---|---|---|
| View public session page (time, venue, courts, status) | Yes | Yes | Yes |
| View own match number and court | No | Own only | Yes |
| View another player's assignment or skill level | No | No | Yes |
| Suggest, assign, start, finish a match | No | No | Yes (host of this session only) |

**8.3 Lifecycle**
| Entity | From | To | Trigger | Preconditions | Side effects |
|---|---|---|---|---|---|
| Attendance | PENDING | READY | Host approves guest | Host owns the session | Guest appears in the ready pool |
| Attendance | READY | ASSIGNED | Host confirms a match | Player not in another unfinished match | Player sees "Your next match: Court 2" |
| Match | ACTIVE | FINISHED | Host taps Finish | Match is ACTIVE | 4 players back to READY, games played + 1 |

**8.4 Business rule**
| ID | Rule | Example | If violated | Enforced in |
|---|---|---|---|---|
| BR-03 | A player can be in at most one unfinished match | Mai is on Court 1 (PLANNED); host tries to put her on Court 2 | 409 `MATCH_CONFLICT` "A player or court is no longer available." | Service check + partial unique index |

**8.5 Golden examples**
| # | Input | Expected output | Why |
|---|---|---|---|
| 1 | 8 ready players, levels 1,1,2,2,3,3,4,4; 2 free courts | Court 1: levels 1,1,2,2; Court 2: 3,3,4,4; teams balanced | Group similar levels first, then balance teams |
| 2 | 4 players with 0 games and 4 with 1 game; 1 court | The four 0-game players | Fewer games played goes first |

**9.1 Use case**

```text
UC-02: Guest joins a session                Priority: Must
Actor: Visitor without an account
Trigger: Opens the shared session link
Preconditions: Session is OPEN
Main flow:
  1. Visitor taps "Join session".
  2. Enters a display name and answers four skill questions.
  3. Sees "Waiting for host approval".
Alternative / edge flows:
  1a. Session is closed → page is read-only, no Join button.
  3a. Visitor joins again from the same browser → sees the same entry, no duplicate.
Postconditions: Guest attendance PENDING; private guest cookie set.
Acceptance criteria:
  - Given an open session, when a visitor submits valid answers, then they see "Waiting for host approval" and the host sees them as Pending.
  - Given a guest cookie, when another person calls that guest's status API without it, then they get 401.
```
