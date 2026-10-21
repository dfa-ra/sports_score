import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPeriodBlocks, compareMatchEvents, eventMinute, eventOnHomeSide } from '../src/lib/matchMinute.ts'

test('13:00 left in a 15:00 first half displays 2 minutes, not 13', () => {
  const stored = 2 * 60
  assert.equal(eventMinute(stored), 2)
  assert.notEqual(eventMinute(stored), 13)
})

test('12:00 left in the second half of a 2x15 match displays 18', () => {
  const stored = 18 * 60
  assert.equal(eventMinute(stored), 18)
  assert.notEqual(eventMinute(stored), 12)
  assert.notEqual(eventMinute(stored), 3)
})

test('admin minute 18 is the minute a logged-out viewer sees', () => {
  assert.equal(eventMinute(18 * 60), 18)
})

test('protocol order follows the match minute, earlier first', () => {
  const home = 'home'
  const away = 'away'
  const blocks = buildPeriodBlocks([
    {
      id: 'second-half',
      eventType: 'GOAL',
      teamId: home,
      period: 2,
      gameTime: 18 * 60,
      timestamp: '2026-10-04T12:18:00Z',
    },
    {
      id: 'first-half-late',
      eventType: 'GOAL',
      teamId: away,
      period: 1,
      gameTime: 11 * 60,
      timestamp: '2026-10-04T12:30:00Z',
    },
    {
      id: 'first-half-early',
      eventType: 'GOAL',
      teamId: home,
      period: 1,
      gameTime: 2 * 60,
      timestamp: '2026-10-04T12:02:00Z',
    },
    {
      id: 'card',
      eventType: 'YELLOW_CARD',
      teamId: away,
      period: 2,
      gameTime: 20 * 60,
      timestamp: '2026-10-04T12:20:00Z',
    },
  ], { homeTeamId: home })

  assert.deepEqual(blocks.map((block) => block.period), [1, 2])
  assert.deepEqual(blocks[0].items.map((item) => eventMinute(item.gameTime)), [2, 11])
  assert.deepEqual(blocks[0].items.map((item) => item.scoreline), ['1-0', '1-1'])
  assert.equal(blocks[0].score, '1-1')
  assert.deepEqual(blocks[1].items.map((item) => eventMinute(item.gameTime)), [18, 20])
  assert.equal(blocks[1].items[0].scoreline, '2-1')
  assert.equal(blocks[1].items[1].scoreline, null)
  assert.equal(blocks[1].score, '2-1')

  const ordered = [...blocks[0].items, ...blocks[1].items].map((item) => item.id)
  const byWritten = ['second-half', 'first-half-late', 'first-half-early', 'card']
    .map((id) => ({ id, gameTime: id === 'first-half-early' ? 120 : id === 'first-half-late' ? 660 : id === 'second-half' ? 1080 : 1200 }))
    .sort(compareMatchEvents)
    .map((item) => item.id)
  assert.deepEqual(ordered, ['first-half-early', 'first-half-late', 'second-half', 'card'])
  assert.deepEqual(byWritten, ordered)
})

test('an own goal adds the minute to the other team and keeps the kickoff minute', () => {
  const home = 'home'
  const blocks = buildPeriodBlocks([
    {
      id: 'own',
      eventType: 'GOAL',
      teamId: home,
      period: 1,
      gameTime: 5 * 60,
      timestamp: '2026-10-04T12:05:00Z',
      metadata: { ownGoal: true },
    },
    {
      id: 'normal',
      eventType: 'GOAL',
      teamId: home,
      period: 1,
      gameTime: 9 * 60,
      timestamp: '2026-10-04T12:09:00Z',
    },
  ], { homeTeamId: home })

  assert.equal(eventMinute(blocks[0].items[0].gameTime), 5)
  assert.equal(blocks[0].items[0].home, false)
  assert.equal(blocks[0].items[1].home, true)
  assert.deepEqual(blocks[0].items.map((item) => item.scoreline), ['0-1', '1-1'])
  assert.equal(blocks[0].score, '1-1')
})

test('an own goal by the away player is drawn on the home side and raises the home score', () => {
  const home = 'home'
  const away = 'away'
  const blocks = buildPeriodBlocks([
    {
      id: 'card',
      eventType: 'YELLOW_CARD',
      teamId: away,
      playerId: 'away-card',
      period: 2,
      gameTime: 23 * 60,
      timestamp: '2026-10-18T12:23:00Z',
    },
    {
      id: 'own',
      eventType: 'GOAL',
      teamId: away,
      playerId: 'away-scorer',
      period: 2,
      gameTime: 28 * 60,
      timestamp: '2026-10-18T12:28:00Z',
      metadata: { ownGoal: true },
    },
  ], { homeTeamId: home })

  const card = blocks[0].items[0]
  const own = blocks[0].items[1]
  assert.equal(eventOnHomeSide(card, home), false)
  assert.equal(card.home, false)
  assert.equal(card.scoreline, null)
  assert.equal(eventOnHomeSide(own, home), true)
  assert.equal(own.home, true)
  assert.equal(own.scoreline, '1-0')
  assert.equal(own.playerId, 'away-scorer')
  assert.equal(own.teamId, away)
  assert.equal(blocks[0].score, '1-0')
})
