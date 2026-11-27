import type {
  AttendanceState,
  CourtMatchState,
  GameState,
  MatchFormat,
  MyMatchState,
} from '../api/types'
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

// Own attendance state: label + tone for StatusBadge. ASSIGNED apart from PLAYING (blueprint section 11).
export const ATTENDANCE_STATUS: Record<
  AttendanceState,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: copy.myGame.pending, tone: 'neutral' },
  READY: { label: copy.myGame.ready, tone: 'positive' },
  ASSIGNED: { label: copy.myGame.assigned, tone: 'positive' },
  PLAYING: { label: copy.myGame.playing, tone: 'positive' },
  RESTING: { label: copy.myGame.resting, tone: 'neutral' },
  LEFT: { label: copy.myGame.left, tone: 'neutral' },
}

// Host board labels: all six attendance states, ASSIGNED apart from PLAYING (blueprint section 11).
export const HOST_ATTENDANCE_STATUS: Record<
  AttendanceState,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: copy.hostBoard.statePending, tone: 'neutral' },
  READY: { label: copy.hostBoard.stateReady, tone: 'positive' },
  ASSIGNED: { label: copy.hostBoard.stateAssigned, tone: 'positive' },
  PLAYING: { label: copy.hostBoard.statePlaying, tone: 'positive' },
  RESTING: { label: copy.hostBoard.stateResting, tone: 'neutral' },
  LEFT: { label: copy.hostBoard.stateLeft, tone: 'neutral' },
}

// Own match on the My game page: PLANNED is "Your next match", ACTIVE is "Playing now".
export const MY_MATCH_STATUS: Record<
  MyMatchState,
  { label: string; tone: StatusTone }
> = {
  PLANNED: { label: copy.myGame.assigned, tone: 'positive' },
  ACTIVE: { label: copy.myGame.playing, tone: 'positive' },
}

// The two match states a court can show.
export const COURT_MATCH_STATUS: Record<
  CourtMatchState,
  { label: string; tone: StatusTone }
> = {
  PLANNED: { label: copy.hostBoard.matchPlanned, tone: 'neutral' },
  ACTIVE: { label: copy.hostBoard.matchActive, tone: 'positive' },
}
