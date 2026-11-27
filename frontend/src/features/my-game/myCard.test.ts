import { describe, expect, it } from 'vitest'
import { cardOf, pollingWanted } from './myCard'

describe('cardOf', () => {
  it('showsTheStateCardWhenThereIsNoAssignment', () => {
    expect(cardOf({ attendanceState: 'READY', assignment: null })).toEqual({
      kind: 'state',
      state: 'READY',
    })
  })

  it('showsYourNextMatchWithCourtMatchAndHintForAPlannedMatch', () => {
    const card = cardOf({
      attendanceState: 'ASSIGNED',
      assignment: { matchNo: 3, courtNo: 2, matchState: 'PLANNED' },
    })

    expect(card).toMatchObject({
      kind: 'assignment',
      label: 'Your next match',
      court: 'Court 2',
      matchNo: 'Match 3',
      hint: 'Go to the court. The host will start the match.',
    })
  })

  it('showsPlayingNowWithItsHintForAnActiveMatch', () => {
    const card = cardOf({
      attendanceState: 'PLAYING',
      assignment: { matchNo: 3, courtNo: 2, matchState: 'ACTIVE' },
    })

    expect(card).toMatchObject({
      kind: 'assignment',
      label: 'Playing now',
      court: 'Court 2',
      matchNo: 'Match 3',
      hint: 'Your match is on.',
    })
  })

  it('fallsBackToTheStateCardWhenAssignedHasNoAssignment', () => {
    expect(cardOf({ attendanceState: 'ASSIGNED', assignment: null })).toEqual({
      kind: 'state',
      state: 'ASSIGNED',
    })
  })
})

describe('pollingWanted', () => {
  it('isOnAfterASuccessUnlessTheStateIsLeft', () => {
    expect(pollingWanted('success', null, 'READY')).toBe(true)
    expect(pollingWanted('success', null, 'PENDING')).toBe(true)
    expect(pollingWanted('success', null, 'LEFT')).toBe(false)
  })

  it('isOffAfter401And404', () => {
    expect(pollingWanted('error', 401, null)).toBe(false)
    expect(pollingWanted('error', 404, null)).toBe(false)
  })

  it('isOnAfter503OrANetworkError', () => {
    expect(pollingWanted('error', 503, null)).toBe(true)
    expect(pollingWanted('error', null, null)).toBe(true)
  })

  it('isOffWhileLoading', () => {
    expect(pollingWanted('loading', null, null)).toBe(false)
  })
})
