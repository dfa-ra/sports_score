import assert from 'node:assert/strict'
import test from 'node:test'
import { pickNextMatch } from '../src/lib/nextMatch.ts'
import { shortSourceRound, winnerSlotLabel } from '../src/lib/playoffLabel.ts'
import { tableRouteQuery } from '../src/lib/tableQuery.ts'

const now = Date.parse('2026-11-03T11:00:00Z')

test('nearest match is the open tournament live row, not a finished kickoff that is still ahead', () => {
  const open = 'open'
  const other = 'other'
  const picked = pickNextMatch([
    { id: 'finished-future', tournamentId: open, status: 'FINISHED', scheduledAt: '2026-11-04T14:30:00Z' },
    { id: 'later', tournamentId: open, status: 'SCHEDULED', scheduledAt: '2026-11-06T12:00:00Z' },
    { id: 'sooner-other', tournamentId: other, status: 'SCHEDULED', scheduledAt: '2026-11-04T08:00:00Z' },
    { id: 'live', tournamentId: open, status: 'LIVE', scheduledAt: '2026-11-03T09:00:00Z' },
  ], now, open)
  assert.equal(picked?.id, 'live')
})

test('nearest scheduled kickoff ignores a past scheduled row and a finished one', () => {
  const picked = pickNextMatch([
    { id: 'past', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: '2026-11-02T12:00:00Z' },
    { id: 'done', tournamentId: 'open', status: 'FINISHED', scheduledAt: '2026-11-04T14:30:00Z' },
    { id: 'soon', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: '2026-11-04T15:00:00Z' },
    { id: 'later', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: '2026-11-05T15:00:00Z' },
  ], now, 'open')
  assert.equal(picked?.id, 'soon')
})

test('a scheduled match with no kickoff is not nearest, and a live one still is', () => {
  const picked = pickNextMatch([
    { id: 'blank', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: null },
    { id: 'soon', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: '2026-11-04T15:00:00Z' },
  ], now, 'open')
  assert.equal(picked?.id, 'soon')
  assert.equal(pickNextMatch([
    { id: 'blank', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: null },
  ], now, 'open'), null)
  assert.equal(pickNextMatch([
    { id: 'live-blank', tournamentId: 'open', status: 'LIVE', scheduledAt: null },
    { id: 'soon', tournamentId: 'open', status: 'SCHEDULED', scheduledAt: '2026-11-04T15:00:00Z' },
  ], now, 'open')?.id, 'live-blank')
})

test('no open-tournament future match means no banner row', () => {
  assert.equal(pickNextMatch([
    { id: 'done', tournamentId: 'open', status: 'FINISHED', scheduledAt: '2026-11-04T14:30:00Z' },
  ], now, 'open'), null)
})

test('empty slot label uses the source round and pair index', () => {
  assert.equal(shortSourceRound('1/2 финала'), '1/2')
  assert.equal(shortSourceRound('1/4 финала'), '1/4')
  assert.equal(shortSourceRound('1/8 финала'), '1/8')
  assert.equal(shortSourceRound('Финал'), 'финала')
  assert.equal(winnerSlotLabel('1/2 финала', 1), 'Победитель 1/2 №1')
  assert.equal(winnerSlotLabel('1/2 финала', 2), 'Победитель 1/2 №2')
  assert.equal(winnerSlotLabel('1/4 финала', 3), 'Победитель 1/4 №3')
  assert.equal(winnerSlotLabel('Финал', 1), 'Победитель финала №1')
})

test('a dead playoff tab is dropped and other tabs stay', () => {
  assert.deepEqual(tableRouteQuery({
    tournamentId: 't-groups',
    tab: 'playoff',
    keepPlayoff: false,
    listAll: true,
  }), { tournament: 't-groups', list: 'all' })
  assert.deepEqual(tableRouteQuery({
    tournamentId: 't-playoff',
    tab: 'playoff',
    keepPlayoff: true,
  }), { tournament: 't-playoff', tab: 'playoff' })
  assert.deepEqual(tableRouteQuery({
    tournamentId: 't-groups',
    tab: 'scorers',
    keepPlayoff: false,
  }), { tournament: 't-groups', tab: 'scorers' })
  assert.deepEqual(tableRouteQuery({
    tournamentId: 't-groups',
    tab: 'table',
  }), { tournament: 't-groups' })
})
