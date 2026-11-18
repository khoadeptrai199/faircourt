import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import type { PublicGame } from '../../api/types'
import CourtLines from '../../components/CourtLines'
import StatusBadge from '../../components/StatusBadge'
import { copy } from '../../copy/en'
import { formatVndRange } from '../../lib/money'
import { GAME_STATUS, MATCH_FORMAT } from '../../lib/status'
import { formatGameTime } from '../../lib/time'

const t = copy.publicGame

// Public facts of one game and the Join button (blueprint sections 6, 16). No names or private data.
// Phone: one column (hero, time, facts, price) with Join fixed at the bottom. 768px+: hero on the left,
// facts + price + Join on the right.
export default function GameHero({ game }: { game: PublicGame }) {
  const status = GAME_STATUS[game.state]
  const price = game.pricePerPerson
  const time = formatGameTime(game.startsAt, game.endsAt)
  const open = game.state === 'OPEN'
  const { publicCode = '' } = useParams()

  return (
    <div className="flex flex-col gap-3 md:grid md:grid-cols-12 md:gap-5">
      <section
        aria-labelledby="game-title"
        className={`relative flex flex-col gap-3.5 overflow-hidden rounded-3xl p-5 text-white md:col-span-7 md:min-h-[520px] md:justify-between md:rounded-[32px] md:p-10 ${open ? 'bg-action' : 'bg-muted'}`}
      >
        <CourtLines
          className={`pointer-events-none absolute -top-2.5 -right-10 h-[210px] w-[300px] md:-right-16 md:top-auto md:-bottom-8 md:h-[360px] md:w-[560px] ${open ? 'opacity-20' : 'opacity-15'}`}
        />
        <div className="relative flex flex-col items-start gap-3.5 md:gap-[18px]">
          <StatusBadge label={status.label} tone={status.tone} />
          <h1
            id="game-title"
            className="text-[32px] leading-[1.05] font-black tracking-[-0.03em] md:text-6xl md:leading-none md:tracking-[-0.04em]"
          >
            {game.title}
          </h1>
          <p className="flex items-center gap-1.5 text-base font-medium md:gap-2 md:text-xl">
            <svg
              viewBox="0 0 24 24"
              className="h-[18px] w-[18px] shrink-0 md:h-[22px] md:w-[22px]"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z" />
              <circle cx="12" cy="9.5" r="2.5" />
            </svg>
            {game.venue}
          </p>
        </div>
        {/* Desktop: the time is part of the hero card (big, like a scoreboard). */}
        <div className="relative hidden flex-col gap-1 md:flex">
          <span className="text-base font-bold tracking-[0.08em] text-highlight uppercase">
            {time.day} · {t.timeZoneNote}
          </span>
          <span className="text-[72px] leading-none font-black tracking-[-0.04em] tabular-nums">
            {time.start}
            <span className="font-bold text-white/60">–</span>
            {time.end}
          </span>
        </div>
      </section>

      <div className="flex flex-col gap-3 md:col-span-5 md:gap-4">
        {/* Phone: the time is its own card below the hero. */}
        <section
          aria-label={t.timeZoneNote}
          className="flex items-center justify-between gap-3 rounded-[20px] bg-surface px-5 py-4 shadow-[0_1px_0_rgb(16_32_42/0.06)] md:hidden"
        >
          <div className="flex flex-col gap-0.5">
            <span
              className={`text-sm font-bold tracking-[0.06em] uppercase ${open ? 'text-action' : 'text-subtle'}`}
            >
              {time.day}
            </span>
            <span
              className={`text-[34px] leading-[1.1] font-black tracking-[-0.03em] tabular-nums ${open ? '' : 'text-subtle'}`}
            >
              {time.start}
              <span className="font-bold text-subtle">–</span>
              {time.end}
            </span>
          </div>
          <span className="text-right text-[13px] leading-snug font-semibold text-subtle">
            {t.timeZoneNote}
          </span>
        </section>

        <ul className="grid grid-cols-3 gap-2 md:gap-3">
          <Tile>
            <Big open={open}>{game.courtCount}</Big>
            <Label>{t.courtWord(game.courtCount)}</Label>
          </Tile>
          <Tile>
            <svg
              viewBox="0 0 36 30"
              className={`h-[30px] w-9 md:h-[34px] md:w-10 ${open ? 'stroke-action' : 'stroke-subtle'}`}
              fill="none"
              strokeWidth="2.4"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <circle cx="9" cy="8" r="4" />
              <circle cx="27" cy="8" r="4" />
              <path d="M2 27c0-4.4 3.1-8 7-8s7 3.6 7 8" />
              <path d="M20 27c0-4.4 3.1-8 7-8s7 3.6 7 8" />
            </svg>
            <Label>{MATCH_FORMAT[game.matchFormat]}</Label>
          </Tile>
          <Tile>
            <Big open={open}>{game.minSets}+</Big>
            <Label>{t.minSetsWord}</Label>
          </Tile>
        </ul>

        {price && (
          <section
            aria-labelledby="price-title"
            className="flex flex-col gap-2.5 rounded-[20px] bg-highlight px-5 py-4 md:gap-3 md:rounded-[22px] md:px-6 md:py-[22px]"
          >
            <h2
              id="price-title"
              className="text-sm font-extrabold tracking-[0.06em] text-action-dark uppercase md:text-[15px]"
            >
              {t.priceTitle}
            </h2>
            {price.male && (
              <PriceLine
                label={t.priceMale}
                value={formatVndRange(price.male)}
              />
            )}
            {price.male && price.female && (
              <div className="h-px bg-action/20" />
            )}
            {price.female && (
              <PriceLine
                label={t.priceFemale}
                value={formatVndRange(price.female)}
              />
            )}
            <p className="text-sm text-muted md:text-[15px]">{t.priceNote}</p>
          </section>
        )}

        {open ? (
          // Phone: fixed to the bottom of the screen so it is always within reach (owner 2026-11-18).
          // The Join button leads to the guest check-in page (Guest check-in; replaces the disabled button of OD-13).
          <div className="fixed inset-x-0 bottom-0 z-10 border-t border-ink/10 bg-background/95 px-4 pt-3.5 pb-5 md:static md:border-0 md:bg-transparent md:p-0">
            <Link
              to={`/g/${encodeURIComponent(publicCode)}/join`}
              className="inline-flex min-h-14 w-full items-center justify-center gap-2 rounded-2xl bg-action px-4 text-lg font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action md:min-h-16 md:rounded-[20px] md:text-xl"
            >
              {t.join}
            </Link>
          </div>
        ) : (
          <p
            role="status"
            className="rounded-2xl border-2 border-dashed border-ink/25 bg-surface p-4 text-center text-base font-bold"
          >
            {t.closedNote}
          </p>
        )}
      </div>
    </div>
  )
}

function Tile({ children }: { children: ReactNode }) {
  return (
    <li className="flex flex-col gap-1.5 rounded-[18px] bg-surface px-3 py-3.5 shadow-[0_1px_0_rgb(16_32_42/0.06)] md:gap-1 md:rounded-[22px] md:px-4 md:py-5">
      {children}
    </li>
  )
}

function Big({ open, children }: { open: boolean; children: ReactNode }) {
  return (
    <span
      className={`text-4xl leading-none font-black tracking-[-0.03em] md:text-[44px] ${open ? 'text-action' : 'text-subtle'}`}
    >
      {children}
    </span>
  )
}

function Label({ children }: { children: string }) {
  return (
    <span className="text-sm leading-tight font-bold md:text-[15px]">
      {children}
    </span>
  )
}

function PriceLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="text-base font-semibold md:text-[17px]">{label}</span>
      <span className="text-lg font-extrabold tabular-nums md:text-[22px]">
        {value}
      </span>
    </div>
  )
}
