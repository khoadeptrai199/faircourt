import { apiPost } from './client'
import type { MatchSuggestions } from './types'

// Host compute without a write (blueprint section 13): proposes courts, saves nothing. POST so it stays behind CSRF.
export function suggestMatches(gameId: string): Promise<MatchSuggestions> {
  return apiPost<MatchSuggestions>(
    `/host/games/${encodeURIComponent(gameId)}/suggestions`,
  )
}
