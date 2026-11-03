import assert from 'node:assert/strict'
import test from 'node:test'
import { matchInstant } from '../src/lib/matchDate.ts'

function day(value: string | number | Date | null) {
  if (value == null) return ''
  const date = new Date(value)
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
}

const scheduled = '2026-11-25T12:00:00Z'
const started = '2026-11-03T12:00:00Z'

test('a started or played match uses the actual start, so the day and the card share one date', () => {
  for (const status of ['LIVE', 'PAUSED', 'FINISHED']) {
    const instant = matchInstant({ status, scheduledAt: scheduled, startedAt: started })
    assert.equal(instant, started)
    assert.equal(day(instant), day(started))
    assert.notEqual(day(instant), day(scheduled))
  }
})

test('a scheduled match stays on the planned kickoff even if a start timestamp is present', () => {
  assert.equal(
    matchInstant({ status: 'SCHEDULED', scheduledAt: scheduled, startedAt: started }),
    scheduled,
  )
})

test('a cancellation uses the actual start only when play began', () => {
  assert.equal(
    matchInstant({ status: 'CANCELLED', scheduledAt: scheduled, startedAt: started }),
    started,
  )
  assert.equal(
    matchInstant({ status: 'CANCELLED', scheduledAt: scheduled, startedAt: null }),
    scheduled,
  )
  assert.equal(
    matchInstant({ status: 'CANCELLED', scheduledAt: scheduled }),
    scheduled,
  )
})

test('a started status without a usable start timestamp falls back to the schedule', () => {
  assert.equal(matchInstant({ status: 'FINISHED', scheduledAt: scheduled, startedAt: null }), scheduled)
  assert.equal(matchInstant({ status: 'LIVE', scheduledAt: scheduled, startedAt: '' }), scheduled)
  assert.equal(matchInstant({ status: 'PAUSED', scheduledAt: scheduled, startedAt: 'not-a-date' }), scheduled)
})

test('a missing match has no date', () => {
  assert.equal(matchInstant(null), null)
  assert.equal(matchInstant(undefined), null)
})
