import type { AttendanceState, HostBoardPlayer } from '../../api/types'
import PlayerChip from '../../components/PlayerChip'
import StatusBadge from '../../components/StatusBadge'
import WaitIndicator from '../../components/WaitIndicator'
import { copy } from '../../copy/en'
import { HOST_ATTENDANCE_STATUS } from '../../lib/status'

// One section per attendance state (blueprint sections 6, 11). Players arrive already in the board's order.
export default function PlayerList({
  state,
  players,
  asOf,
  minSets,
  note,
}: {
  state: AttendanceState
  players: HostBoardPlayer[]
  asOf: string
  minSets: number
  note?: string
}) {
  const status = HOST_ATTENDANCE_STATUS[state]
  return (
    <section aria-label={status.label} className="flex flex-col gap-2">
      <h3 className="flex items-center gap-2 text-lg font-black">
        <span className="rounded-full bg-highlight">
          <StatusBadge label={status.label} tone={status.tone} />
        </span>
        <span>{players.length}</span>
      </h3>
      {note && (
        <p role="status" className="text-base font-bold text-muted">
          {note}
        </p>
      )}
      <ul className="flex flex-col gap-2">
        {players.map((player) => (
          <li key={player.attendanceId}>
            <PlayerChip
              player={player}
              extra={
                <>
                  {state === 'READY' && (
                    <WaitIndicator
                      minutes={waitMinutes(asOf, player.availableSince)}
                    />
                  )}
                  <span className="text-sm font-bold text-subtle">
                    {copy.hostBoard.setsPlayed(player.matchesPlayed, minSets)}
                  </span>
                </>
              }
            />
          </li>
        ))}
      </ul>
    </section>
  )
}

// Whole minutes from the server's own clock (`asOf`), so phones with a wrong clock show the same number.
function waitMinutes(asOf: string, availableSince: string): number {
  const ms = new Date(asOf).getTime() - new Date(availableSince).getTime()
  return Math.max(0, Math.floor(ms / 60000))
}
