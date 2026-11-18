import { apiGet } from './client'
import type { PublicGame } from './types'

export function getPublicGame(
  publicCode: string,
  signal?: AbortSignal,
): Promise<PublicGame> {
  return apiGet<PublicGame>(
    `/public/games/${encodeURIComponent(publicCode)}`,
    signal,
  )
}
