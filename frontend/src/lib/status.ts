import type { AttendanceState, GameState, MatchFormat } from '../api/types'
import { copy } from '../copy/en'

// Status is always text + color, never color alone (blueprint section 6). Record<…> keeps every value covered.
export type StatusTone = 'positive' | 'neutral'

export const GAME_STATUS: Record<
  GameState,
  { label: string; tone: StatusTone }
> = {
  OPEN: { label: copy.publicGame.statusOpen, tone: 'positive' },
  CLOSED: { label: copy.publicGame.statusClosed, tone: 'neutral' },
}

export const MATCH_FORMAT: Record<MatchFormat, string> = {
  SINGLES: copy.publicGame.formatSingles,
  DOUBLES: copy.publicGame.formatDoubles,
}

// Own attendance state: label + tone for StatusBadge. ASSIGNED and PLAYING share one generic label until the
// own-assignment feature shows the match details.
export const ATTENDANCE_STATUS: Record<
  AttendanceState,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: copy.myGame.pending, tone: 'neutral' },
  READY: { label: copy.myGame.ready, tone: 'positive' },
  ASSIGNED: { label: copy.myGame.inMatch, tone: 'positive' },
  PLAYING: { label: copy.myGame.inMatch, tone: 'positive' },
  RESTING: { label: copy.myGame.resting, tone: 'neutral' },
  LEFT: { label: copy.myGame.left, tone: 'neutral' },
}
