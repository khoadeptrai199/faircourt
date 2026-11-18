import type { GameState, MatchFormat } from '../api/types'
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
