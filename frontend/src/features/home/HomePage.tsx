// Home page at "/" (owner-approved 2026-11-12; not part of blueprint section 16 routes).
// 2026-11-18: "session" -> "game" and a single contest -> "match" (owner decision, OD-22); restyled with the
// "Court lines" look (Look and feel feature). Only existing copy; navigation and "games near you" are mocked up
// in the design file and come with their own features (backlog L-20…L-22).
// "Host login" links to /login (Account login feature); when signed in the header shows the email and "Sign out".
import { useCallback } from 'react'
import { Link } from 'react-router'
import { logout } from '../../api/account'
import { getHostedGames } from '../../api/host'
import type { HostedGame } from '../../api/types'
import Button from '../../components/Button'
import CourtLines from '../../components/CourtLines'
import ErrorBanner from '../../components/ErrorBanner'
import Logo from '../../components/Logo'
import StatusBadge from '../../components/StatusBadge'
import { copy } from '../../copy/en'
import { useAccount, type AccountState } from '../../hooks/useAccount'
import { useAsync } from '../../hooks/useAsync'
import { GAME_STATUS } from '../../lib/status'
import { formatGameTime } from '../../lib/time'

const steps = [
  {
    title: 'Join a game',
    body: 'Open the link your host shares and check in.',
  },
  {
    title: 'Find a fair match',
    body: 'Matches are suggested by level and by who has waited longest.',
  },
  {
    title: 'Keep playing',
    body: 'See your court when it is your turn. The host makes the final call.',
  },
]

