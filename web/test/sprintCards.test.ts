import assert from 'node:assert/strict'
import test from 'node:test'
import {
  applyCalendarFilter,
  calendarCounts,
  calendarDayKey,
  calendarHeroPending,
  cardScoreText,
  dottedDays,
  groupByTournament,
  heroMeta,
  matchesOnDay,
  sortDayMatches,
  undatedMatches,
  weekKeys,
} from '../src/lib/calendarFeed.ts'
import { advancingRank, analystRows, formMarks, refereeNames, venueLabel } from '../src/lib/matchPresentation.ts'
import { collectPaged, nullKickoffScanStep, pageBodyComplete } from '../src/lib/pagedList.ts'
import { unplayedCardScore } from '../src/lib/match.ts'

const day = '2026-11-05T15:00:00Z'

test('cancelled and scheduled scores are a dash, finished nil-nil stays numeric', () => {
  assert.equal(unplayedCardScore('CANCELLED'), '– : –')
  assert.equal(cardScoreText({ status: 'CANCELLED', homeScore: 3, awayScore: 2 }), '– : –')
  assert.equal(cardScoreText({ status: 'SCHEDULED', homeScore: 0, awayScore: 0 }), '– : –')
  assert.equal(cardScoreText({ status: 'FINISHED', homeScore: 0, awayScore: 0 }), '0 : 0')
  assert.equal(cardScoreText({ status: 'LIVE', homeScore: 1, awayScore: 1 }), '1 : 1')
  assert.equal(cardScoreText({ status: 'PAUSED', homeScore: 2, awayScore: 0 }), '2 : 0')
})

test('a scheduled match without kickoff is not placed on a calendar day', () => {
  const undated = { id: 'u', status: 'SCHEDULED', scheduledAt: null, startedAt: '2026-11-05T12:00:00Z' }
  const dated = { id: 'd', status: 'SCHEDULED', scheduledAt: day, startedAt: null }
  const played = { id: 'p', status: 'FINISHED', scheduledAt: null, startedAt: day }
  assert.equal(calendarDayKey(undated), '')
  assert.equal(calendarDayKey(dated), '2026-11-05')
  assert.equal(calendarDayKey(played), '2026-11-05')
  assert.deepEqual(undatedMatches([undated, dated, played]).map((row) => row.id), ['u'])
  const onDay = matchesOnDay([dated], [undated, played], '2026-11-05')
  assert.deepEqual(onDay.map((row) => row.id), ['d', 'p'])
})

test('live counter ignores paused matches', () => {
  const counts = calendarCounts([
    { tournamentId: 'a', status: 'LIVE' },
    { tournamentId: 'a', status: 'PAUSED' },
    { tournamentId: 'b', status: 'FINISHED' },
  ])
  assert.deepEqual(counts, { matches: 3, tournaments: 2, live: 1, paused: 1 })
  assert.equal(heroMeta(counts, true), '3 матча · 2 турнира · 1 в игре · 1 на паузе')
  assert.equal(heroMeta(counts, false), '')
})

test('an empty filter keeps an honest total and does not look like loading', () => {
  const rows = [
    { id: '1', tournamentId: 'liga', status: 'SCHEDULED', scheduledAt: '2026-11-06T15:00:00Z' },
  ]
  const favorites = applyCalendarFilter(rows, { kind: 'favorites' }, () => false)
  const otherTournament = applyCalendarFilter(rows, { kind: 'tournament', tournamentId: 'cup' }, () => false)
  assert.deepEqual(favorites, [])
  assert.deepEqual(otherTournament, [])
  const counts = calendarCounts(favorites)
  assert.equal(heroMeta(counts, true), '0 матчей · 0 турниров · 0 в игре')
  assert.equal(calendarHeroPending(false, true), false)
  assert.equal(calendarHeroPending(true, false), true)
  assert.equal(calendarHeroPending(false, false), false)
})

test('week dots follow the filter and ignore the selected day', () => {
  const keys = weekKeys('2026-11-05')
  assert.deepEqual(keys, [
    '2026-11-02',
    '2026-11-03',
    '2026-11-04',
    '2026-11-05',
    '2026-11-06',
    '2026-11-07',
    '2026-11-08',
  ])
  const rows = [
    { id: '1', tournamentId: 'liga', status: 'SCHEDULED', scheduledAt: '2026-11-05T15:00:00Z' },
    { id: '2', tournamentId: 'cup', status: 'SCHEDULED', scheduledAt: '2026-11-07T15:00:00Z' },
    { id: '3', tournamentId: 'liga', status: 'SCHEDULED', scheduledAt: null },
  ]
  assert.deepEqual(dottedDays(rows, keys), ['2026-11-05', '2026-11-07'])
  const onlyCup = applyCalendarFilter(rows, { kind: 'tournament', tournamentId: 'cup' }, () => false)
  assert.deepEqual(dottedDays(onlyCup, keys), ['2026-11-07'])
  const fav = applyCalendarFilter(rows, { kind: 'favorites' }, (row) => row.id === '1')
  assert.deepEqual(dottedDays(fav, keys), ['2026-11-05'])
})

