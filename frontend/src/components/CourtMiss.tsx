// "Not found" picture: an empty court and a shuttle that landed outside it. Decorative.
export default function CourtMiss({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 220 150"
      className={className}
      fill="none"
      aria-hidden="true"
    >
      <g className="stroke-action" strokeWidth="3">
        <rect x="10" y="20" width="200" height="120" rx="6" strokeWidth="4" />
        <line x1="10" y1="34" x2="210" y2="34" />
        <line x1="10" y1="126" x2="210" y2="126" />
        <line x1="110" y1="20" x2="110" y2="140" strokeDasharray="7 6" />
        <line x1="70" y1="34" x2="70" y2="126" />
        <line x1="150" y1="34" x2="150" y2="126" />
      </g>
      <g className="stroke-ink" strokeWidth="2.5">
        <path
          d="M168 58 L182 86 L196 58 Z"
          className="fill-white"
          strokeLinejoin="round"
          transform="rotate(28 182 72)"
        />
        <circle cx="176" cy="93" r="7" className="fill-accent" />
        <path
          d="M150 104 q12 -2 20 -8"
          strokeWidth="2"
          strokeDasharray="3 4"
          strokeLinecap="round"
        />
      </g>
    </svg>
  )
}
