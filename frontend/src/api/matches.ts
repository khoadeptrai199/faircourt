import { apiPatch, apiPost } from './client'
import type {
  ConfirmedMatch,
  ConfirmMatchRequest,
  HostMatchState,
  MatchSuggestions,
} from './types'

// Host writes on one match (blueprint section 13). `matchId` names the match; the server finds the game and checks the host.
export function startMatch(matchId: string): Promise<HostMatchState> {
  return apiPatch<HostMatchState>(
    `/host/matches/${encodeURIComponent(matchId)}/start`,
  )
}

export function finishMatch(matchId: string): Promise<HostMatchState> {
  return apiPatch<HostMatchState>(
    `/host/matches/${encodeURIComponent(matchId)}/finish`,
  )
}

export function cancelMatch(matchId: string): Promise<HostMatchState> {
  return apiPatch<HostMatchState>(
    `/host/matches/${encodeURIComponent(matchId)}/cancel`,
  )
}

// Host compute without a write (blueprint section 13): proposes courts, saves nothing. POST so it stays behind CSRF.
export function suggestMatches(gameId: string): Promise<MatchSuggestions> {
  return apiPost<MatchSuggestions>(
    `/host/games/${encodeURIComponent(gameId)}/suggestions`,
  )
}

// Host write (blueprint section 13): saves one PLANNED match, or 409 when a player or court is no longer free.
export function confirmMatch(
  gameId: string,
  request: ConfirmMatchRequest,
): Promise<ConfirmedMatch> {
  return apiPost<ConfirmedMatch>(
    `/host/games/${encodeURIComponent(gameId)}/matches`,
    request,
  )
}
