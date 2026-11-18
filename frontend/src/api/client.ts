import { ApiError } from './errors'
import type { ApiErrorBody } from './types'

// The only place that calls fetch (AD-08). Same origin: Vite proxies /api to the gateway (blueprint section 0).
export async function apiGet<T>(
  path: string,
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
    signal,
  })
  if (!response.ok) {
    throw new ApiError(response.status, await readErrorBody(response))
  }
  return (await response.json()) as T
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'code' in body) {
      return body as ApiErrorBody
    }
  } catch {
    // Not JSON (e.g. the dev proxy could not reach the gateway).
  }
  return null
}
