import type { StatusTone } from '../lib/status'

// Status as text plus an icon/dot and color, never color alone (blueprint section 6). Sits on a colored card.
export default function StatusBadge({
  label,
  tone,
}: {
  label: string
  tone: StatusTone
}) {
  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full bg-white py-1 pr-3 pl-2.5 text-sm font-extrabold md:text-[15px] ${tone === 'positive' ? 'text-action' : 'text-muted'}`}
    >
      {tone === 'positive' ? (
        <span
          aria-hidden="true"
          className="h-2.5 w-2.5 rounded-full bg-accent ring-4 ring-accent/35"
        />
      ) : (
        <svg
          viewBox="0 0 24 24"
          className="h-3 w-3"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          aria-hidden="true"
        >
          <rect x="5" y="11" width="14" height="10" rx="2" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
      )}
      {label}
    </span>
  )
}
