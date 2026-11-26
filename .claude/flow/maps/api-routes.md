# FairCourt discovery map — routes

Built 2026-11-18 from `GatewayRouteTable` and the `@*Mapping` annotations. Refresh when a route is added: new row here, rows in `GatewayAccessMatrixIT` and the owning service's `AccessMatrixIT`, and `API Document/`.

## Public routes (`/api/v1`, through the gateway)

| Method | Path | Gateway access | Answered by | Controller | Main tests | Frontend |
|---|---|---|---|---|---|---|
| GET | `/api/v1/auth/csrf` | PUBLIC | gateway | `gateway.csrf.CsrfController` | `CsrfBootstrapIT` | `api/client.ts` |
| POST | `/api/v1/auth/login` | PUBLIC (CSRF, throttle) | gateway → auth `/internal/v1/auth/verify` | `gateway.account.AccountController` | `AccountLoginIT`, `LoginThrottleTest` | `features/login/`, `api/account.ts` |
| POST | `/api/v1/auth/logout` | ACCOUNT | gateway | `gateway.account.AccountController` | `AccountLoginIT` | `api/account.ts` |
| GET | `/api/v1/me` | ACCOUNT | gateway (+ auth session-check) | `gateway.account.AccountController` | `AccountLoginIT` | `api/account.ts` |
| POST | `/api/v1/auth/password/forgot` | PUBLIC (CSRF) | auth-service | `auth.passwordreset.PasswordResetController` | `PasswordResetIT` | `features/password-reset/`, `api/passwordReset.ts` |
| POST | `/api/v1/auth/password/reset` | PUBLIC (CSRF) | auth-service | `auth.passwordreset.PasswordResetController` | `PasswordResetIT`, `PasswordRulesTest` | `features/password-reset/`, `api/passwordReset.ts` |
| GET | `/api/v1/public/games/{publicCode}` | PUBLIC | playmatch-service | `playmatch.game.PublicGameController` | `PublicGameIT` | `features/public-game/`, `api/games.ts` |
| POST | `/api/v1/public/games/{publicCode}/guests` | PUBLIC (CSRF, 4 KB cap) | gateway → playmatch `/internal/v1/games/by-code/*/guest-check-ins` (saga with player + auth) | `gateway.guest.GuestController` | `GuestCheckInIT` (gateway, playmatch), `GatewayAccessMatrixIT` | `features/join/`, `api/guests.ts` |
| POST | `/api/v1/games/{gameId}/join` | ACCOUNT (CSRF) | gateway → playmatch `/internal/v1/games/*/member-attendances` (→ player `/internal/v1/players/members`) | `gateway.player.PlayerAccessController` | `PlayerAccessIT`, `MemberAttendanceIT`, `GatewayAccessMatrixIT` | `features/public-game/JoinButton.tsx`, `api/attendance.ts` |
| GET | `/api/v1/games/{gameId}/me` | PLAYER (account or guest cookie) | gateway → playmatch `/internal/v1/games/*/my-attendance` | `gateway.player.PlayerAccessController` | `PlayerAccessIT`, `MemberAttendanceIT` | `features/my-game/`, `api/attendance.ts` |
| GET | `/api/v1/host/games/{gameId}/board` | HOST (signed-in account; token `X-Internal-Identity` added) | playmatch-service (+ player `/internal/v1/players/levels`) | `playmatch.board.HostBoardController` (`access.AccessPolicy.requireHost`) | `HostBoardIT`, `HostRoutesIT`, `PlaymatchServiceAccessIT` | `features/host-board/`, `api/host.ts` |
| GET | `/api/v1/host/games` | HOST (signed-in account; token added) | playmatch-service | `playmatch.board.HostGamesController` | `HostGamesIT`, `HostRoutesIT`, `PlaymatchServiceAccessIT` | `features/home/HomePage.tsx`, `api/host.ts` |

All routes: `GatewayAccessMatrixIT`. Unknown routes answer 401. Identity token: gateway `routing.IdentityTokenFilter` (only `HOST` routes), services `common.identity.CallerIdentityArgumentResolver` (2026-11-25).

## Internal routes (`/internal/v1`, service to service; the gateway never routes them)

| Method | Path | Owner | Caller | Controller | Tests |
|---|---|---|---|---|---|
| POST | `/internal/v1/auth/verify` | auth-service | gateway (login) | `auth.account.AccountVerifyController` | `AccountVerifyIT` |
| GET | `/internal/v1/auth/accounts/{userId}/session-check` | auth-service | gateway | `auth.account.SessionCheckController` | `AuthServiceAccessIT` |
| POST | `/internal/v1/auth/guest-credentials` | auth-service | playmatch (guest check-in) | `auth.guest.GuestCredentialController` | `GuestCredentialIT`, `AuthServiceAccessIT` |
| POST | `/internal/v1/auth/guest-credentials/resolve` | auth-service | gateway (guest cookie) | `auth.guest.GuestCredentialController` | `GuestCredentialIT`, `AuthServiceAccessIT` |
| POST | `/internal/v1/players/guests` | player-service | playmatch (guest check-in) | `player.guest.GuestProfileController` | `GuestProfileIT`, `PlayerServiceAccessIT` |
| POST | `/internal/v1/games/by-code/{publicCode}/guest-check-ins` | playmatch-service | gateway | `playmatch.guest.GuestCheckInController` | `GuestCheckInIT`, `PlaymatchServiceAccessIT` |
| POST | `/internal/v1/players/levels` | player-service | playmatch (host board) | `player.level.PlayerLevelsController` | `PlayerLevelsIT`, `PlayerServiceAccessIT` |

Member join and own attendance (2026-11-20) also added internal routes (`/internal/v1/games/*/member-attendances`, `/internal/v1/games/*/my-attendance`, `/internal/v1/players/members`, `/internal/v1/players/by-user/*`); not yet listed row by row here.

## Not built yet (blueprint §13, routes renamed per the "game" naming decision)

Suggestions, confirm/start/finish/cancel, feedback, host assessment. See `planning/README.md` for the feature that owns each.