export default function HomePage() {
  const account = useAccount()
  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1080px] items-center justify-between gap-3 px-4 pt-4 pb-2 pl-5 md:px-6 md:pt-6 md:pb-3">
        <Logo />
        <AccountMenu account={account} />
      </header>

      <main className="mx-auto max-w-[1080px] px-4 pt-2 pb-12 md:px-6 md:pt-3">
        {account.status === 'signedIn' && <HostedGames />}

        <section
          aria-labelledby="home-title"
          className="relative overflow-hidden rounded-[28px] bg-action px-[22px] py-[26px] text-white md:rounded-[32px] md:px-12 md:py-16"
        >
          <CourtLines className="pointer-events-none absolute -right-12 -bottom-8 h-64 w-[26rem] text-white opacity-20 md:right-0 md:h-96 md:w-[36rem]" />
          <div className="relative max-w-[560px]">
            <h1
              id="home-title"
              className="text-[42px] leading-[1.02] font-black tracking-[-0.04em] md:text-7xl md:leading-none md:tracking-[-0.045em]"
            >
              Join a game.
              <br />
              Find a fair match.
              <br />
              <span className="text-accent">Keep playing.</span>
            </h1>
            <p className="mt-4 max-w-[300px] text-base leading-normal font-medium md:mt-5 md:max-w-none md:text-lg">
              FairCourt helps your badminton group form fair doubles matches, so
              everyone gets a good match.
            </p>
          </div>
        </section>

        <section aria-labelledby="how-title" className="mt-8 md:mt-12">
          <h2
            id="how-title"
            className="px-1 text-[22px] font-black tracking-tight md:text-[28px]"
          >
            How it works
          </h2>
          <ol className="mt-3 grid gap-2 md:mt-5 md:grid-cols-3 md:gap-5">
            {steps.map((step, index) => (
              <li
                key={step.title}
                className="flex items-start gap-3.5 rounded-[18px] bg-surface px-4 py-3.5 shadow-[0_1px_0_rgb(16_32_42/0.06)] md:flex-col md:gap-4 md:rounded-[22px] md:p-6"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-highlight text-lg font-black text-action md:h-11 md:w-11 md:text-xl">
                  {index + 1}
                </span>
                <div>
                  <h3 className="text-base font-extrabold md:text-xl">
                    {step.title}
                  </h3>
                  <p className="mt-0.5 text-sm leading-relaxed text-muted md:mt-2 md:text-base">
                    {step.body}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section
          aria-labelledby="link-title"
          className="mt-8 flex items-center gap-3.5 rounded-[20px] bg-ink p-4 text-white md:mt-12 md:gap-6 md:rounded-[24px] md:p-8"
        >
          <span
            aria-hidden="true"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-accent text-ink md:h-14 md:w-14 md:rounded-2xl"
          >
            <svg
              viewBox="0 0 24 24"
              className="h-[22px] w-[22px] md:h-7 md:w-7"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1" />
              <path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1" />
            </svg>
          </span>
          <div>
            <h2 id="link-title" className="text-base font-extrabold md:text-xl">
              Got a game link?
            </h2>
            <p className="mt-0.5 text-sm leading-relaxed text-white/85 md:mt-1 md:text-base">
              Open the link from your host's message to see tonight's game and
              check in.
            </p>
          </div>
        </section>
      </main>
    </div>
  )
}

function AccountMenu({
  account,
}: {
  account: AccountState & { reload: () => void }
}) {
  switch (account.status) {
    case 'loading':
      // Keeps the space so the header does not jump when the answer arrives.
      return <span className="inline-block min-h-11" aria-hidden="true" />
    case 'signedIn':
      return (
        <div className="flex min-w-0 items-center gap-2">
          <span className="hidden max-w-[240px] truncate text-sm font-bold text-muted sm:inline">
            {copy.account.signedInAs(account.account.email)}
          </span>
          <Button
            variant="secondary"
            className="min-h-11 rounded-xl px-3 text-sm md:px-4 md:text-base"
            onClick={async () => {
              await logout().catch(() => undefined)
              account.reload()
            }}
          >
            {copy.account.signOut}
          </Button>
        </div>
      )
    case 'signedOut':
    case 'error':
      return (
        <Link
          to="/login"
          className="inline-flex min-h-11 items-center rounded-xl border-2 border-ink/15 bg-surface px-3 text-sm font-extrabold text-ink hover:bg-highlight focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action md:px-4 md:text-base"
        >
          {copy.login.title}
        </Link>
      )
    default: {
      const unreachable: never = account
      return unreachable
    }
  }
}

// "Games you host": only for a signed-in account; the server answers with this caller's own games (Host board).
function HostedGames() {
  const load = useCallback((signal: AbortSignal) => getHostedGames(signal), [])
  const games = useAsync(load, (list) => list.length === 0)

  return (
    <section aria-labelledby="hosted-title" className="mb-8 md:mb-12">
      <h2
        id="hosted-title"
        className="px-1 text-[22px] font-black tracking-tight md:text-[28px]"
      >
        {copy.home.hostedTitle}
      </h2>
      <div className="mt-3">
        {(() => {
          switch (games.status) {
            case 'loading':
              return (
                <p role="status" className="text-base">
                  {copy.common.loading}
                </p>
              )
            case 'empty':
              return (
                <p role="status" className="px-1 text-base font-bold">
                  {copy.home.hostedEmpty}
                </p>
              )
            case 'success':
              return (
                <ul className="grid gap-2 md:grid-cols-2 md:gap-4">
                  {games.data.map((game) => (
                    <HostedGameRow key={game.id} game={game} />
                  ))}
                </ul>
              )
            case 'error':
              return <ErrorBanner onRetry={games.reload} />
            default: {
              const unreachable: never = games
              return unreachable
            }
          }
        })()}
      </div>
    </section>
  )
}

function HostedGameRow({ game }: { game: HostedGame }) {
  const time = formatGameTime(game.startsAt, game.endsAt)
  const status = GAME_STATUS[game.state]
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-[18px] bg-surface px-4 py-3.5">
      <div className="min-w-0">
        <p className="truncate text-base font-extrabold">{game.title}</p>
        <p className="text-sm text-muted">
          {time.day} · {time.start}
        </p>
        <span className="mt-1 inline-block rounded-full bg-highlight">
          <StatusBadge label={status.label} tone={status.tone} />
        </span>
      </div>
      <Link
        to={`/host/games/${game.id}`}
        className="inline-flex min-h-11 items-center rounded-xl bg-action px-4 text-base font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
      >
        {copy.home.manage}
      </Link>
    </li>
  )
}
