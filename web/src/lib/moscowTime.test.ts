import assert from 'node:assert/strict'
import test from 'node:test'
import { fromLocalInput, toLocalInput } from './datetime.ts'
import { formatDateRange, formatMatchDay, formatWhen } from './format.ts'
import { kickoffClock, kickoffDayMonth, longKickoff, ymd } from './match.ts'
import {
  civilYear,
  formatMoscowWhen,
  moscowClock,
  moscowDottedDate,
  moscowWeekdayShort,
  moscowYear,
  moscowYmd,
  shiftYmd,
} from './moscowTime.ts'

const kickoff = '2026-11-10T15:00:00Z'
const nextMoscowDay = '2026-11-10T21:30:00Z'

function intlMoscow(iso: string) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(iso))
  const read = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value
  return {
    ymd: `${read('year')}-${read('month')}-${read('day')}`,
    clock: `${read('hour')}:${read('minute')}`,
  }
}

test('2026-11-10T15:00:00Z is 10.11.2026 18:00 and calendar day 2026-11-10 in Europe/Moscow', () => {
  assert.equal(moscowDottedDate(kickoff), '10.11.2026')
  assert.equal(moscowClock(kickoff), '18:00')
  assert.equal(moscowYmd(kickoff), '2026-11-10')
  assert.deepEqual(intlMoscow(kickoff), { ymd: '2026-11-10', clock: '18:00' })
  assert.equal(longKickoff(kickoff), '10.11.2026 18:00')
  assert.equal(kickoffClock(kickoff), '18:00')
  assert.equal(kickoffDayMonth(kickoff), '10/11')
  assert.equal(ymd(kickoff), '2026-11-10')
  assert.equal(formatMatchDay(kickoff), '10.11.26')
  assert.equal(formatWhen(kickoff), formatMoscowWhen(kickoff))
  assert.match(formatWhen(kickoff), /18:00/)
  assert.equal(moscowWeekdayShort('2026-11-10'), 'ВТ')
})

test('2026-11-10T21:30:00Z is 11.11.2026 00:30, the next Moscow day', () => {
  assert.equal(moscowDottedDate(nextMoscowDay), '11.11.2026')
  assert.equal(moscowClock(nextMoscowDay), '00:30')
  assert.equal(ymd(nextMoscowDay), '2026-11-11')
  assert.equal(longKickoff(nextMoscowDay), '11.11.2026 00:30')
  assert.deepEqual(intlMoscow(nextMoscowDay), { ymd: '2026-11-11', clock: '00:30' })
  assert.equal(moscowWeekdayShort('2026-11-11'), 'СР')
})

test('June stays on UTC+3 because Europe/Moscow has no daylight saving', () => {
  const summer = '2026-06-10T15:00:00Z'
  assert.equal(moscowClock(summer), '18:00')
  assert.equal(moscowYmd(summer), '2026-06-10')
  assert.deepEqual(intlMoscow(summer), { ymd: '2026-06-10', clock: '18:00' })
})

test('an admin datetime-local round-trips Moscow wall time to a UTC instant', () => {
  assert.equal(toLocalInput(kickoff), '2026-11-10T18:00')
  assert.equal(fromLocalInput('2026-11-10T18:00'), '2026-11-10T15:00:00.000Z')
  assert.equal(toLocalInput(fromLocalInput('2026-11-10T18:00')), '2026-11-10T18:00')
  assert.equal(toLocalInput(nextMoscowDay), '2026-11-11T00:30')
  assert.equal(fromLocalInput('2026-11-11T00:30'), '2026-11-10T21:30:00.000Z')
})

test('a date-only tournament day is not shifted, and the Moscow day key can move by calendar days', () => {
  assert.equal(formatDateRange('2026-11-10', '2026-11-12'), '10.11.2026 — 12.11.2026')
  assert.equal(formatDateRange(nextMoscowDay, nextMoscowDay), '11.11.2026')
  assert.equal(civilYear('2026-01-01'), 2026)
  assert.equal(moscowYear('2026-12-31T21:30:00Z'), 2027)
  assert.equal(shiftYmd('2026-11-10', -21), '2026-10-20')
  assert.equal(shiftYmd('2026-01-01', -1), '2025-12-31')
})
