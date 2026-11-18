import { useCallback } from 'react'
import { useParams } from 'react-router'
import { getPublicGame } from '../../api/games'
import { isNotFound } from '../../api/errors'
import BackHomeLink from '../../components/BackHomeLink'
import CourtMiss from '../../components/CourtMiss'
import EmptyState from '../../components/EmptyState'
import ErrorBanner from '../../components/ErrorBanner'
import Logo from '../../components/Logo'
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
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1080px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6 md:pb-3">
        <Logo />
      </header>
      {/* Bottom padding keeps the last card clear of the Join bar fixed to the bottom on phones. */}
      <main className="mx-auto max-w-[1080px] px-4 pt-2 pb-28 md:px-6 md:pt-3 md:pb-10">
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
    </div>
  )
}

function NotFound() {
  return (
    <EmptyState
      title={copy.publicGame.notFoundTitle}
      body={copy.publicGame.notFoundBody}
      illustration={<CourtMiss className="h-[150px] w-[220px]" />}
      action={<BackHomeLink />}
    />
  )
}
