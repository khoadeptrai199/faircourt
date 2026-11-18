import type { ReactNode } from 'react'

// Friendly message when there is nothing to show (e.g. unknown link), with an optional picture and action.
export default function EmptyState({
  title,
  body,
  illustration,
  action,
}: {
  title: string
  body?: string
  illustration?: ReactNode
  action?: ReactNode
}) {
  return (
    <section className="flex flex-col items-center gap-5 px-2 py-10 text-center md:py-16">
      {illustration}
      <h1 className="text-[32px] leading-tight font-black tracking-tight">
        {title}
      </h1>
      {body && (
        <p className="max-w-[280px] text-base leading-relaxed text-muted">
          {body}
        </p>
      )}
      {action}
    </section>
  )
}
