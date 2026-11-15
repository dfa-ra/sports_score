import assert from 'node:assert/strict'
import test from 'node:test'
import {
  calendarScore,
  calendarScoreParts,
  collectPages,
  myTeamMode,
  playerStatRows,
  shouldSendRsvp,
  showsPlayerHistory,
  teamRecordLine,
} from './profileMyTeam.ts'

test('a fan has no player history, a player and a captain do, an admin only with a real profile', () => {
  assert.equal(showsPlayerHistory(['FAN'], false), false)
  assert.equal(showsPlayerHistory(['FAN'], true), false)
  assert.equal(showsPlayerHistory(['PLAYER'], false), true)
  assert.equal(showsPlayerHistory(['CAPTAIN', 'REFEREE'], false), true)
  assert.equal(showsPlayerHistory(['ADMIN'], false), false)
  assert.equal(showsPlayerHistory(['ADMIN'], true), true)
})

test('player stats keep the existing metrics and do not turn a missing card into zeros', () => {
  assert.deepEqual(playerStatRows(null), [])
  assert.deepEqual(playerStatRows({ goals: 0, assists: 2 }), [
    { key: 'goals', label: 'Голы', value: 0 },
    { key: 'assists', label: 'Ассисты', value: 2 },
  ])
})

test('team record uses the statistics payload and stays blank when it is missing', () => {
  assert.equal(teamRecordLine(null), '')
  assert.equal(teamRecordLine({ wins: 3, draws: 4, losses: 1 }), '3 победы · 4 ничьи · 1 поражение')
  assert.equal(teamRecordLine({ wins: 0, draws: 0, losses: 0 }), '0 побед · 0 ничьих · 0 поражений')
  assert.equal(myTeamMode(true), 'Вы капитан')
  assert.equal(myTeamMode(false), 'Вы игрок · просмотр')
})

test('calendar score keeps an unplayed dash, a finished 0:0, and penalties beside a draw', () => {
  assert.equal(calendarScore({ status: 'SCHEDULED', homeScore: 0, awayScore: 0 }), '– : –')
  assert.equal(calendarScore({ status: 'FINISHED', homeScore: 0, awayScore: 0 }), '0 : 0')
  const shootout = {
    status: 'FINISHED',
    homeScore: 1,
    awayScore: 1,
    penaltyHome: 5,
    penaltyAway: 4,
  }
  assert.equal(calendarScore(shootout), '1 : 1 · пен. 5 : 4')
  assert.deepEqual(calendarScoreParts(shootout), { score: '1 : 1', penalty: 'пен. 5 : 4' })
  assert.deepEqual(calendarScoreParts({ status: 'FINISHED', homeScore: 0, awayScore: 0 }), { score: '0 : 0', penalty: null })
})

test('RSVP does not send a repeat of the confirmed answer or a click while one is in flight', () => {
  assert.equal(shouldSendRsvp('', 'GOING', false), true)
  assert.equal(shouldSendRsvp('GOING', 'GOING', false), false)
  assert.equal(shouldSendRsvp('GOING', 'NOT_GOING', false), true)
  assert.equal(shouldSendRsvp('', 'GOING', true), false)
})

test('two calendar pages append to the full total without duplicate ids', async () => {
  const calls: number[] = []
  const rows = await collectPages(async (page) => {
    calls.push(page)
    const content = page === 0
      ? Array.from({ length: 10 }, (_, index) => ({ id: `m${index}` }))
      : Array.from({ length: 7 }, (_, index) => ({ id: `m${index + 10}` }))
    return { content, totalElements: 17, last: page === 1 }
  }, 10)
  assert.deepEqual(calls, [0, 1])
  assert.equal(rows.length, 17)
  assert.equal(new Set(rows.map((row) => row.id)).size, 17)
})
