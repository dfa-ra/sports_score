import assert from 'node:assert/strict'
import test from 'node:test'
import { calendarQueryPatch, calendarQueryState, cardScoreParts, sameSearchQuery } from '../src/lib/calendarFeed.ts'
import { keepFormPayload } from '../src/lib/matchPresentation.ts'
import { registeredName } from '../src/lib/playerTitle.ts'

test('a registered name is surname then given name, even when the shirt name is the other way', () => {
  assert.equal(registeredName({
    firstName: 'Толян',
    lastName: 'Игроков',
    displayName: 'Толян Игроков',
  }), 'Игроков Толян')
  assert.equal(registeredName({
    firstName: 'Иван',
    lastName: 'Иванов',
    displayName: 'Vanya',
  }), 'Иванов Иван')
})

test('a stale form response does not replace the current match', () => {
  const rows = [{ id: 'a', status: 'FINISHED' }]
  assert.equal(keepFormPayload(1, 2, 'match-a', 'match-a', rows), null)
  assert.equal(keepFormPayload(2, 2, 'match-a', 'match-b', rows), null)
  assert.deepEqual(keepFormPayload(2, 2, 'match-a', 'match-a', rows), rows)
})

test('calendar date and tournament survive in the query and a replace stays equal', () => {
  const today = '2026-11-06'
  assert.deepEqual(calendarQueryState({}, today), { date: today, tournamentId: '' })
  assert.deepEqual(calendarQueryState({ date: '2026-11-02', tournament: 'cup' }, today), {
    date: '2026-11-02',
    tournamentId: 'cup',
  })
  assert.equal(calendarQueryState({ date: 'tomorrow' }, today).date, today)
  const next = calendarQueryPatch({ tab: 'live' }, '2026-11-02', { kind: 'tournament', tournamentId: 'cup' })
  assert.deepEqual(next, { tab: 'live', date: '2026-11-02', tournament: 'cup' })
  assert.equal(sameSearchQuery(next, next), true)
  const cleared = calendarQueryPatch(next, '2026-11-02', { kind: 'all' })
  assert.equal(cleared.tournament, undefined)
  assert.equal(cleared.date, '2026-11-02')
})

test('an unplayed team score is a dash and a finished 0:0 stays 0:0', () => {
  assert.deepEqual(cardScoreParts({ status: 'SCHEDULED', homeScore: 0, awayScore: 0 }), {
    dash: true,
    home: '–',
    away: '–',
  })
  assert.deepEqual(cardScoreParts({ status: 'FINISHED', homeScore: 0, awayScore: 0 }), {
    dash: false,
    home: '0',
    away: '0',
  })
  assert.deepEqual(cardScoreParts({ status: 'LIVE', homeScore: 1, awayScore: 0 }), {
    dash: false,
    home: '1',
    away: '0',
  })
})
