import type { StatusTone } from '../lib/status'

const tones: Record<StatusTone, string> = {
  positive: 'bg-highlight text-action',
  neutral: 'bg-ink/10 text-ink',
}

// Status as text plus color, never color alone (blueprint section 6).
export default function StatusBadge({
  label,
  tone,
}: {
  label: string
  tone: StatusTone
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-bold ${tones[tone]}`}
    >
      {label}
    </span>
  )
}
