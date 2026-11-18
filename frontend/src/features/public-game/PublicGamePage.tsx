import { useCallback } from 'react'
import { useParams } from 'react-router'
import { getPublicGame } from '../../api/games'
import { isNotFound } from '../../api/errors'
import BackHomeLink from '../../components/BackHomeLink'
import EmptyState from '../../components/EmptyState'
import ErrorBanner from '../../components/ErrorBanner'
import { copy } from '../../copy/en'
import { useAsync } from '../../hooks/useAsync'
import GameHero from './GameHero'

// /g/:publicCode: public game page for anyone with the link (blueprint sections 3, 16; OD-22).
export default function PublicGamePage() {
  const { publicCode = '' } = useParams()
  const load = useCallback(
    (signal: AbortSignal) => getPublicGame(publicCode, signal),
    [publicCode],
  )
  const game = useAsync(load)

  return (
    <main className="mx-auto max-w-[720px] px-4 py-6 md:py-10">
      {(() => {
        switch (game.status) {
          case 'loading':
            return (
              <p role="status" className="text-base">
                {copy.common.loading}
              </p>
            )
          case 'success':
            return <GameHero game={game.data} />
          case 'empty':
            return <NotFound />

          case 'error':
            return isNotFound(game.error) ? (
              <NotFound />
            ) : (
              <ErrorBanner onRetry={game.reload} />
            )
          default: {
            const unreachable: never = game
            return unreachable
          }
        }
      })()}
    </main>
  )
}

function NotFound() {
  return (
    <EmptyState
      title={copy.publicGame.notFoundTitle}
      body={copy.publicGame.notFoundBody}
      action={<BackHomeLink />}
    />
  )
}
