import type { ApiErrorBody } from './types'

// A non-2xx answer from the API, with the standard error body when there is one (blueprint section 13).
export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly requestId: string | null

  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code ?? null
    this.requestId = body?.requestId ?? null
  }
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
