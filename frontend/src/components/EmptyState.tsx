import type { ReactNode } from 'react'

// Friendly message when there is nothing to show (e.g. unknown link), with an optional action.
export default function EmptyState({
  title,
  body,
  action,
}: {
  title: string
  body?: string
  action?: ReactNode
}) {
  return (
    <section className="rounded-2xl bg-surface p-4 text-center shadow-sm ring-1 ring-ink/5 md:p-6">
      <h1 className="text-[28px] leading-tight font-extrabold tracking-tight">
        {title}
      </h1>
      {body && <p className="mt-2 text-base leading-relaxed">{body}</p>}
      {action && <div className="mt-5">{action}</div>}
    </section>
  )
}
