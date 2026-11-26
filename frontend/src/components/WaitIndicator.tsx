import { copy } from '../copy/en'

// Host only: how long a Ready player has waited, in whole minutes (text, never color alone).
export default function WaitIndicator({ minutes }: { minutes: number }) {
  return (
    <span className="inline-flex items-center rounded-full bg-highlight px-2.5 py-0.5 text-sm font-extrabold text-action-dark">
      {copy.hostBoard.waiting(minutes)}
    </span>
  )
}
