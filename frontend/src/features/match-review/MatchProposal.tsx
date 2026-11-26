import type {
  HostBoardPlayer,
  MatchProposal as Proposal,
  ReasonCode,
  SuggestionWarning,
} from '../../api/types'
import PlayerChip from '../../components/PlayerChip'
import { copy } from '../../copy/en'

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

// One proposed court: Team A vs Team B with the players' names and levels, reason tags and warnings (blueprint
// sections 6, 16). Read only: swap and confirm come with Match confirm.
export default function MatchProposal({
  proposal,
  players,
}: {
  proposal: Proposal
  players: Map<string, HostBoardPlayer>
}) {
  const h = copy.hostBoard
  return (
    <article className="flex flex-col gap-3 rounded-[20px] border-2 border-ink/15 bg-surface p-4">
      <h2 className="text-lg font-black">{h.court(proposal.courtNo)}</h2>
      <Team label={h.teamA} ids={proposal.teams.A} players={players} />
      <p className="text-center text-sm font-extrabold text-subtle">{t.vs}</p>
      <Team label={h.teamB} ids={proposal.teams.B} players={players} />
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
    </article>
  )
}

function Team({
  label,
  ids,
  players,
}: {
  label: string
  ids: string[]
  players: Map<string, HostBoardPlayer>
}) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-bold text-subtle">{label}</h3>
      {ids.map((id) => {
        const player = players.get(id)
        return player ? <PlayerChip key={id} player={player} /> : null
      })}
    </div>
  )
}
