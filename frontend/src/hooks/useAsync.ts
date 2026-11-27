import { useCallback, useEffect, useRef, useState } from 'react'

// Page-state for one fetch (blueprint section 16; AD-08). Render it with an exhaustive switch on status.
export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'empty' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: unknown }

// `load` must be stable for the same inputs (wrap it in useCallback); a new `load` starts a new fetch.
// `isEmpty` marks a successful answer that has nothing to show.
// `reload()` goes back to `loading`; `refresh()` re-fetches without it (polling), the answer replaces the state.
export function useAsync<T>(
  load: (signal: AbortSignal) => Promise<T>,
  isEmpty?: (data: T) => boolean,
): AsyncState<T> & { reload: () => void; refresh: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const loadRef = useRef(load)
  const isEmptyRef = useRef(isEmpty)
  const refreshing = useRef<AbortController | null>(null)

  useEffect(() => {
    loadRef.current = load
    isEmptyRef.current = isEmpty
  })

  useEffect(() => {
    const controller = new AbortController()
    refreshing.current?.abort()
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
    return () => {
      controller.abort()
      refreshing.current?.abort()
    }
    // isEmpty is read only when an answer arrives; on purpose it does not restart the fetch.
  }, [load, attempt])

  const reload = useCallback(() => setAttempt((n) => n + 1), [])

  const refresh = useCallback(() => {
    refreshing.current?.abort()
    const controller = new AbortController()
    refreshing.current = controller
    loadRef.current(controller.signal).then(
      (data) => {
        if (controller.signal.aborted) return
        setState(
          isEmptyRef.current?.(data)
            ? { status: 'empty' }
            : { status: 'success', data },
        )
      },
      (error: unknown) => {
        if (controller.signal.aborted) return
        setState({ status: 'error', error })
      },
    )
  }, [])

  return { ...state, reload, refresh }
}
