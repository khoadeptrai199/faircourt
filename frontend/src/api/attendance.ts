import { apiGet, apiPost } from './client'
import type { MemberJoin, MyAttendance } from './types'

// Own entry in one game, for a signed-in member or a guest (the guest cookie is HttpOnly and sent by the browser).
// `gameId` is the opaque id of the public summary; it names the game, never the caller.
export function getMyAttendance(
  gameId: string,
  signal?: AbortSignal,
): Promise<MyAttendance> {
  return apiGet<MyAttendance>(`/games/${encodeURIComponent(gameId)}/me`, signal)
}

// Signed-in account joins at once: 201 the first time, 200 when already joined.
export function joinAsMember(gameId: string): Promise<MemberJoin> {
  return apiPost<MemberJoin>(`/games/${encodeURIComponent(gameId)}/join`)
}
