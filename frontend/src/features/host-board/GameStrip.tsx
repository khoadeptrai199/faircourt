import { Link } from 'react-router'
import type { HostBoardGame } from '../../api/types'
import StatusBadge from '../../components/StatusBadge'
import { copy } from '../../copy/en'
import { GAME_STATUS, MATCH_FORMAT } from '../../lib/status'
import { formatGameTime } from '../../lib/time'

// Top strip of the host board: this game's title, time, venue, courts and format, with a link to the public page.
export default function GameStrip({ game }: { game: HostBoardGame }) {
  const time = formatGameTime(game.startsAt, game.endsAt)
  const status = GAME_STATUS[game.state]
  return (
    <section
      aria-label={game.title}
      className="flex flex-col gap-1 rounded-[20px] bg-ink p-4 text-white"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-xl font-black">{game.title}</h2>
        <StatusBadge label={status.label} tone={status.tone} />
      </div>
      <p className="text-base font-bold">
        {time.day} · {time.start}–{time.end} · {game.venue}
      </p>
      <p className="text-base">
        {game.courtCount} {copy.publicGame.courtWord(game.courtCount)} ·{' '}
        {MATCH_FORMAT[game.matchFormat]}
      </p>
      <Link
        to={`/g/${game.publicCode}`}
        className="mt-1 inline-flex min-h-11 items-center self-start font-extrabold text-highlight underline focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-highlight"
      >
        {copy.hostBoard.toPublicPage}
      </Link>
    </section>
  )
}
