import type { AttendanceState } from '../../api/types'
import CourtLines from '../../components/CourtLines'
import StatusBadge from '../../components/StatusBadge'
import { copy } from '../../copy/en'
import { ATTENDANCE_STATUS } from '../../lib/status'

// One large card with the caller's own state (blueprint section 16). Status is text + colour via StatusBadge.
// No court or match yet: the assignment comes with the own-assignment feature.
export default function StateCard({ state }: { state: AttendanceState }) {
  const status = ATTENDANCE_STATUS[state]
  const positive = status.tone === 'positive'

  return (
    <section
      aria-labelledby="my-game-state"
      className={`relative flex flex-col items-start gap-4 overflow-hidden rounded-3xl p-6 text-white md:rounded-[32px] md:p-10 ${positive ? 'bg-action' : 'bg-muted'}`}
    >
      <CourtLines
        className={`pointer-events-none absolute -top-2 -right-10 h-40 w-64 ${positive ? 'opacity-20' : 'opacity-15'}`}
      />
      <div className="relative">
        <StatusBadge label={status.label} tone={status.tone} />
      </div>
      <p
        id="my-game-state"
        role="status"
        className="relative text-[32px] leading-[1.1] font-black tracking-[-0.03em] md:text-5xl"
      >
        {bodyOf(state) ?? status.label}
      </p>
    </section>
  )
}

function bodyOf(state: AttendanceState): string | null {
  switch (state) {
    case 'PENDING':
      return copy.myGame.pendingBody
    case 'READY':
      return copy.myGame.readyBody
    case 'ASSIGNED':
    case 'PLAYING':
    case 'RESTING':
    case 'LEFT':
      return null
    default: {
      const unreachable: never = state
      return unreachable
    }
  }
}
