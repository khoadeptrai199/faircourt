import type { HostBoardPlayer } from '../../api/types'
import { copy } from '../../copy/en'
import type { Slot } from './lineup'
import PlayerSlotButton from './PlayerSlotButton'

// Ready players the suggester did not pick, longest waiting first. Tap one, then a court player, to swap them in
// (blueprint section 0 step 3, section 14). Hidden when empty.
export default function BenchList({
  ids,
  players,
  selected,
  onTap,
  disabled,
}: {
  ids: string[]
  players: Map<string, HostBoardPlayer>
  selected: Slot | null
  onTap: (slot: Slot) => void
  disabled: boolean
}) {
  if (ids.length === 0) {
    return null
  }
  return (
    <section className="flex flex-col gap-3 rounded-[20px] border-2 border-ink/15 bg-surface p-4">
      <h2 className="text-lg font-black">{copy.matchReview.benchTitle}</h2>
      <div className="grid gap-2 md:grid-cols-2">
        {ids.map((id, index) => {
          const player = players.get(id)
          return player ? (
            <PlayerSlotButton
              key={id}
              player={player}
              slot={{ kind: 'bench', index }}
              selected={selected}
              onTap={onTap}
              disabled={disabled}
            />
          ) : null
        })}
      </div>
    </section>
  )
}
