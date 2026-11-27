import { useCallback, useEffect, useReducer } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { ApiError } from '../../api/errors'
import { getHostBoard } from '../../api/host'
import { confirmMatch, suggestMatches } from '../../api/matches'
import type { HostBoard, MatchSuggestions } from '../../api/types'
import BackHomeLink from '../../components/BackHomeLink'
import Button from '../../components/Button'
import EmptyState from '../../components/EmptyState'
import ErrorBanner from '../../components/ErrorBanner'
import Logo from '../../components/Logo'
import StatusBadge from '../../components/StatusBadge'
import { copy } from '../../copy/en'
import { useAsync } from '../../hooks/useAsync'
import { useMutation } from '../../hooks/useMutation'
import { COURT_MATCH_STATUS } from '../../lib/status'
import BenchList from './BenchList'
import {
  initLineup,
  lineupReducer,
  type CourtLineup,
  type Slot,
} from './lineup'
import MatchProposal from './MatchProposal'

const t = copy.matchReview

type Review = { suggestions: MatchSuggestions; board: HostBoard }

function statusOf(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null
}

const linkClass =
  'inline-flex min-h-11 items-center justify-center rounded-2xl border-2 border-ink bg-surface px-4 text-base font-extrabold text-ink hover:bg-highlight focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action'

