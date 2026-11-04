import assert from 'node:assert/strict'
import test from 'node:test'
import { DATE_PENDING, matchCardWhen, matchInstant } from '../src/lib/matchDate.ts'
import { unplayedCardScore, ymd } from '../src/lib/match.ts'

function day(value: string | number | Date | null) {
  if (value == null) return ''
  return ymd(value)
}

const scheduled = '2026-11-25T12:00:00Z'
const started = '2026-11-03T12:00:00Z'

test('planned time wins for the feed, the bracket and the card, even after the match starts', () => {
  for (const status of ['SCHEDULED', 'LIVE', 'PAUSED', 'FINISHED', 'CANCELLED']) {
    const instant = matchInstant({ status, scheduledAt: scheduled, startedAt: started })
    assert.equal(instant, scheduled)
    assert.equal(day(instant), day(scheduled))
    assert.notEqual(day(instant), day(started))
  }
  assert.equal(matchCardWhen({ status: 'FINISHED', scheduledAt: scheduled, startedAt: started }), '25 нояб., 15:00')
})

test('a dateless match that has started or been played uses the actual start', () => {
  for (const status of ['LIVE', 'PAUSED', 'FINISHED']) {
    const instant = matchInstant({ status, scheduledAt: null, startedAt: started })
    assert.equal(instant, started)
    assert.equal(day(instant), '2026-11-03')
    assert.equal(matchCardWhen({ status, scheduledAt: null, startedAt: started }), '3 нояб., 15:00')
  }
  assert.equal(
    matchInstant({ status: 'CANCELLED', scheduledAt: null, startedAt: started }),
    started,
  )
})

test('a scheduled match does not borrow a stray start timestamp', () => {
  assert.equal(matchInstant({ status: 'SCHEDULED', scheduledAt: null, startedAt: started }), null)
  assert.equal(matchCardWhen({ status: 'SCHEDULED', scheduledAt: null, startedAt: started }), DATE_PENDING)
})

test('neither planned time nor an actual start shows the pending label instead of a dash', () => {
  assert.equal(matchInstant({ status: 'FINISHED', scheduledAt: null, startedAt: null }), null)
  assert.equal(matchInstant({ status: 'LIVE', scheduledAt: '', startedAt: 'not-a-date' }), null)
  assert.equal(matchInstant({ status: 'CANCELLED', scheduledAt: null, startedAt: null }), null)
  assert.equal(matchCardWhen({ status: 'FINISHED' }), 'Дата уточняется')
  assert.equal(matchCardWhen(null), 'Дата уточняется')
})

test('a missing match has no date', () => {
  assert.equal(matchInstant(null), null)
  assert.equal(matchInstant(undefined), null)
})

test('an autofinal that has started uses the card date, not the dotted stamp', () => {
  const startedAt = '2026-11-04T18:14:00Z'
  assert.equal(matchCardWhen({ status: 'LIVE', scheduledAt: null, startedAt }), '4 нояб., 21:14')
  assert.equal(matchCardWhen({ status: 'FINISHED', scheduledAt: null, startedAt }), '4 нояб., 21:14')
})

test('a match that has not started shows dashes, and a started match keeps the score', () => {
  assert.equal(unplayedCardScore('SCHEDULED'), '– : –')
  assert.equal(unplayedCardScore('LIVE'), null)
  assert.equal(unplayedCardScore('PAUSED'), null)
  assert.equal(unplayedCardScore('FINISHED'), null)
})
