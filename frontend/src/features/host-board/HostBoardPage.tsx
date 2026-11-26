import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { changeAttendance, getHostBoard } from '../../api/host'
import { ApiError } from '../../api/errors'
import type {
  AttendanceState,
  AttendanceTarget,
  HostBoard,
} from '../../api/types'
import BackHomeLink from '../../components/BackHomeLink'
import Button from '../../components/Button'
import CourtCard from '../../components/CourtCard'
import EmptyState from '../../components/EmptyState'
import ErrorBanner from '../../components/ErrorBanner'
import Logo from '../../components/Logo'
import { copy } from '../../copy/en'
import { useAsync } from '../../hooks/useAsync'
import { useMutation, type MutationState } from '../../hooks/useMutation'
import GameStrip from './GameStrip'
import PlayerList from './PlayerList'

const t = copy.hostBoard

// Order of the status lists (blueprint section 3). READY has its own place beside the courts on desktop.
const OTHER_STATES: AttendanceState[] = [
  'PENDING',
  'ASSIGNED',
  'PLAYING',
  'RESTING',
  'LEFT',
]

function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null
}

// /host/games/:gameId: the host's full view of one game (blueprint sections 6, 13, 16). Reads only; a Refresh
// button, no polling.
export default function HostBoardPage() {
  const { gameId = '' } = useParams()
  const load = useCallback(
    (signal: AbortSignal) => getHostBoard(gameId, signal),
    [gameId],
  )
  const board = useAsync(load)
  const change = useMutation(changeAttendance)
  const [changingId, setChangingId] = useState<string | null>(null)
  const { status: changeStatus } = change
  const { reload } = board

  // One change at a time: every action button is disabled while one is saving. After a change (or a 409) the
  // board is read again.
  useEffect(() => {
    if (changeStatus === 'success' || changeStatus === 'conflict') reload()
  }, [changeStatus, reload])

  const onChange = (attendanceId: string, state: AttendanceTarget) => {
    setChangingId(attendanceId)
    void change.run({ gameId, attendanceId, state })
  }
  const changeFailure =
    change.status === 'error' ? statusOf(change.error) : null
  // Only a real FORBIDDEN answer is the "not your game" view; a 403 for a bad CSRF token is an ordinary error.
  const changeForbidden =
    change.status === 'error' &&
    change.error instanceof ApiError &&
    change.error.status === 403 &&
    change.error.code === 'FORBIDDEN'
  const { reset } = change
  // Refresh / Try again: clear the failed write so its banner goes away, then read the board again.
  const refresh = () => {
    reset()
    reload()
  }

  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1200px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6 md:pb-3">
        <Logo />
      </header>
      <main className="mx-auto flex max-w-[1200px] flex-col gap-4 px-4 pt-2 pb-6 md:px-6 md:pt-3">
        {(() => {
          if (changeFailure === 401) {
            return (
              <Navigate
                to={`/login?next=${encodeURIComponent(`/host/games/${gameId}`)}`}
                replace
              />
            )
          }
          if (changeForbidden) return <Forbidden />
          switch (board.status) {
            case 'loading':
              return (
                <p role="status" className="text-base">
                  {copy.common.loading}
                </p>
              )
            case 'empty':
              return <NotFound />
            case 'success':
              return (
                <Board
                  board={board.data}
                  onRefresh={refresh}
                  change={change}
                  savingId={changeStatus === 'saving' ? changingId : null}
                  onChange={onChange}
                />
              )
            case 'error':
              switch (statusOf(board.error)) {
                case 401:
                  return (
                    <Navigate
                      to={`/login?next=${encodeURIComponent(`/host/games/${gameId}`)}`}
                      replace
                    />
                  )
                case 403:
                  return <Forbidden />
                case 404:
                  return <NotFound />
                default:
                  return <ErrorBanner onRetry={board.reload} />
              }
            default: {
              const unreachable: never = board
              return unreachable
            }
          }
        })()}
      </main>
    </div>
  )
}

