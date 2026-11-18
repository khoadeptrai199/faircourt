import type { ReactNode } from 'react'
import CourtLines from './CourtLines'
import Logo from './Logo'

// Shared frame of the sign-in pages (login, forgot password, reset password): logo header and a card with the
// court-lines strip and the page title. Same look as the public game page (Look and feel).
export default function AuthPageShell({
  titleId,
  title,
  children,
}: {
  titleId: string
  title: string
  children: ReactNode
}) {
  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1080px] items-center px-5 pt-4 pb-2 md:px-6 md:pt-6 md:pb-3">
        <Logo />
      </header>
      <main className="mx-auto max-w-[480px] px-4 pt-2 pb-12 md:pt-8">
        <section
          aria-labelledby={titleId}
          className="overflow-hidden rounded-3xl bg-surface shadow-[0_1px_0_rgb(16_32_42/0.06)]"
        >
          <div className="relative overflow-hidden bg-action px-6 py-6 text-white">
            <CourtLines className="pointer-events-none absolute -top-2 -right-10 h-40 w-64 opacity-20" />
            <h1
              id={titleId}
              className="relative text-[32px] leading-none font-black tracking-[-0.03em]"
            >
              {title}
            </h1>
          </div>
          <div className="flex flex-col gap-4 p-6">{children}</div>
        </section>
      </main>
    </div>
  )
}
