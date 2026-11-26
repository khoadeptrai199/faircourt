import { useState } from 'react'
import type { AttendanceState, AttendanceTarget } from '../../api/types'
import Button from '../../components/Button'
import ConfirmDialog from '../../components/ConfirmDialog'
import { copy } from '../../copy/en'

const t = copy.hostBoard

type ActionId = 'approve' | 'reject' | 'markLeft'

type Action = {
  id: ActionId
  target: AttendanceTarget
  primary: boolean
  confirm: boolean
}

// Which buttons a row gets, by its state (design section 7). RESTING, ASSIGNED, PLAYING and LEFT have none.
const ACTIONS: Record<AttendanceState, Action[]> = {
  PENDING: [
    { id: 'approve', target: 'READY', primary: true, confirm: false },
    { id: 'reject', target: 'LEFT', primary: false, confirm: true },
  ],
  READY: [{ id: 'markLeft', target: 'LEFT', primary: false, confirm: true }],
  RESTING: [],
  ASSIGNED: [],
  PLAYING: [],
  LEFT: [],
}

const LABEL: Record<ActionId, string> = {
  approve: t.approve,
  reject: t.reject,
  markLeft: t.markLeft,
}

// Host buttons for one player row. Approve acts at once; Reject and Mark as left ask first (both are final).
export default function AttendanceActions({
  name,
  state,
  gameClosed,
  saving,
  busyHere,
  onChange,
}: {
  name: string
  state: AttendanceState
  gameClosed: boolean
  saving: boolean
  busyHere: boolean
  onChange: (target: AttendanceTarget) => void
}) {
  const [asking, setAsking] = useState<ActionId | null>(null)
  const actions = gameClosed ? [] : ACTIONS[state]
  if (actions.length === 0) return null

  const pending = actions.find((a) => a.id === asking)
  const texts =
    asking === 'reject'
      ? { title: t.rejectTitle(name), body: t.rejectBody(name) }
      : { title: t.leftTitle(name), body: t.leftBody(name) }

  return (
    <div className="flex flex-wrap gap-2 md:ml-auto">
      {actions.map((action) => (
        <Button
          key={action.id}
          variant={action.primary ? 'primary' : 'secondary'}
          disabled={saving}
          onClick={() =>
            action.confirm ? setAsking(action.id) : onChange(action.target)
          }
        >
          {busyHere ? t.saving : LABEL[action.id]}
        </Button>
      ))}
      <ConfirmDialog
        open={pending !== undefined}
        title={texts.title}
        body={texts.body}
        confirmLabel={asking ? LABEL[asking] : ''}
        cancelLabel={t.cancelAction}
        onConfirm={() => {
          const target = pending?.target
          setAsking(null)
          if (target) onChange(target)
        }}
        onCancel={() => setAsking(null)}
      />
    </div>
  )
}
