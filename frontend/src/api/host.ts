import { apiGet } from './client'
import type { HostBoard, HostedGame } from './types'

// Host-only reads (blueprint section 13). `gameId` names the game, never the caller; the server checks the owner.
export function getHostBoard(
  gameId: string,
  signal?: AbortSignal,
): Promise<HostBoard> {
  return apiGet<HostBoard>(
    `/host/games/${encodeURIComponent(gameId)}/board`,
    signal,
  )
}

export async function getHostedGames(
  signal?: AbortSignal,
): Promise<HostedGame[]> {
  const answer = await apiGet<{ games: HostedGame[] }>('/host/games', signal)
  return answer.games
}
