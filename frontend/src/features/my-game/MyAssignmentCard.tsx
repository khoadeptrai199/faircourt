import CourtLines from '../../components/CourtLines'
import StatusBadge from '../../components/StatusBadge'
import type { MyCard } from './myCard'

// One large card with the caller's own match: status, court, match number, arrival hint (blueprint sections 6, 16).
// Never shows opponents or partners (section 11).
export default function MyAssignmentCard({
  card,
}: {
  card: Extract<MyCard, { kind: 'assignment' }>
}) {
  return (
    <section
      aria-labelledby="my-game-court"
      className="relative flex flex-col items-start gap-4 overflow-hidden rounded-3xl bg-action p-6 text-white md:rounded-[32px] md:p-10"
    >
      <CourtLines className="pointer-events-none absolute -top-2 -right-10 h-40 w-64 opacity-20" />
      <div className="relative">
        <StatusBadge label={card.label} tone={card.tone} />
      </div>
      <p
        id="my-game-court"
        role="status"
        className="relative text-[32px] leading-[1.1] font-black tracking-[-0.03em] md:text-5xl"
      >
        {card.court}
      </p>
      <p className="relative text-xl font-extrabold">{card.matchNo}</p>
      <p className="relative text-base">{card.hint}</p>
    </section>
  )
}
