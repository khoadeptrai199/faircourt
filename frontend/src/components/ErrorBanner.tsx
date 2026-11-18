import { copy } from '../copy/en'
import Button from './Button'

// A failed request (including 503 when a service is down) with a Retry button (blueprint section 16).
export default function ErrorBanner({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-2xl border-2 border-error bg-surface p-4 md:flex-row md:items-center md:justify-between md:p-6"
    >
      <p className="text-base font-bold text-error">{copy.common.errorTitle}</p>
      <Button variant="secondary" onClick={onRetry}>
        {copy.common.retry}
      </Button>
    </div>
  )
}
