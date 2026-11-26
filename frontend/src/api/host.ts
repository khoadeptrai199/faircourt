import { apiGet, apiPatch } from './client'
import type {
  AttendanceTarget,
  HostAttendanceChange,
  HostBoard,
  HostedGame,
} from './types'

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

// Host write (blueprint section 13): approve a pending guest (READY) or mark a player as left (LEFT).
export function changeAttendance(change: {
  gameId: string
  attendanceId: string
  state: AttendanceTarget
}): Promise<HostAttendanceChange> {
  return apiPatch<HostAttendanceChange>(
    `/host/games/${encodeURIComponent(change.gameId)}/attendance/${encodeURIComponent(change.attendanceId)}`,
    { state: change.state },
  )
}
