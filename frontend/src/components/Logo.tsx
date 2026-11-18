import { Link } from 'react-router'

// Brand mark (court-green tile with a shuttle) + wordmark; links home.
export default function Logo() {
  return (
    <Link
      to="/"
      className="flex items-center gap-2 rounded-xl text-lg font-black tracking-tight text-ink focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action md:gap-2.5 md:text-[22px]"
    >
      <svg
        viewBox="0 0 40 40"
        className="h-7 w-7 md:h-[34px] md:w-[34px]"
        aria-hidden="true"
      >
        <rect width="40" height="40" rx="11" className="fill-action" />
        <path d="M13 11 L20 25 L27 11 Z" className="fill-white" />
        <path
          d="M17 11 L20 25 M23 11 L20 25"
          className="stroke-action"
          strokeWidth="1.5"
        />
        <circle cx="20" cy="28" r="4" className="fill-accent" />
      </svg>
      FairCourt
    </Link>
  )
}
