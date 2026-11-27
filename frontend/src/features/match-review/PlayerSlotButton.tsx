import type { HostBoardPlayer } from '../../api/types'
import PlayerChip from '../../components/PlayerChip'
import { sameSlot, type Slot } from './lineup'

// A player as a real button: tap to select, tap another to swap (blueprint section 16; works by keyboard, no
// drag-and-drop). Selected = thick ring + check icon, never color alone. Used by MatchProposal and BenchList.
export default function PlayerSlotButton({
  player,
  slot,
  selected,
  onTap,
  disabled = false,
}: {
  player: HostBoardPlayer
  slot: Slot
  selected: Slot | null
  onTap: (slot: Slot) => void
  disabled?: boolean
}) {
  const isSelected = selected !== null && sameSlot(selected, slot)
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      disabled={disabled}
      onClick={() => onTap(slot)}
      className={`relative flex min-h-11 w-full items-center rounded-xl text-left focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action disabled:cursor-not-allowed ${isSelected ? 'ring-4 ring-action' : 'ring-2 ring-ink/15'}`}
    >
      <PlayerChip player={player} />
      {isSelected && (
        <svg
          viewBox="0 0 24 24"
          className="absolute top-1/2 right-3 h-5 w-5 -translate-y-1/2 text-action"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="m5 12.5 4.5 4.5L19 7" />
        </svg>
      )}
    </button>
  )
}
