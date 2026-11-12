import assert from 'node:assert/strict'
import test from 'node:test'
import {
  eventStamp,
  reconciledEvent,
  refereeListGroups,
  refereeListScore,
  refereeListWhen,
  refereeProtocolScore,
  refereePenaltyCaption,
  unclearSend,
} from './refereePad.ts'

const rows = [
  { id: 'live', status: 'LIVE', scheduledAt: '2026-11-10T15:00:00Z', homeScore: 2, awayScore: 1, period: 1, minute: 8 },
  { id: 'paused', status: 'PAUSED', scheduledAt: '2026-11-10T14:00:00Z', homeScore: 0, awayScore: 0, period: 2 },
  { id: 'soon', status: 'SCHEDULED', scheduledAt: '2026-11-10T18:00:00Z', homeScore: 0, awayScore: 0 },
  { id: 'nodate', status: 'SCHEDULED', scheduledAt: null, homeScore: 0, awayScore: 0 },
  { id: 'done', status: 'FINISHED', scheduledAt: '2026-11-01T12:00:00Z', homeScore: 1, awayScore: 1, penaltyHome: 5, penaltyAway: 3 },
  { id: 'nil', status: 'FINISHED', scheduledAt: '2026-11-02T12:00:00Z', homeScore: 0, awayScore: 0 },
]

test('groups keep kickoff order and hide an empty bucket', () => {
  const groups = refereeListGroups(rows)
  assert.deepEqual(groups.map((group) => group.title), ['В игре', 'Предстоящие', 'Завершённые'])
  assert.deepEqual(groups[0].rows.map((row) => row.id), ['paused', 'live'])
  assert.deepEqual(groups[1].rows.map((row) => row.id), ['nodate', 'soon'])
  assert.equal(refereeListGroups([]).length, 0)
})

test('a forfeit keeps the stored score when the protocol recount is 0:0', () => {
  const counted = { home: 0, away: 0 }
  assert.deepEqual(
    refereeProtocolScore({ status: 'FINISHED', homeScore: 5, awayScore: 0, forfeit: 'away' }, counted),
    { homeScore: 5, awayScore: 0 },
  )
  assert.deepEqual(
    refereeProtocolScore({ status: 'FINISHED', homeScore: 0, awayScore: 0, forfeit: 'both' }, counted),
    { homeScore: 0, awayScore: 0 },
  )
  assert.deepEqual(
    refereeProtocolScore({ status: 'LIVE', homeScore: 1, awayScore: 0, forfeit: 'none' }, counted),
    { homeScore: 0, awayScore: 0 },
  )
})

test('scheduled score is a dash, a real 0:0 stays, penalties are a separate caption', () => {
  assert.equal(refereeListScore(rows[2]), '– : –')
  assert.equal(refereeListScore(rows[1]), '0 : 0')
  assert.equal(refereeListScore(rows[5]), '0 : 0')
  assert.equal(refereeListScore(rows[4]), '1 : 1')
  assert.equal(refereePenaltyCaption(rows[4]), 'Пенальти 5 : 3')
  assert.equal(refereePenaltyCaption(rows[5]), '')
  assert.equal(refereePenaltyCaption(rows[2]), '')
})

test('missing date and missing clock are not filled in', () => {
  assert.equal(refereeListWhen(rows[3]), 'Дата уточняется')
  assert.equal(refereeListWhen(rows[0]), '1-й тайм · 8\'')
  assert.equal(refereeListWhen({ status: 'LIVE' }), 'В игре')
  assert.equal(refereeListWhen({ status: 'PAUSED' }), 'Пауза')
  assert.equal(refereeListWhen({ status: 'CANCELLED', scheduledAt: '2026-11-03T12:00:00Z' }), 'Отменён')
})

test('an unclear save is reconciled from a new protocol row and a clear HTTP error is not', () => {
  const payload = { eventType: 'GOAL', teamId: 'home', playerId: 'p1' }
  const before = [{ id: 'old', eventType: 'GOAL', teamId: 'home', playerId: 'p9' }]
  const added = { id: 'new', eventType: 'GOAL', teamId: 'home', playerId: 'p1' }
  assert.equal(reconciledEvent(before, [...before, added], payload)?.id, 'new')
  assert.equal(reconciledEvent([...before, added], [...before, added], payload)?.id, undefined)
  assert.equal(unclearSend({ code: 'ECONNABORTED' }), true)
  assert.equal(unclearSend({ response: { status: 500 } }), false)
  assert.equal(eventStamp({ eventType: 'GOAL', teamId: 'home', playerId: 'p1', metadata: { ownGoal: true } }).endsWith('|own'), true)
})
