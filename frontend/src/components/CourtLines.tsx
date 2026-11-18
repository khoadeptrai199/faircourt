// The FairCourt motif: a badminton court seen from above, thin lines. Decorative; color comes from the parent
// (currentColor), so it can sit on any card.
export default function CourtLines({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 360 220"
      preserveAspectRatio="none"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      aria-hidden="true"
    >
      <rect x="4" y="4" width="352" height="212" rx="4" />
      <line x1="4" y1="24" x2="356" y2="24" />
      <line x1="4" y1="196" x2="356" y2="196" />
      <line x1="180" y1="4" x2="180" y2="216" strokeDasharray="8 7" />
      <line x1="120" y1="24" x2="120" y2="196" />
      <line x1="240" y1="24" x2="240" y2="196" />
      <line x1="4" y1="110" x2="120" y2="110" />
      <line x1="240" y1="110" x2="356" y2="110" />
    </svg>
  )
}
