import { useCallback, useState } from 'react'
import { ApiError } from '../api/errors'

// State of one write (blueprint section 16; AD-08). Render it with an exhaustive switch on status.
// `conflict` = the server answered 409; every other failure is `error` (inspect it, e.g. an ApiError status).
export type MutationState<R> =
  | { status: 'idle' }
  | { status: 'saving' }
  | { status: 'success'; data: R }
  | { status: 'conflict' }
  | { status: 'error'; error: unknown }

export function useMutation<A, R>(
  action: (arg: A) => Promise<R>,
): MutationState<R> & {
  run: (arg: A) => Promise<R | undefined>
  reset: () => void
} {
  const [state, setState] = useState<MutationState<R>>({ status: 'idle' })

  const run = useCallback(
    async (arg: A) => {
      setState({ status: 'saving' })
      try {
        const data = await action(arg)
        setState({ status: 'success', data })
        return data
      } catch (error) {
        setState(
          error instanceof ApiError && error.status === 409
            ? { status: 'conflict' }
            : { status: 'error', error },
        )
        return undefined
      }
    },
    [action],
  )

  const reset = useCallback(() => setState({ status: 'idle' }), [])

  return { ...state, run, reset }
}
