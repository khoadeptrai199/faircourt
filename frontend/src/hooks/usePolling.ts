import { useEffect, useRef } from 'react'

// Calls `callback` every `intervalMs` while `enabled` and the page is visible. Hidden page: no timer; becoming
// visible again calls once at once, then restarts the interval. This is the single seam a push channel would replace.
export function usePolling(
  callback: () => void,
  intervalMs: number,
  enabled: boolean,
) {
  const latest = useRef(callback)
  useEffect(() => {
    latest.current = callback
  })

  useEffect(() => {
    if (!enabled) return
    let timer: ReturnType<typeof setInterval> | null = null

    const stop = () => {
      if (timer !== null) {
        clearInterval(timer)
        timer = null
      }
    }
    const start = () => {
      stop()
      timer = setInterval(() => latest.current(), intervalMs)
    }
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        latest.current()
        start()
      } else {
        stop()
      }
    }

    if (document.visibilityState === 'visible') start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [intervalMs, enabled])
}
