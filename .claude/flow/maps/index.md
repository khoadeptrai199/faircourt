# FairCourt discovery map — modules, data, tests

Built 2026-11-18 by reading the code on branch `feature/agentic-workflow` (based on `feature/guest-check-in`). Refresh when a service, schema, migration or test class is added (`/flow` docs step).

## Backend modules (`backend/pom.xml`)

| Module | Package | Owns | Migrations (`src/main/resources/db/migration/`) | Tests |
|---|---|---|---|---|
| `libs/common-web` | `com.faircourt.common…` | error model (`ErrorCode`), request ID, identity token, test support (`@IntegrationTest`, `TestDatabase`) | — | `ErrorCodeTest`, `RequestIdFilterTest`, `TestSupportTest`, `IdentityTokenTest`, `CallerIdentityArgumentResolverTest` (identity token v2 in `com.faircourt.common.identity`, bound to service, method and path, 2026-11-26) |
| `libs/matching` | `com.faircourt.matching` | pure matcher: `MatchSuggester`, `BoardSnapshot`, `Proposal`, `ReasonCode` (`SIMILAR_LEVEL`, `BALANCED_TEAMS`, `LONG_WAIT`), `Warning` (`WIDE_SKILL_GAP`, `UNKNOWN_LEVEL`), `SuggestionResult.NoSuggestionReason` (`WAITING_FOR_PLAYERS`, `NO_FREE_COURT`) | — | `MatchSuggesterTest`; golden cases: [../golden/matcher-cases.md](../golden/matcher-cases.md) |
| `services/gateway` | `com.faircourt.gateway` (`account`, `common`, `csrf`, `guest`, `player`, `routing`, `security`) | route table `routing/GatewayRouteTable`, account session (`fc_sid`), CSRF, login throttle, header sanitizer | — | `GatewayAccessMatrixIT`, `AccountLoginIT`, `GuestCheckInIT`, `CsrfBootstrapIT`, `HostRoutesIT`, `DownstreamErrorIT`, `AuthServiceDownIT`, `GatewayHealthIT`, `HeaderSanitizerIT`, `RequestIdIT`, `AbsoluteSessionTimeoutFilterTest`, `LoginThrottleTest` |
| `services/auth-service` | `com.faircourt.auth` (`account`, `guest`, `passwordreset`, `security`, `common`) | schema `auth`: `app_user`, `guest_access`, `password_reset` | `V1__initial_schema`, `V2__rename_session_to_game`, `V3__password_reset` | `AccountVerifyIT`, `GuestCredentialIT`, `PasswordResetIT`, `PasswordRulesTest`, `AuthSchemaIT`, `AuthServiceAccessIT`, `SchemaBoundaryTest` |
| `services/player-service` | `com.faircourt.player` (`guest`, `level`, `member`, `security`, `seed`) | schema `player`: `player_profile`, `skill_assessment` | `V1__initial_schema`, `V2__rename_session_to_game` | `PlayerSchemaIT`, `GuestProfileIT`, `PlayerLevelsIT`, `GuestAnswersTest`, `PlayerServiceAccessIT`, `SchemaBoundaryTest` |
| `services/playmatch-service` | `com.faircourt.playmatch` (`access`, `attendance`, `board`, `common`, `game`, `guest`, `match`, `player`, `security`, `seed`) | schema `playmatch`: `game`, `attendance`, `play_match`, `match_player` | `V1__initial_schema`, `V2__rename_session_to_game` | `PublicGameIT`, `GuestCheckInIT`, `HostBoardIT`, `HostGamesIT`, `HostAttendanceIT`, `AttendanceStateTest`, `CreateGameIT`, `GameServiceTest`, `PlaymatchSchemaIT`, `PlaymatchServiceAccessIT`, `SchemaBoundaryTest` |
| `rating-service` | — | not built yet (S05) | — | — |

Next free migration number: auth `V4`, player `V3`, playmatch `V3`.

## Compose services (`compose.yaml`)

`postgres`, `gateway` (host 9090), `auth-service`, `mailpit`, `player-service`, `playmatch-service` (JDWP 9092), `frontend` (host 9091), `backend-test` (profile `test`), `backend-format` (profile `tools`).

## Frontend (`frontend/src`)

| Folder | Contents |
|---|---|
| `api/` | `client.ts` (only `fetch` caller), `errors.ts`, `types.ts`, `account.ts`, `games.ts`, `host.ts`, `passwordReset.ts` |
| `features/` | `create-game/`, `home/`, `host-board/`, `join/`, `login/`, `my-game/`, `password-reset/`, `public-game/` |
| `routes/` | route table: `/`, `/g/:publicCode`, `/g/:publicCode/join`, `/my/games/:gameId`, `/host/games/new`, `/host/games/:gameId`, `/login`, `/forgot-password`, `/reset-password`, `*` |
| `components/`, `hooks/`, `lib/`, `copy/en.ts`, `styles/` | shared UI, `useAsync`/`useMutation`, helpers, all copy, tokens |

No frontend unit-test runner is set up yet.

## Validators

`scripts/precommit.sh` (full pipeline), fast commands in `.claude/flow/config.json`.
