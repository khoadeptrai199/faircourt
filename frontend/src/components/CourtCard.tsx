import type { ReactNode } from 'react'
import type { CourtMatchState } from '../api/types'
import { copy } from '../copy/en'
import { COURT_MATCH_STATUS } from '../lib/status'
import StatusBadge from './StatusBadge'

export type CourtCardTeams = { A: string[]; B: string[] }

// One court: "Free", or its current match with Team A / Team B (blueprint sections 6, 16). Names are looked up by the
// page from the board's players; this card only shows them.
export default function CourtCard({
  courtNo,
  match,
  actions,
}: {
  courtNo: number
  actions?: ReactNode
  match: {
    matchNo: number
    state: CourtMatchState
    teams: CourtCardTeams
  } | null
}) {
  const t = copy.hostBoard
  return (
    <article className="flex min-h-36 w-64 shrink-0 snap-start flex-col gap-2 rounded-[20px] border-2 border-ink/15 bg-surface p-4 md:w-auto">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-lg font-black">{t.court(courtNo)}</h3>
        {match && (
          <span className="rounded-full bg-highlight">
            <StatusBadge
              label={COURT_MATCH_STATUS[match.state].label}
              tone={COURT_MATCH_STATUS[match.state].tone}
            />
          </span>
        )}
      </div>
      {match ? (
        <dl className="flex flex-col gap-2">
          <Team label={t.teamA} names={match.teams.A} />
          <Team label={t.teamB} names={match.teams.B} />
        </dl>
      ) : (
        <p className="text-base font-bold text-muted">{t.courtFree}</p>
      )}
      {match && actions}
    </article>
  )
}

function Team({ label, names }: { label: string; names: string[] }) {
  return (
    <div>
      <dt className="text-sm font-bold text-subtle">{label}</dt>
      <dd className="text-base font-extrabold">{names.join(', ')}</dd>
    </div>
  )
}
