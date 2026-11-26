import { apiGet, apiPost } from './client'
import type { CreatedGame, CreateGameInput, PublicGame } from './types'

export function getPublicGame(
  publicCode: string,
  signal?: AbortSignal,
): Promise<PublicGame> {
  return apiGet<PublicGame>(
    `/public/games/${encodeURIComponent(publicCode)}`,
    signal,
  )
}

// Host creates a game (blueprint section 13 POST /sessions, renamed by OD-22). The caller becomes the host.
export function createGame(input: CreateGameInput): Promise<CreatedGame> {
  return apiPost<CreatedGame>('/games', input)
}
