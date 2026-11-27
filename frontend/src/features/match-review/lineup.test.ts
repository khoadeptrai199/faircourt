import { describe, expect, it } from 'vitest'
import type {
  AttendanceState,
  HostBoard,
  HostBoardPlayer,
  MatchSuggestions,
} from '../../api/types'
import {
  initLineup,
  lineupReducer,
  type LineupState,
  type Slot,
} from './lineup'

function player(id: string, state: AttendanceState = 'READY'): HostBoardPlayer {
  return {
    attendanceId: id,
    displayName: id,
    kind: 'GUEST',
    state,
    band: null,
    bandStatus: null,
    checkedInAt: '2026-11-26T10:00:00Z',
    availableSince: '2026-11-26T10:00:00Z',
    matchesPlayed: 0,
  }
}

function boardOf(players: HostBoardPlayer[]): HostBoard {
  return {
    asOf: '2026-11-26T10:00:00Z',
    game: {
      id: 'game-1',
      publicCode: 'CODE',
      title: 'Test game',
      venue: 'Test venue',
      startsAt: '2026-11-26T18:00:00Z',
      endsAt: '2026-11-26T20:00:00Z',
      courtCount: 2,
      state: 'OPEN',
      matchFormat: 'DOUBLES',
      minSets: 6,
    },
    players,
    courts: [
      { courtNo: 1, match: null },
      { courtNo: 2, match: null },
    ],
  }
}

const board = boardOf([
  player('a1'),
  player('a2'),
  player('b1'),
  player('b2'),
  player('c1'),
  player('c2'),
  player('d1'),
  player('d2'),
  player('e1'),
  player('p1', 'PENDING'),
  player('e2'),
  player('x1', 'ASSIGNED'),
])

const suggestions: MatchSuggestions = {
  suggestions: [
    {
      courtNo: 1,
      teams: { A: ['a1', 'a2'], B: ['b1', 'b2'] },
      reasonCodes: [],
      warnings: [],
    },
    {
      courtNo: 2,
      teams: { A: ['c1', 'c2'], B: ['d1', 'd2'] },
      reasonCodes: [],
      warnings: [],
    },
  ],
  noSuggestionReason: null,
}

const slot = (courtNo: number, team: 'A' | 'B', index: number): Slot => ({
  kind: 'court',
  courtNo,
  team,
  index,
})
const bench = (index: number): Slot => ({ kind: 'bench', index })

const tap = (state: LineupState, s: Slot) =>
  lineupReducer(state, { type: 'tap', slot: s })

