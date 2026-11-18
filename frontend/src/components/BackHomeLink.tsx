import { Link } from 'react-router'
import { copy } from '../copy/en'

export default function BackHomeLink() {
  return (
    <Link
      to="/"
      className="inline-flex min-h-[52px] items-center justify-center rounded-2xl bg-action px-6 text-[17px] font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action"
    >
      {copy.common.backHome}
    </Link>
  )
}
