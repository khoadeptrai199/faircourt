import type { ReactNode } from 'react'
import type {
  HostBoardPlayer,
  MatchProposal as Proposal,
  ReasonCode,
  SuggestionWarning,
} from '../../api/types'
import PlayerSlotButton from './PlayerSlotButton'
import { copy } from '../../copy/en'
import type { CourtLineup, Slot, Team } from './lineup'

const t = copy.matchReview

const REASON_LABEL: Record<ReasonCode, string> = {
  SIMILAR_LEVEL: t.reasonSimilarLevel,
  BALANCED_TEAMS: t.reasonBalancedTeams,
  LONG_WAIT: t.reasonLongWait,
}

const WARNING_LABEL: Record<SuggestionWarning, string> = {
  WIDE_SKILL_GAP: t.warnWideGap,
  UNKNOWN_LEVEL: t.warnUnknownLevel,
}

// One court: Team A vs Team B with names and levels, reason tags and warnings (blueprint sections 6, 16). The
// players are tap-to-swap buttons; `footer` carries Confirm and its states. After a swap the tags and warnings no
// longer describe the lineup, so they are hidden (design D-4).
export default function MatchProposal({
  proposal,
  court,
  players,
  selected,
  onTap,
  frozen,
  footer,
}: {
  proposal: Proposal
  court: CourtLineup
  players: Map<string, HostBoardPlayer>
  selected: Slot | null
  onTap: (slot: Slot) => void
  frozen: boolean
  footer: ReactNode
}) {
  const h = copy.hostBoard
  const locked = court.saved !== null || frozen
  const team = (label: string, key: Team) => (
    <TeamColumn
      label={label}
      courtNo={court.courtNo}
      team={key}
      ids={court[key]}
      players={players}
      selected={selected}
      onTap={onTap}
      disabled={locked}
    />
  )
  return (
    <article className="flex flex-col gap-3 rounded-[20px] border-2 border-ink/15 bg-surface p-4">
      <h2 className="text-lg font-black">{h.court(court.courtNo)}</h2>
      {team(h.teamA, 'A')}
      <p className="text-center text-sm font-extrabold text-subtle">{t.vs}</p>
      {team(h.teamB, 'B')}
      {court.edited ? (
        <p className="text-base font-bold text-muted">{t.edited}</p>
      ) : (
        <>
          {proposal.reasonCodes.length > 0 && (
            <ul className="flex flex-wrap gap-2">
              {proposal.reasonCodes.map((code) => (
                <li
                  key={code}
                  className="rounded-full border-2 border-ink/20 px-3 py-1 text-sm font-bold text-ink"
                >
                  {REASON_LABEL[code]}
                </li>
              ))}
            </ul>
          )}
          {proposal.warnings.length > 0 && (
            <ul className="flex flex-col gap-2">
              {proposal.warnings.map((code) => (
                <li
                  key={code}
                  className="flex items-start gap-2 text-base font-bold text-error"
                >
                  <svg
                    viewBox="0 0 24 24"
                    className="mt-0.5 h-5 w-5 shrink-0"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.4"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M12 3 2.5 20h19z" />
                    <path d="M12 10v4" />
                    <path d="M12 17.5v.01" />
                  </svg>
                  <span>{WARNING_LABEL[code]}</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {footer}
    </article>
  )
}

function TeamColumn({
  label,
  courtNo,
  team,
  ids,
  players,
  selected,
  onTap,
  disabled,
}: {
  label: string
  courtNo: number
  team: Team
  ids: string[]
  players: Map<string, HostBoardPlayer>
  selected: Slot | null
  onTap: (slot: Slot) => void
  disabled: boolean
}) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-bold text-subtle">{label}</h3>
      {ids.map((id, index) => {
        const player = players.get(id)
        return player ? (
          <PlayerSlotButton
            key={id}
            player={player}
            slot={{ kind: 'court', courtNo, team, index }}
            selected={selected}
            onTap={onTap}
            disabled={disabled}
          />
        ) : null
      })}
    </div>
  )
}