// /host/games/:gameId/review: the host's suggested courts (blueprint sections 6, 13, 16). Tap two players to swap
// them, then confirm one court at a time; only Confirm saves. Suggestions carry attendance ids, so names and levels
// come from the board read right after (design D-5).
export default function MatchReviewPage() {
  const { gameId = '' } = useParams()
  const load = useCallback(
    async (signal: AbortSignal): Promise<Review> => {
      const suggestions = await suggestMatches(gameId)
      const board = await getHostBoard(gameId, signal)
      return { suggestions, board }
    },
    [gameId],
  )
  const review = useAsync(load)
  const boardPath = `/host/games/${gameId}`

  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1200px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6 md:pb-3">
        <Logo />
      </header>
      <main className="mx-auto flex max-w-[1200px] flex-col gap-4 px-4 pt-2 pb-6 md:px-6 md:pt-3">
        {(() => {
          switch (review.status) {
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
                <Proposals
                  review={review.data}
                  gameId={gameId}
                  boardPath={boardPath}
                  onRefresh={review.reload}
                />
              )
            case 'error':
              switch (statusOf(review.error)) {
                case 401:
                  return (
                    <Navigate
                      to={`/login?next=${encodeURIComponent(`${boardPath}/review`)}`}
                      replace
                    />
                  )
                case 403:
                  return (
                    <EmptyState
                      title={copy.hostBoard.forbidden}
                      action={<BackHomeLink />}
                    />
                  )
                case 404:
                  return <NotFound />
                case 409:
                  return (
                    <EmptyState
                      title={t.closed}
                      action={
                        <Link to={boardPath} className={linkClass}>
                          {t.backToBoard}
                        </Link>
                      }
                    />
                  )
                default:
                  return <ErrorBanner onRetry={review.reload} />
              }
            default: {
              const unreachable: never = review
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

function Proposals({
  review,
  gameId,
  boardPath,
  onRefresh,
}: {
  review: Review
  gameId: string
  boardPath: string
  onRefresh: () => void
}) {
  const { suggestions, board } = review
  const [lineup, dispatch] = useReducer(lineupReducer, review, (r) =>
    initLineup(r.suggestions, r.board),
  )
  // Fresh data after "Refresh suggestions": rebuild the lineup (edits are dropped, design section 7).
  useEffect(() => {
    dispatch({
      type: 'reset',
      suggestions: review.suggestions,
      board: review.board,
    })
  }, [review])
  const frozen = lineup.saving !== null
  const players = new Map(board.players.map((p) => [p.attendanceId, p]))
  const emptyNote =
    suggestions.noSuggestionReason === 'NO_FREE_COURT'
      ? t.noFreeCourt
      : board.game.matchFormat === 'DOUBLES'
        ? copy.hostBoard.fewerThanFour
        : copy.hostBoard.fewerThanTwo
  const onTap = (slot: Slot) => dispatch({ type: 'tap', slot })

  const sel = lineup.selected
  const selectedId =
    sel === null
      ? null
      : sel.kind === 'bench'
        ? lineup.bench[sel.index]
        : lineup.courts.find((c) => c.courtNo === sel.courtNo)?.[sel.team][
            sel.index
          ]
  const selectedName = selectedId ? players.get(selectedId)?.displayName : null

  return (
    <>
      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-extrabold">{t.title}</h1>
        <p className="text-base text-muted">{t.notSaved}</p>
        {suggestions.suggestions.length > 0 && (
          <p className="text-base text-muted">{t.swapHint}</p>
        )}
        <p aria-live="polite" className="text-base font-bold text-ink">
          {selectedName ? t.selected(selectedName) : ''}
        </p>
      </div>
      {suggestions.suggestions.length === 0 ? (
        <p role="status" className="text-base font-bold text-muted">
          {emptyNote}
        </p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {suggestions.suggestions.map((proposal) => {
            const court = lineup.courts.find(
              (c) => c.courtNo === proposal.courtNo,
            )
            return court ? (
              <MatchProposal
                key={proposal.courtNo}
                proposal={proposal}
                court={court}
                players={players}
                selected={lineup.selected}
                onTap={onTap}
                frozen={frozen}
                footer={
                  <CourtConfirm
                    gameId={gameId}
                    court={court}
                    boardPath={boardPath}
                    frozen={frozen}
                    onSaving={() =>
                      dispatch({ type: 'saving', courtNo: court.courtNo })
                    }
                    onFailed={() => dispatch({ type: 'failed' })}
                    onSaved={(matchNo) =>
                      dispatch({
                        type: 'saved',
                        courtNo: court.courtNo,
                        matchNo,
                      })
                    }
                  />
                }
              />
            ) : null
          })}
        </div>
      )}
      <BenchList
        ids={lineup.bench}
        players={players}
        selected={lineup.selected}
        onTap={onTap}
        disabled={frozen}
      />
      <div className="sticky bottom-0 -mx-4 flex flex-col gap-2 bg-background/95 px-4 py-3 md:mx-0 md:flex-row md:px-0">
        <Button
          onClick={onRefresh}
          disabled={frozen}
          className="min-h-14 w-full md:w-auto"
        >
          {t.refresh}
        </Button>
        <Link to={boardPath} className={`${linkClass} min-h-14 md:w-auto`}>
          {t.backToBoard}
        </Link>
      </div>
    </>
  )
}

// Confirm for one court: one mutation per court, so a failure on court 1 never touches court 2 (design section 7).
function CourtConfirm({
  gameId,
  court,
  boardPath,
  frozen,
  onSaving,
  onFailed,
  onSaved,
}: {
  gameId: string
  court: CourtLineup
  boardPath: string
  frozen: boolean
  onSaving: () => void
  onFailed: () => void
  onSaved: (matchNo: number) => void
}) {
  const save = useCallback(
    (lineup: CourtLineup) =>
      confirmMatch(gameId, {
        courtNo: lineup.courtNo,
        teams: { A: lineup.A, B: lineup.B },
      }),
    [gameId],
  )
  const mutation = useMutation(save)

  const confirm = async () => {
    onSaving()
    const data = await mutation.run(court)
    if (data) {
      onSaved(data.matchNo)
    } else {
      onFailed()
    }
  }

  if (court.saved) {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <p role="status" className="text-base font-extrabold">
          {t.saved(court.saved.matchNo)}
        </p>
        <StatusBadge {...COURT_MATCH_STATUS.PLANNED} />
      </div>
    )
  }

  const saving = mutation.status === 'saving'
  const button = (
    <Button
      onClick={confirm}
      disabled={saving || frozen}
      className="min-h-12 w-full"
    >
      {saving ? copy.hostBoard.saving : t.confirm}
    </Button>
  )
  switch (mutation.status) {
    case 'idle':
    case 'saving':
    case 'success':
      return button
    case 'conflict':
      return (
        <>
          <p role="alert" className="text-base font-bold text-error">
            {t.conflict}
          </p>
          {button}
        </>
      )
    case 'error': {
      const status = statusOf(mutation.error)
      if (status === 401) {
        return (
          <Navigate
            to={`/login?next=${encodeURIComponent(`${boardPath}/review`)}`}
            replace
          />
        )
      }
      if (status === 403) {
        return (
          <p role="alert" className="text-base font-bold text-error">
            {copy.hostBoard.forbidden}
          </p>
        )
      }
      return <ErrorBanner onRetry={confirm} />
    }
    default: {
      const unreachable: never = mutation
      return unreachable
    }
  }
}
