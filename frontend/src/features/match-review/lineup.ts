import type { HostBoard, MatchSuggestions } from '../../api/types'

// Editable lineups of the review page and the tap-to-swap rule (Match confirm design section 7). Pure: no React,
// so it can be unit-tested. Players are attendance ids; names and levels are looked up from the board.
export type Team = 'A' | 'B'

export type Slot =
  | { kind: 'court'; courtNo: number; team: Team; index: number }
  | { kind: 'bench'; index: number }

export type CourtLineup = {
  courtNo: number
  A: string[]
  B: string[]
  edited: boolean
  saved: { matchNo: number } | null
}

export type LineupState = {
  courts: CourtLineup[]
  bench: string[]
  selected: Slot | null
  // Court whose confirm is in flight. The lineup is frozen meanwhile, so `saved` locks what the server stored.
  saving: number | null
}

export type LineupAction =
  | { type: 'tap'; slot: Slot }
  | { type: 'saving'; courtNo: number }
  | { type: 'failed' }
  | { type: 'saved'; courtNo: number; matchNo: number }
  | { type: 'reset'; suggestions: MatchSuggestions; board: HostBoard }

export function initLineup(
  suggestions: MatchSuggestions,
  board: HostBoard,
): LineupState {
  const courts: CourtLineup[] = suggestions.suggestions.map((proposal) => ({
    courtNo: proposal.courtNo,
    A: [...proposal.teams.A],
    B: [...proposal.teams.B],
    edited: false,
    saved: null,
  }))
  const picked = new Set(courts.flatMap((court) => [...court.A, ...court.B]))
  const bench = board.players
    .filter((p) => p.state === 'READY' && !picked.has(p.attendanceId))
    .map((p) => p.attendanceId)
  return { courts, bench, selected: null, saving: null }
}

export function sameSlot(a: Slot, b: Slot): boolean {
  if (a.kind === 'bench' || b.kind === 'bench') {
    return a.kind === b.kind && a.index === b.index
  }
  return a.courtNo === b.courtNo && a.team === b.team && a.index === b.index
}

function isSaved(state: LineupState, slot: Slot): boolean {
  return (
    slot.kind === 'court' &&
    state.courts.some((c) => c.courtNo === slot.courtNo && c.saved !== null)
  )
}

function playerAt(state: LineupState, slot: Slot): string | undefined {
  if (slot.kind === 'bench') {
    return state.bench[slot.index]
  }
  const court = state.courts.find((c) => c.courtNo === slot.courtNo)
  return court?.[slot.team][slot.index]
}

function withPlayer(state: LineupState, slot: Slot, id: string): LineupState {
  if (slot.kind === 'bench') {
    return {
      ...state,
      bench: state.bench.map((p, i) => (i === slot.index ? id : p)),
    }
  }
  return {
    ...state,
    courts: state.courts.map((court) =>
      court.courtNo === slot.courtNo
        ? {
            ...court,
            [slot.team]: court[slot.team].map((p, i) =>
              i === slot.index ? id : p,
            ),
            edited: true,
          }
        : court,
    ),
  }
}

export function lineupReducer(
  state: LineupState,
  action: LineupAction,
): LineupState {
  switch (action.type) {
    case 'tap': {
      const { slot } = action
      if (state.saving !== null || isSaved(state, slot)) {
        return state
      }
      const first = state.selected
      if (first === null) {
        return { ...state, selected: slot }
      }
      if (sameSlot(first, slot)) {
        return { ...state, selected: null }
      }
      const a = playerAt(state, first)
      const b = playerAt(state, slot)
      if (
        a === undefined ||
        b === undefined ||
        (first.kind === 'bench' && slot.kind === 'bench')
      ) {
        return { ...state, selected: null }
      }
      return {
        ...withPlayer(withPlayer(state, first, b), slot, a),
        selected: null,
      }
    }
    case 'saving':
      return { ...state, saving: action.courtNo }
    case 'failed':
      return { ...state, saving: null }
    case 'saved':
      return {
        ...state,
        saving: null,
        courts: state.courts.map((court) =>
          court.courtNo === action.courtNo
            ? { ...court, saved: { matchNo: action.matchNo } }
            : court,
        ),
        selected:
          state.selected?.kind === 'court' &&
          state.selected.courtNo === action.courtNo
            ? null
            : state.selected,
      }
    case 'reset':
      return initLineup(action.suggestions, action.board)
    default: {
      const unreachable: never = action
      return unreachable
    }
  }
}