test('live matches sort ahead of paused and scheduled', () => {
  const rows = sortDayMatches([
    { status: 'SCHEDULED', scheduledAt: '2026-11-05T10:00:00Z' },
    { status: 'PAUSED', scheduledAt: '2026-11-05T12:00:00Z' },
    { status: 'LIVE', scheduledAt: '2026-11-05T18:00:00Z' },
  ])
  assert.deepEqual(rows.map((row) => row.status), ['LIVE', 'PAUSED', 'SCHEDULED'])
  const groups = groupByTournament([
    { tournamentId: 'a', status: 'FINISHED', scheduledAt: '2026-11-05T10:00:00Z' },
    { tournamentId: 'b', status: 'LIVE', scheduledAt: '2026-11-05T18:00:00Z' },
  ])
  assert.equal(groups[0]?.id, 'b')
})

test('page totals prove completeness and a cap without them does not', () => {
  assert.equal(pageBodyComplete({ content: [], page: 0, totalPages: 0, totalElements: 0 }, 0), true)
  assert.equal(pageBodyComplete({ content: [{ id: 1 }], page: 0, totalPages: 2, totalElements: 101 }, 1), false)
  assert.equal(pageBodyComplete({ content: [{ id: 1 }], page: 1, totalPages: 2, totalElements: 101 }, 1), true)
  assert.equal(pageBodyComplete({ content: [] }, 0), false)
  assert.equal(nullKickoffScanStep({
    page: 0,
    cap: 30,
    body: { page: 0, totalPages: 4, content: [{ scheduledAt: null }] },
    batch: [{ scheduledAt: null }],
  }), 'continue')
  assert.equal(nullKickoffScanStep({
    page: 1,
    cap: 30,
    body: { page: 1, totalPages: 4 },
    batch: [{ scheduledAt: null }, { scheduledAt: '2026-11-05T15:00:00Z' }],
  }), 'done-complete')
  assert.equal(nullKickoffScanStep({
    page: 29,
    cap: 30,
    body: { page: 29, totalPages: 40 },
    batch: [{ scheduledAt: null }],
  }), 'done-incomplete')
})

test('a repeated match page is kept once and does not count as complete', async () => {
  let calls = 0
  const result = await collectPaged(async () => {
    calls += 1
    return { content: [{ id: 'm1' }], page: calls - 1, size: 1 }
  }, 5)
  assert.equal(result.rows.length, 1)
  assert.equal(result.complete, false)
  assert.equal(calls, 2)
})

test('form shorter than five is not padded and venue null is pending', () => {
  const marks = formMarks([
    { status: 'FINISHED', homeTeamId: 'a', awayTeamId: 'b', homeScore: 1, awayScore: 0 },
    { status: 'FINISHED', homeTeamId: 'c', awayTeamId: 'a', homeScore: 0, awayScore: 0 },
  ], 'a')
  assert.deepEqual(marks, ['В', 'Н'])
  assert.equal(formMarks([], 'a').length, 0)
  assert.equal(venueLabel(null), 'Место уточняется')
  assert.equal(venueLabel('  Зал ИТМО  '), 'Зал ИТМО')
  assert.deepEqual(refereeNames([
    { firstName: 'Иван', lastName: 'Петров', email: 'hidden@example.com' },
    { email: 'only@example.com' },
  ]), ['Петров Иван'])
})

test('analyst zeros stay zeros and null possession is only that field', () => {
  const view = analystRows({
    home: { shots: 0, shotsOnTarget: 0, saves: 0, corners: 0, fouls: 0, freeKicks: 0, kickIns: 0, woodwork: 0 },
    away: { shots: 0, shotsOnTarget: 0, saves: 0, corners: 0, fouls: 0, freeKicks: 0, kickIns: 0, woodwork: 0 },
    homePossessionPercent: null,
    awayPossessionPercent: null,
  })
  assert.equal(view.rows[0]?.home, '0 / 0')
  assert.equal(view.rows[1]?.home, '0')
  assert.equal(view.possession.kind, 'empty')
  const tracked = analystRows({ home: {}, away: {}, homePossessionPercent: 0, awayPossessionPercent: 100 })
  assert.deepEqual(tracked.possession, { kind: 'nums', home: 0, away: 100 })
})

test('qualification highlight follows advancing places only', () => {
  assert.equal(advancingRank(0, []), false)
  assert.equal(advancingRank(1, []), false)
  assert.equal(advancingRank(0, [1, 2]), true)
  assert.equal(advancingRank(2, [1, 2]), false)
  assert.equal(advancingRank(3, null), false)
})
