import { apiPost } from './client'
import type {
  ConfirmedMatch,
  ConfirmMatchRequest,
  MatchSuggestions,
} from './types'

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
