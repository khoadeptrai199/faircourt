import { Link } from 'react-router'
import { copy } from '../copy/en'

export default function BackHomeLink() {
  return (
    <Link
      to="/"
      className="inline-flex min-h-11 items-center justify-center rounded-xl bg-action px-4 text-base font-bold text-white focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
    >
      {copy.common.backHome}
    </Link>
  )
}
