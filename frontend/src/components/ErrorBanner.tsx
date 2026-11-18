import { copy } from '../copy/en'
import Button from './Button'

// A failed request (including 503 when a service is down) with a Retry button (blueprint section 16).
export default function ErrorBanner({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col gap-4 rounded-[20px] border-2 border-error bg-surface p-5"
    >
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-error-soft text-error"
        >
          <svg
            viewBox="0 0 24 24"
            className="h-[22px] w-[22px]"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M12 3 2.5 20h19z" />
            <path d="M12 10v4" />
            <path d="M12 17.5v.01" />
          </svg>
        </span>
        <p className="text-lg font-extrabold text-error">
          {copy.common.errorTitle}
        </p>
      </div>
      <Button variant="secondary" onClick={onRetry} className="min-h-[52px]">
        <svg
          viewBox="0 0 24 24"
          className="h-[18px] w-[18px]"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.6"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M20 11a8 8 0 1 0-2.3 5.7" />
          <path d="M20 4v7h-7" />
        </svg>
        {copy.common.retry}
      </Button>
    </div>
  )
}
