import { useCallback } from 'react'
import { getMe } from '../api/account'
import type { Account } from '../api/types'
import { useAsync } from './useAsync'

export type AccountState =
  | { status: 'loading' }
  | { status: 'signedOut' }
  | { status: 'signedIn'; account: Account }
  | { status: 'error' }

// Who is signed in, asked once per page load from GET /me (answered by the gateway from its session).
export function useAccount(): AccountState & { reload: () => void } {
  const load = useCallback((signal: AbortSignal) => getMe(signal), [])
  const result = useAsync(load)

  switch (result.status) {
    case 'loading':
      return { status: 'loading', reload: result.reload }
    case 'empty':
      return { status: 'signedOut', reload: result.reload }
    case 'success':
      return result.data
        ? { status: 'signedIn', account: result.data, reload: result.reload }
        : { status: 'signedOut', reload: result.reload }
    case 'error':
      return { status: 'error', reload: result.reload }
    default: {
      const unreachable: never = result
      return unreachable
    }
  }
}