describe('lineup', () => {
  it('initLineup builds courts and a bench of unpicked READY players in board order', () => {
    const state = initLineup(suggestions, board)

    expect(state.courts).toEqual([
      {
        courtNo: 1,
        A: ['a1', 'a2'],
        B: ['b1', 'b2'],
        edited: false,
        saved: null,
      },
      {
        courtNo: 2,
        A: ['c1', 'c2'],
        B: ['d1', 'd2'],
        edited: false,
        saved: null,
      },
    ])
    expect(state.bench).toEqual(['e1', 'e2'])
    expect(state.selected).toBeNull()
  })

  it('tapping a player selects it and tapping it again deselects it', () => {
    const start = initLineup(suggestions, board)

    const selected = tap(start, slot(1, 'A', 0))
    const cleared = tap(selected, slot(1, 'A', 0))

    expect(selected.selected).toEqual(slot(1, 'A', 0))
    expect(cleared).toEqual(start)
  })

  it('tapping two players of one team swaps them and marks the court edited', () => {
    const state = tap(
      tap(initLineup(suggestions, board), slot(1, 'A', 0)),
      slot(1, 'A', 1),
    )

    expect(state.courts[0].A).toEqual(['a2', 'a1'])
    expect(state.courts[0].edited).toBe(true)
    expect(state.courts[1].edited).toBe(false)
    expect(state.selected).toBeNull()
  })

  it('tapping players of two teams on one court swaps them across teams', () => {
    const state = tap(
      tap(initLineup(suggestions, board), slot(1, 'A', 0)),
      slot(1, 'B', 1),
    )

    expect(state.courts[0].A).toEqual(['b2', 'a2'])
    expect(state.courts[0].B).toEqual(['b1', 'a1'])
    expect(state.courts[0].edited).toBe(true)
  })

  it('tapping players of two courts swaps them and marks both courts edited', () => {
    const state = tap(
      tap(initLineup(suggestions, board), slot(1, 'A', 0)),
      slot(2, 'B', 0),
    )

    expect(state.courts[0].A).toEqual(['d1', 'a2'])
    expect(state.courts[1].B).toEqual(['a1', 'd2'])
    expect(state.courts[0].edited).toBe(true)
    expect(state.courts[1].edited).toBe(true)
  })

  it('tapping a court player then a bench player swaps them', () => {
    const state = tap(
      tap(initLineup(suggestions, board), slot(1, 'B', 0)),
      bench(1),
    )

    expect(state.courts[0].B).toEqual(['e2', 'b2'])
    expect(state.bench).toEqual(['e1', 'b1'])
    expect(state.courts[0].edited).toBe(true)
  })

  it('tapping two bench players only clears the selection', () => {
    const start = initLineup(suggestions, board)

    const state = tap(tap(start, bench(0)), bench(1))

    expect(state).toEqual(start)
  })

  it('players of a saved court cannot be selected or swapped', () => {
    const saved = lineupReducer(initLineup(suggestions, board), {
      type: 'saved',
      courtNo: 1,
      matchNo: 3,
    })

    const ignored = tap(saved, slot(1, 'A', 0))
    const withOther = tap(saved, slot(2, 'A', 0))
    const notSwapped = tap(withOther, slot(1, 'A', 0))

    expect(saved.courts[0].saved).toEqual({ matchNo: 3 })
    expect(ignored).toEqual(saved)
    expect(notSwapped).toEqual(withOther)
    expect(notSwapped.courts[0].A).toEqual(['a1', 'a2'])
  })

  it('reset rebuilds the state from the new suggestions and drops edits', () => {
    const edited = tap(
      tap(initLineup(suggestions, board), slot(1, 'A', 0)),
      slot(1, 'A', 1),
    )

    const newSuggestions: MatchSuggestions = {
      suggestions: [
        {
          courtNo: 2,
          teams: { A: ['e1', 'c1'], B: ['a1', 'd1'] },
          reasonCodes: [],
          warnings: [],
        },
      ],
      noSuggestionReason: null,
    }
    const newBoard = boardOf([
      player('a1'),
      player('c1'),
      player('d1'),
      player('e1'),
      player('b1'),
      player('b2', 'PENDING'),
    ])

    const state = lineupReducer(edited, {
      type: 'reset',
      suggestions: newSuggestions,
      board: newBoard,
    })

    expect(state).toEqual(initLineup(newSuggestions, newBoard))
    expect(state.courts).toHaveLength(1)
    expect(state.bench).toEqual(['b1'])
  })

  it('while a court is saving no player can be selected or swapped; failed unfreezes (extends TC-29)', () => {
    const selected = tap(initLineup(suggestions, board), slot(1, 'A', 0))
    const saving = lineupReducer(selected, { type: 'saving', courtNo: 2 })

    const ignoredTap = tap(saving, slot(1, 'A', 1))
    const ignoredBench = tap(saving, bench(0))
    const failed = lineupReducer(saving, { type: 'failed' })
    const swapped = tap(failed, slot(1, 'A', 1))

    expect(saving.saving).toBe(2)
    expect(ignoredTap).toEqual(saving)
    expect(ignoredBench).toEqual(saving)
    expect(failed.saving).toBeNull()
    expect(swapped.courts[0].A).toEqual(['a2', 'a1'])
  })

  it('saved clears the saving flag and locks the court', () => {
    const saving = lineupReducer(initLineup(suggestions, board), {
      type: 'saving',
      courtNo: 1,
    })

    const saved = lineupReducer(saving, {
      type: 'saved',
      courtNo: 1,
      matchNo: 2,
    })

    expect(saved.saving).toBeNull()
    expect(saved.courts[0].saved).toEqual({ matchNo: 2 })
  })
})
