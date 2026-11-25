import { useCallback } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/errors'
import { getMyAttendance } from '../../api/attendance'
import Button from '../../components/Button'
import ErrorBanner from '../../components/ErrorBanner'
import Logo from '../../components/Logo'
import { copy } from '../../copy/en'
import { useAsync } from '../../hooks/useAsync'
import StateCard from './StateCard'

const t = copy.myGame

function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null
}

// /my/games/:gameId: the caller's own state only, from its own endpoint (blueprint sections 13, 16). A Refresh
// button, no polling yet.
export default function MyGamePage() {
  const { gameId = '' } = useParams()
  const load = useCallback(
    (signal: AbortSignal) => getMyAttendance(gameId, signal),
    [gameId],
  )
  const mine = useAsync(load)
  // The public code (not secret) comes in the query so the page can link back; anything else in the query is ignored.
  const [query] = useSearchParams()
  const rawCode = query.get('code')
  const code = rawCode && /^[A-Za-z0-9]{1,20}$/.test(rawCode) ? rawCode : null

  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1080px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6 md:pb-3">
        <Logo />
      </header>
      <main className="mx-auto flex max-w-[720px] flex-col gap-4 px-4 pt-2 pb-12 md:pt-3">
        <h1 className="text-xl font-extrabold">{t.title}</h1>
        {(() => {
          switch (mine.status) {
            case 'loading':
              return (
                <p role="status" className="text-base">
                  {copy.common.loading}
                </p>
              )
            case 'empty':
              return <NotJoined code={code} />
            case 'success':
              return (
                <>
                  <StateCard state={mine.data.attendanceState} />
                  <Button
                    variant="secondary"
                    onClick={mine.reload}
                    className="min-h-14 text-[17px]"
                  >
                    {t.refresh}
                  </Button>
                </>
              )
            case 'error':
              switch (statusOf(mine.error)) {
                case 404:
                  return <NotJoined code={code} />
                case 401:
                  return <Expired code={code} />
                default:
                  return <ErrorBanner onRetry={mine.reload} />
              }
            default: {
              const unreachable: never = mine
              return unreachable
            }
          }
        })()}
      </main>
    </div>
  )
}

const linkClass =
  'inline-flex min-h-[52px] items-center justify-center rounded-2xl bg-action px-6 text-[17px] font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action'

function NotJoined({ code }: { code: string | null }) {
  return (
    <div className="flex flex-col items-start gap-4">
      <p
        role="status"
        className="w-full rounded-2xl border-2 border-dashed border-ink/25 bg-surface p-4 text-center text-base font-bold"
      >
        {t.notJoined}
      </p>
      {code && (
        <Link to={`/g/${code}`} className={linkClass}>
          {t.backToGame}
        </Link>
      )}
    </div>
  )
}

// 401: guest access is gone or expired, or nobody is signed in. With a valid public code in the query, "Check in
// again" goes to that game's join page; otherwise to the home page.
function Expired({ code }: { code: string | null }) {
  return (
    <div className="flex flex-col items-start gap-4">
      <p
        role="alert"
        className="rounded-xl bg-error-soft px-3.5 py-3 text-base font-bold text-error"
      >
        {t.expired}
      </p>
      <Link to={code ? `/g/${code}/join` : '/'} className={linkClass}>
        {t.checkInAgain}
      </Link>
    </div>
  )
}
