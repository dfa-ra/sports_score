import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPeriodBlocks, compareMatchEvents, eventMinute } from '../src/lib/matchMinute.ts'

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
