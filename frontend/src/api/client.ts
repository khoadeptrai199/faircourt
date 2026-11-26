import { ApiError } from './errors'
import type { ApiErrorBody } from './types'

// The only place that calls fetch (AD-08). Same origin: Vite proxies /api to the gateway (blueprint section 0).
// Cookies (the HttpOnly session cookie) are sent by the browser; JavaScript never reads them.

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

// CSRF token for writes (blueprint section 0): fetched from the gateway, kept only in memory (never in
// localStorage or a cookie JavaScript can read), sent in the header the gateway names. The gateway ties it to the
// session, so it is forgotten at login and logout and fetched again.
type CsrfToken = { headerName: string; token: string }
let csrf: CsrfToken | null = null

export function forgetCsrfToken(): void {
  csrf = null
}

async function csrfHeader(): Promise<Record<string, string>> {
  if (!csrf) {
    csrf = await apiGet<CsrfToken>('/auth/csrf')
  }
  return { [csrf.headerName]: csrf.token }
}

// JSON write (POST). Retries once with a fresh token when the gateway says the token is stale; never retries
// anything else (a write is not repeated by guesswork, AD-15). Answers 204 with undefined.
export function apiPost<T = void>(path: string, body?: unknown): Promise<T> {
  return apiWrite<T>('POST', path, body)
}

// JSON write (PATCH): same CSRF and retry-once rule as apiPost.
export function apiPatch<T = void>(path: string, body?: unknown): Promise<T> {
  return apiWrite<T>('PATCH', path, body)
}

async function apiWrite<T>(
  method: 'POST' | 'PATCH',
  path: string,
  body?: unknown,
): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    const response = await fetch(`/api/v1${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(await csrfHeader()),
      },
      credentials: 'same-origin',
      body: JSON.stringify(body ?? {}),
    })
    if (response.ok) {
      return (response.status === 204 ? undefined : await response.json()) as T
    }
    const errorBody = await readErrorBody(response)
    if (
      attempt === 0 &&
      response.status === 403 &&
      errorBody?.code === 'CSRF_TOKEN_INVALID'
    ) {
      forgetCsrfToken()
      continue
    }
    throw new ApiError(response.status, errorBody)
  }
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
