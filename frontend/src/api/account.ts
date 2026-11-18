import { ApiError } from './errors'
import { apiGet, apiPost, forgetCsrfToken } from './client'
import type { Account } from './types'

// The password goes only in the body of this one request, over the same origin, and is not kept anywhere.
export async function login(email: string, password: string): Promise<Account> {
  const account = await apiPost<Account>('/auth/login', { email, password })
  forgetCsrfToken() // the token belongs to the old session
  return account
}

export async function logout(): Promise<void> {
  try {
    await apiPost('/auth/logout')
  } finally {
    forgetCsrfToken()
  }
}

// null = nobody is signed in (401 is an answer here, not an error).
export async function getMe(signal?: AbortSignal): Promise<Account | null> {
  try {
    return await apiGet<Account>('/me', signal)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}
