import { apiPost } from './client'
import type { GuestCheckIn, GuestCheckInRequest } from './types'

// 201 for a new guest, 200 when the same browser joins again. The guest cookie is set by the gateway
// (HttpOnly); JavaScript never sees it.
export function checkInAsGuest(
  publicCode: string,
  request: GuestCheckInRequest,
): Promise<GuestCheckIn> {
  return apiPost<GuestCheckIn>(
    `/public/games/${encodeURIComponent(publicCode)}/guests`,
    request,
  )
}
