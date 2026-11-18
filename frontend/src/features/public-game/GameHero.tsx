import type { PublicGame } from '../../api/types'
import Button from '../../components/Button'
import StatusBadge from '../../components/StatusBadge'
import { copy } from '../../copy/en'
import { formatVndRange } from '../../lib/money'
import { GAME_STATUS, MATCH_FORMAT } from '../../lib/status'
import { formatGameWindow } from '../../lib/time'

const t = copy.publicGame

// Public facts of one game and the Join button (blueprint sections 6, 16). No names or private data.
export default function GameHero({ game }: { game: PublicGame }) {
  const status = GAME_STATUS[game.state]
  const price = game.pricePerPerson

  return (
    <section
      aria-labelledby="game-title"
      className="rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-ink/5 md:p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1
          id="game-title"
          className="text-[28px] leading-tight font-extrabold tracking-tight"
        >
          {game.title}
        </h1>
        <StatusBadge label={status.label} tone={status.tone} />
      </div>
      <p className="mt-2 text-base">{game.venue}</p>
      <p className="mt-1 text-base font-bold">
        {formatGameWindow(game.startsAt, game.endsAt)}{' '}
        <span className="text-sm font-normal">({t.timeZoneNote})</span>
      </p>

      <ul className="mt-5 grid grid-cols-2 gap-3">
        <Fact>{t.courts(game.courtCount)}</Fact>
        <Fact>{MATCH_FORMAT[game.matchFormat]}</Fact>
        <Fact wide>{t.minSets(game.minSets)}</Fact>
      </ul>

      {price && (
        <div className="mt-5 rounded-xl bg-highlight p-4">
          <h2 className="text-base font-bold">{t.priceTitle}</h2>
          <ul className="mt-1 text-base">
            {price.male && <li>{t.priceMale(formatVndRange(price.male))}</li>}
            {price.female && (
              <li>{t.priceFemale(formatVndRange(price.female))}</li>
            )}
          </ul>
          <p className="mt-1 text-sm">{t.priceNote}</p>
        </div>
      )}

      <div className="mt-6">
        {game.state === 'OPEN' ? (
          // Joining comes with guest check-in; until then the button is shown disabled (OD-13, owner 2026-11-18).
          <Button
            disabled
            aria-describedby="join-soon"
            className="w-full md:w-auto"
          >
            {t.join}
            <span
              id="join-soon"
              className="rounded-full bg-accent px-2 py-0.5 text-xs font-bold text-ink"
            >
              {copy.common.comingSoon}
            </span>
          </Button>
        ) : (
          <p className="text-base font-bold">{t.closedNote}</p>
        )}
      </div>
    </section>
  )
}

function Fact({ children, wide }: { children: string; wide?: boolean }) {
  return (
    <li
      className={`rounded-xl bg-background px-3 py-2 text-base font-bold ${wide ? 'col-span-2' : ''}`}
    >
      {children}
    </li>
  )
}
