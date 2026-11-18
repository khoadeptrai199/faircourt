// Home page at "/" (owner-approved 2026-11-12; not part of blueprint section 16 routes).
// 2026-11-18: "session" -> "game" and a single contest -> "match" (owner decision, OD-22).
// Only blueprint copy plus short explanatory text. "Host login" stays disabled until the
// identity slice (blueprint section 17, step 2) ships the real login.

const steps = [
  {
    title: 'Join a game',
    body: 'Open the link your host shares and check in.',
  },
  {
    title: 'Find a fair match',
    body: 'Matches are suggested by level and by who has waited longest.',
  },
  {
    title: 'Keep playing',
    body: 'See your court when it is your turn. The host makes the final call.',
  },
]

export default function HomePage() {
  return (
    <div className="min-h-dvh bg-background text-ink">
      <header className="mx-auto flex max-w-[1080px] items-center justify-between gap-4 px-4 py-4 md:px-6">
        <a
          href="/"
          className="flex items-center gap-2 rounded-xl text-xl font-extrabold tracking-tight focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
        >
          <ShuttleMark className="h-9 w-9" />
          FairCourt
        </a>
        <HostLoginButton />
      </header>

      <main className="mx-auto max-w-[1080px] px-4 pb-12 md:px-6">
        <section
          aria-labelledby="home-title"
          className="relative overflow-hidden rounded-3xl bg-action px-6 py-10 text-white md:px-12 md:py-16"
        >
          <CourtLines className="pointer-events-none absolute -right-16 -bottom-10 h-72 w-[28rem] opacity-25 md:right-0 md:h-96 md:w-[36rem]" />
          <div className="relative max-w-[560px]">
            <p className="inline-flex items-center gap-2 rounded-full bg-accent px-3 py-1 text-sm font-bold text-ink">
              <span aria-hidden="true">🏸</span> Badminton doubles, made fair
            </p>
            <h1
              id="home-title"
              className="mt-5 text-4xl leading-[1.1] font-extrabold tracking-tight md:text-6xl"
            >
              Join a game.
              <br />
              Find a fair match.
              <br />
              <span className="text-accent">Keep playing.</span>
            </h1>
            <p className="mt-5 text-lg leading-relaxed text-white md:text-xl">
              FairCourt helps your badminton group form fair doubles matches, so
              everyone gets a good match.
            </p>
          </div>
        </section>

        <section aria-labelledby="how-title" className="mt-10 md:mt-14">
          <h2
            id="how-title"
            className="text-[28px] leading-tight font-extrabold tracking-tight"
          >
            How it works
          </h2>
          <ol className="mt-5 grid gap-4 md:grid-cols-3 md:gap-6">
            {steps.map((step, index) => (
              <li
                key={step.title}
                className="rounded-2xl bg-surface p-4 shadow-sm ring-1 ring-ink/5 md:p-6"
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-full bg-highlight text-xl font-extrabold text-action">
                  {index + 1}
                </span>
                <h3 className="mt-4 text-xl font-bold">{step.title}</h3>
                <p className="mt-2 text-base leading-relaxed">{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        <section
          aria-labelledby="link-title"
          className="mt-10 flex flex-col gap-3 rounded-2xl bg-highlight p-6 md:mt-14 md:flex-row md:items-center md:gap-6 md:p-8"
        >
          <span
            aria-hidden="true"
            className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-surface text-3xl"
          >
            🔗
          </span>
          <div>
            <h2 id="link-title" className="text-xl font-bold">
              Got a game link?
            </h2>
            <p className="mt-1 text-base leading-relaxed">
              Open the link from your host's message to see tonight's game and
              check in.
            </p>
          </div>
        </section>
      </main>
    </div>
  )
}

function HostLoginButton() {
  return (
    <button
      type="button"
      disabled
      aria-describedby="host-login-soon"
      className="inline-flex min-h-11 cursor-not-allowed items-center gap-2 rounded-xl border-2 border-ink/20 bg-surface px-4 text-base font-bold text-ink/70"
    >
      Host login
      <span
        id="host-login-soon"
        className="rounded-full bg-accent px-2 py-0.5 text-xs font-bold text-ink"
      >
        Coming soon
      </span>
    </button>
  )
}

function ShuttleMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true">
      <rect width="40" height="40" rx="12" fill="#157B63" />
      <path d="M13 11 L20 25 L27 11 Z" fill="#FFFFFF" />
      <path
        d="M17 11 L20 25 M23 11 L20 25"
        stroke="#157B63"
        strokeWidth="1.5"
      />
      <circle cx="20" cy="28" r="4" fill="#F9C94D" />
    </svg>
  )
}

function CourtLines({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 440 300"
      className={className}
      fill="none"
      stroke="#FFFFFF"
      strokeWidth="4"
      aria-hidden="true"
    >
      <rect x="10" y="10" width="420" height="280" rx="6" />
      <line x1="10" y1="40" x2="430" y2="40" />
      <line x1="10" y1="260" x2="430" y2="260" />
      <line x1="220" y1="10" x2="220" y2="290" strokeDasharray="10 8" />
      <line x1="150" y1="40" x2="150" y2="260" />
      <line x1="290" y1="40" x2="290" y2="260" />
      <line x1="10" y1="150" x2="150" y2="150" />
      <line x1="290" y1="150" x2="430" y2="150" />
    </svg>
  )
}
