# FairCourt discovery map — modules, data, tests

Built 2026-11-18 by reading the code on branch `feature/agentic-workflow` (based on `feature/guest-check-in`). Refresh when a service, schema, migration or test class is added (`/flow` docs step).

## Backend modules (`backend/pom.xml`)

| Module | Package | Owns | Migrations (`src/main/resources/db/migration/`) | Tests |
|---|---|---|---|---|
| `libs/common-web` | `com.faircourt.common…` | error model (`ErrorCode`), request ID, identity token, test support (`@IntegrationTest`, `TestDatabase`) | — | `ErrorCodeTest`, `RequestIdFilterTest`, `TestSupportTest` |
| `libs/matching` | `com.faircourt.matching` | pure matcher: `MatchSuggester`, `BoardSnapshot`, `Proposal`, `ReasonCode` (`SIMILAR_LEVEL`, `BALANCED_TEAMS`, `LONG_WAIT`), `Warning` (`WIDE_SKILL_GAP`, `UNKNOWN_LEVEL`), `SuggestionResult.NoSuggestionReason` (`WAITING_FOR_PLAYERS`, `NO_FREE_COURT`) | — | `MatchSuggesterTest`; golden cases: [../golden/matcher-cases.md](../golden/matcher-cases.md) |
| `services/gateway` | `com.faircourt.gateway` (`account`, `common`, `csrf`, `guest`, `routing`, `security`) | route table `routing/GatewayRouteTable`, account session (`fc_sid`), CSRF, login throttle, header sanitizer | — | `GatewayAccessMatrixIT`, `AccountLoginIT`, `GuestCheckInIT`, `CsrfBootstrapIT`, `DownstreamErrorIT`, `AuthServiceDownIT`, `GatewayHealthIT`, `HeaderSanitizerIT`, `RequestIdIT`, `AbsoluteSessionTimeoutFilterTest`, `LoginThrottleTest` |
| `services/auth-service` | `com.faircourt.auth` (`account`, `guest`, `passwordreset`, `security`, `common`) | schema `auth`: `app_user`, `guest_access`, `password_reset` | `V1__initial_schema`, `V2__rename_session_to_game`, `V3__password_reset` | `AccountVerifyIT`, `GuestCredentialIT`, `PasswordResetIT`, `PasswordRulesTest`, `AuthSchemaIT`, `AuthServiceAccessIT`, `SchemaBoundaryTest` |
| `services/player-service` | `com.faircourt.player` (`guest`, `security`, `seed`) | schema `player`: `player_profile`, `skill_assessment` | `V1__initial_schema`, `V2__rename_session_to_game` | `PlayerSchemaIT`, `GuestProfileIT`, `GuestAnswersTest`, `PlayerServiceAccessIT`, `SchemaBoundaryTest` |
| `services/playmatch-service` | `com.faircourt.playmatch` (`attendance`, `common`, `game`, `guest`, `security`, `seed`) | schema `playmatch`: `game`, `attendance`, `play_match`, `match_player` | `V1__initial_schema`, `V2__rename_session_to_game` | `PublicGameIT`, `GuestCheckInIT`, `PlaymatchSchemaIT`, `PlaymatchServiceAccessIT`, `SchemaBoundaryTest` |
| `rating-service` | — | not built yet (S05) | — | — |

Next free migration number: auth `V4`, player `V3`, playmatch `V3`.

## Compose services (`compose.yaml`)

`postgres`, `gateway` (host 9090), `auth-service`, `mailpit`, `player-service`, `playmatch-service` (JDWP 9092), `frontend` (host 9091), `backend-test` (profile `test`), `backend-format` (profile `tools`).

## Frontend (`frontend/src`)

| Folder | Contents |
|---|---|
| `api/` | `client.ts` (only `fetch` caller), `errors.ts`, `types.ts`, `account.ts`, `games.ts`, `passwordReset.ts` |
| `features/` | `home/`, `login/`, `password-reset/`, `public-game/` |
| `routes/` | route table: `/`, `/g/:publicCode`, `/login`, `/forgot-password`, `/reset-password`, `*` |
| `components/`, `hooks/`, `lib/`, `copy/en.ts`, `styles/` | shared UI, `useAsync`/`useMutation`, helpers, all copy, tokens |

No frontend unit-test runner is set up yet.

## Validators

`scripts/precommit.sh` (full pipeline), fast commands in `.claude/flow/config.json`.
