import type { ReactNode } from 'react'
import type { HostBoardPlayer } from '../api/types'
import { copy } from '../copy/en'

// Host only: name, private level and a "Provisional" tag while the level is only self-reported (blueprint sections
// 3, 13). `extra` carries the wait chip and the sets progress.
export default function PlayerChip({
  player,
  extra,
}: {
  player: HostBoardPlayer
  extra?: ReactNode
}) {
  const t = copy.hostBoard
  const provisional =
    player.band !== null && player.bandStatus === 'SELF_REPORTED'
  return (
    <div className="flex min-h-11 flex-wrap items-center gap-x-2 gap-y-1 rounded-xl bg-surface px-3 py-2">
      <span className="text-base font-extrabold">{player.displayName}</span>
      <span className="rounded-full border-2 border-ink/20 px-2 text-sm font-bold text-muted">
        {player.band === null ? t.levelUnknown : t.level(player.band)}
      </span>
      {provisional && (
        <span className="rounded-full bg-accent/40 px-2 text-sm font-bold text-ink">
          {t.provisional}
        </span>
      )}
      {extra}
    </div>
  )
}
