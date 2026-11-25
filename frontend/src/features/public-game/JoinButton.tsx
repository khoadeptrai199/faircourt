import { Link, useNavigate } from 'react-router'
import { joinAsMember } from '../../api/attendance'
import Button from '../../components/Button'
import { copy } from '../../copy/en'
import { useAccount } from '../../hooks/useAccount'
import { useMutation } from '../../hooks/useMutation'

const t = copy.publicGame

const sizeClass =
  'min-h-14 w-full gap-2 rounded-2xl px-4 text-lg md:min-h-16 md:rounded-[20px] md:text-xl'

// Join button of an open game. Signed in: joins as a member at once, then opens My game. Not signed in (or the
// account check failed): the link to guest check-in, as before (Own attendance status, design section 7).
export default function JoinButton({
  gameId,
  publicCode,
}: {
  gameId: string
  publicCode: string
}) {
  const account = useAccount()
  const join = useMutation(joinAsMember)
  const navigate = useNavigate()

  if (account.status !== 'signedIn') {
    return (
      <Link
        to={`/g/${encodeURIComponent(publicCode)}/join`}
        className={`inline-flex items-center justify-center bg-action font-extrabold text-white hover:bg-action-dark focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-action ${sizeClass}`}
      >
        {t.join}
      </Link>
    )
  }

  const saving = join.status === 'saving'

  async function onJoin() {
    const result = await join.run(gameId)
    if (result)
      navigate(
        `/my/games/${encodeURIComponent(gameId)}?code=${encodeURIComponent(publicCode)}`,
      )
  }

  return (
    <div className="flex flex-col gap-2">
      <Button
        disabled={saving}
        onClick={() => void onJoin()}
        className={sizeClass}
      >
        {saving ? copy.join.joining : t.join}
      </Button>
      {(join.status === 'conflict' || join.status === 'error') && (
        <p
          role="alert"
          className="rounded-xl bg-error-soft px-3.5 py-3 text-sm font-bold text-error"
        >
          {copy.common.errorTitle}
        </p>
      )}
    </div>
  )
}