function NotFound() {
  return (
    <EmptyState
      title={copy.common.pageNotFoundTitle}
      action={<BackHomeLink />}
    />
  )
}

// 403: the 403 answer carries no public code, so the page cannot link to this game's public page; it offers home.
function Forbidden() {
  return (
    <EmptyState
      title={t.forbidden}
      action={
        <Link
          to="/"
          className="inline-flex min-h-11 items-center font-extrabold text-action underline focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
        >
          {copy.common.backHome}
        </Link>
      }
    />
  )
}

function Board({
  board,
  onRefresh,
  change,
  savingId,
  onChange,
}: {
  board: HostBoard
  onRefresh: () => void
  change: MutationState<unknown>
  savingId: string | null
  onChange: (attendanceId: string, state: AttendanceTarget) => void
}) {
  const { game, players, courts, asOf } = board
  const names = new Map(players.map((p) => [p.attendanceId, p.displayName]))
  const nameOf = (id: string) => names.get(id) ?? ''
  const ready = players.filter((p) => p.state === 'READY')
  const needed = game.matchFormat === 'DOUBLES' ? 4 : 2
  const readyNote =
    ready.length === 0
      ? t.noReady
      : ready.length < needed
        ? game.matchFormat === 'DOUBLES'
          ? t.fewerThanFour
          : t.fewerThanTwo
        : undefined

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-extrabold">{t.title}</h1>
        <Button variant="secondary" onClick={onRefresh}>
          {t.refresh}
        </Button>
      </div>
      <GameStrip game={game} />
      {(() => {
        switch (change.status) {
          case 'conflict':
            return (
              <p role="status" className="text-base font-bold text-ink">
                {t.changeConflict}
              </p>
            )
          case 'error':
            return <ErrorBanner onRetry={onRefresh} />
          case 'idle':
          case 'saving':
          case 'success':
            return null
          default: {
            const unreachable: never = change
            return unreachable
          }
        }
      })()}
      {game.state === 'CLOSED' && (
        <p role="status" className="text-base font-bold text-muted">
          {t.closedNote}
        </p>
      )}

      <div className="flex flex-col gap-4 md:grid md:grid-cols-[minmax(0,1fr)_340px] md:items-start md:gap-6">
        <div className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-2 md:mx-0 md:grid md:grid-cols-2 md:overflow-visible md:px-0 xl:grid-cols-3">
          {courts.map((court) => (
            <CourtCard
              key={court.courtNo}
              courtNo={court.courtNo}
              match={
                court.match && {
                  matchNo: court.match.matchNo,
                  state: court.match.state,
                  teams: {
                    A: court.match.teams.A.map(nameOf),
                    B: court.match.teams.B.map(nameOf),
                  },
                }
              }
            />
          ))}
        </div>
        <PlayerList
          state="READY"
          players={ready}
          asOf={asOf}
          minSets={game.minSets}
          note={readyNote}
          gameClosed={game.state === 'CLOSED'}
          savingId={savingId}
          onChange={onChange}
        />
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {OTHER_STATES.map((state) => (
          <PlayerList
            key={state}
            state={state}
            players={players.filter((p) => p.state === state)}
            asOf={asOf}
            minSets={game.minSets}
            gameClosed={game.state === 'CLOSED'}
            savingId={savingId}
            onChange={onChange}
          />
        ))}
      </div>

      <div className="sticky bottom-0 -mx-4 flex flex-col items-center gap-1 bg-background/95 px-4 py-3 md:mx-0 md:px-0">
        <Button
          disabled
          aria-describedby="suggest-soon"
          className="min-h-14 w-full text-[17px] md:max-w-sm"
        >
          {t.suggest}
        </Button>
        <p id="suggest-soon" className="text-sm font-bold text-muted">
          {copy.common.comingSoon}
        </p>
      </div>
    </>
  )
}
