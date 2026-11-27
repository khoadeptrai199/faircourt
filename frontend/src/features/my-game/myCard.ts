import type { AttendanceState, MyAttendance } from '../../api/types'
import { copy } from '../../copy/en'
import { MY_MATCH_STATUS } from '../../lib/status'
import type { StatusTone } from '../../lib/status'
import type { AsyncState } from '../../hooks/useAsync'

const t = copy.myGame

export type MyCard =
  | { kind: 'state'; state: AttendanceState }
  | {
      kind: 'assignment'
      label: string
      tone: StatusTone
      court: string
      matchNo: string
      hint: string
    }

// Which card the My game page shows and with which texts (design section 5). No assignment: the state card.
export function cardOf(me: MyAttendance): MyCard {
  const a = me.assignment
  if (a === null) return { kind: 'state', state: me.attendanceState }
  const status = MY_MATCH_STATUS[a.matchState]
  return {
    kind: 'assignment',
    label: status.label,
    tone: status.tone,
    court: t.court(a.courtNo),
    matchNo: t.matchNo(a.matchNo),
    hint: a.matchState === 'ACTIVE' ? t.playingHint : t.assignedHint,
  }
}

// Polling rules (design D-2): on after a success unless LEFT; on after a 503 / network error so the page recovers;
// off while loading and after 401 / 404 (the person must act).
export function pollingWanted(
  status: AsyncState<unknown>['status'],
  errorStatus: number | null,
  state: AttendanceState | null,
): boolean {
  switch (status) {
    case 'success':
      return state !== 'LEFT'
    case 'error':
      return errorStatus !== 401 && errorStatus !== 404
    case 'loading':
    case 'empty':
      return false
    default: {
      const unreachable: never = status
      return unreachable
    }
  }
}
