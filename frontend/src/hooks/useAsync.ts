import { useCallback, useEffect, useState } from 'react'

// Page-state for one fetch (blueprint section 16; AD-08). Render it with an exhaustive switch on status.
export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'empty' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: unknown }

// `load` must be stable for the same inputs (wrap it in useCallback); a new `load` starts a new fetch.
// `isEmpty` marks a successful answer that has nothing to show.
export function useAsync<T>(
  load: (signal: AbortSignal) => Promise<T>,
  isEmpty?: (data: T) => boolean,
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setState({ status: 'loading' })
    load(controller.signal).then(
      (data) => {
        if (controller.signal.aborted) return
        setState(
          isEmpty?.(data) ? { status: 'empty' } : { status: 'success', data },
        )
      },
      (error: unknown) => {
        if (controller.signal.aborted) return
        setState({ status: 'error', error })
      },
    )
    // Abort on unmount or when inputs change, so a late answer never overwrites a newer one.
    return () => controller.abort()
    // isEmpty is read only when an answer arrives; on purpose it does not restart the fetch.
  }, [load, attempt])

  const reload = useCallback(() => setAttempt((n) => n + 1), [])

  return { ...state, reload }
}
