import assert from 'node:assert/strict'
import test from 'node:test'
import { boundaryMinute, buildMirrorFeed, buildPeriodBlocks, compareFeedEvents, compareMatchEvents, eventMinute, eventOnHomeSide, feedMinuteLabel, protocolMinute, protocolMinuteLimit } from '../src/lib/matchMinute.ts'

test('a period that lasted 26 seconds is the first minute, not 0 and not 15', () => {
  assert.equal(boundaryMinute(26), 1)
  assert.equal(boundaryMinute(0), 1)
  assert.equal(boundaryMinute(15 * 60), 15)
  assert.equal(protocolMinute({ eventType: 'PERIOD_END', gameTime: 26 }), 1)
  assert.equal(protocolMinute({ eventType: 'GOAL', gameTime: 2 * 60 }), 2)
})

test('protocol minute cap is the match length plus five minutes of added time', () => {
  assert.equal(protocolMinuteLimit(20 * 60, 1), 25)
  assert.equal(protocolMinuteLimit(15 * 60, 2), 35)
  assert.notEqual(protocolMinuteLimit(20 * 60, 1), 200)
})

test('2x15 kickoff is 1 and six seconds into the second half is 16', () => {
  assert.equal(protocolMinute({ eventType: 'PERIOD_START', period: 1, gameTime: 0 }), 1)
  assert.equal(eventMinute(2 * 60), 2)
  assert.equal(eventMinute(15 * 60 + 6), 16)
  assert.equal(eventMinute(15 * 60 + 6, 2, 15 * 60), 16)
  assert.equal(eventMinute(15 * 60, 1, 15 * 60), 15)
  assert.equal(eventMinute(15 * 60, 2, 15 * 60), 16)
  assert.equal(eventMinute(18 * 60), 18)
  assert.equal(protocolMinute({ eventType: 'PERIOD_END', period: 1, gameTime: 26 }), 1)
  assert.equal(protocolMinute({ eventType: 'PERIOD_START', period: 2, gameTime: 26 }), 1)
  assert.equal(protocolMinute({ eventType: 'PERIOD_END', period: 1, gameTime: 15 * 60 }), 15)
  assert.equal(protocolMinute({ eventType: 'PERIOD_START', period: 2, gameTime: 15 * 60 }), 16)
})

test('period boundaries are part of the public protocol', () => {
  const blocks = buildPeriodBlocks([
    {
      id: 'start',
      eventType: 'PERIOD_START',
      period: 1,
      gameTime: 0,
      timestamp: '2026-11-02T12:00:00Z',
    },
    {
      id: 'end',
      eventType: 'PERIOD_END',
      period: 1,
      gameTime: 26,
      timestamp: '2026-11-02T12:00:26Z',
    },
    {
      id: 'next',
      eventType: 'PERIOD_START',
      period: 2,
      gameTime: 26,
      timestamp: '2026-11-02T12:00:26Z',
    },
  ], { homeTeamId: 'home' })

  assert.deepEqual(blocks.map((block) => block.period), [1, 2])
  assert.deepEqual(blocks[0].items.map((item) => item.id), ['start', 'end'])
  assert.deepEqual(blocks[1].items.map((item) => item.id), ['next'])
  assert.equal(protocolMinute(blocks[0].items[1]), 1)
  assert.equal(protocolMinute(blocks[1].items[0]), 1)
})

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

test('a goal with no stored time does not print 0 minutes', () => {
  assert.equal(eventMinute(null), null)
  assert.equal(eventMinute(undefined), null)
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

test('overview feed drops whistles and scores each half on its own', () => {
  const home = 'home'
  const away = 'away'
  const feed = buildMirrorFeed([
    { id: 'whistle', eventType: 'PERIOD_START', period: 1, gameTime: 0, timestamp: '2026-11-01T12:00:00Z', createdAt: '2026-11-01T12:00:00Z' },
    { id: 'end', eventType: 'PERIOD_END', period: 1, gameTime: 15 * 60, timestamp: '2026-11-01T12:15:00Z', createdAt: '2026-11-01T12:15:00Z' },
    { id: 'h1', eventType: 'GOAL', teamId: home, period: 1, gameTime: 4 * 60, timestamp: '2026-11-01T12:04:00Z', createdAt: '2026-11-01T12:04:00Z' },
    { id: 'h2', eventType: 'GOAL', teamId: away, period: 2, gameTime: 20 * 60, timestamp: '2026-11-01T12:20:00Z', createdAt: '2026-11-01T12:20:00Z' },
    { id: 'card', eventType: 'YELLOW_CARD', teamId: home, period: 2, gameTime: 22 * 60, timestamp: '2026-11-01T12:22:00Z', createdAt: '2026-11-01T12:22:00Z' },
  ], { homeTeamId: home, periodLengthSeconds: 15 * 60 })

  assert.deepEqual(feed.blocks.map((block) => block.period), [1, 2])
  assert.equal(feed.blocks[0].score, '1-0')
  assert.equal(feed.blocks[0].items[0].scoreline, '1-0')
  assert.equal(feed.blocks[1].score, '0-1')
  assert.equal(feed.blocks[1].items[0].scoreline, '1-1')
  assert.equal(feed.blocks[1].items[1].scoreline, null)
  assert.equal(feed.total, '1-1')
  assert.deepEqual(feed.blocks.flatMap((block) => block.items.map((item) => item.id)), ['h1', 'h2', 'card'])
  assert.equal(feedMinuteLabel(feed.blocks[0].items[0], 15 * 60), "4'")
})

test('overview own goal is credited to the other side and keeps the stored minute', () => {
  const home = 'home'
  const feed = buildMirrorFeed([
    {
      id: 'own',
      eventType: 'GOAL',
      teamId: home,
      period: 1,
      gameTime: 6 * 60,
      createdAt: '2026-11-03T13:57:50.393351Z',
      metadata: { ownGoal: true },
    },
  ], { homeTeamId: home, periodLengthSeconds: 900 })
  assert.equal(feed.blocks[0].items[0].home, false)
  assert.equal(feed.blocks[0].score, '0-1')
  assert.equal(feed.blocks[0].items[0].scoreline, '0-1')
  assert.equal(feed.total, '0-1')
  assert.equal(feedMinuteLabel(feed.blocks[0].items[0], 900), "6'")
})

test('overview sort is minute, then added time when present, then createdAt, then id', () => {
  const home = 'home'
  const same = {
    eventType: 'YELLOW_CARD',
    teamId: home,
    period: 1,
    gameTime: 10 * 60,
  }
  const feed = buildMirrorFeed([
    { ...same, id: 'b', createdAt: '2026-11-01T12:00:02Z' },
    { ...same, id: 'a', createdAt: '2026-11-01T12:00:01Z', metadata: { addedMinute: 2 } },
    { ...same, id: 'c', createdAt: '2026-11-01T12:00:01Z' },
  ], { homeTeamId: home, periodLengthSeconds: 15 * 60 })
  assert.deepEqual(feed.blocks[0].items.map((item) => item.id), ['c', 'b', 'a'])
  assert.equal(feedMinuteLabel(feed.blocks[0].items[2], 15 * 60), "15+2'")
  assert.equal(feedMinuteLabel({ eventType: 'GOAL', gameTime: 9, period: 1 }, 1200), "0'")
  assert.equal(feedMinuteLabel({ eventType: 'GOAL', gameTime: 1, period: 1, metadata: { addedMinute: 4 } }, 900), "15+4'")
  assert.equal(feedMinuteLabel({ eventType: 'GOAL', gameTime: 1, period: 2, metadata: { addedMinute: 4 } }, 900), "30+4'")
  assert.equal(feedMinuteLabel({ eventType: 'GOAL', gameTime: 9, period: 1, metadata: { addedMinute: 4 } }, null), "0+4'")
  assert.equal(feedMinuteLabel({ eventType: 'GOAL', gameTime: 20 * 60, period: 1, metadata: { addedMinute: 4 } }, 1200), "20+4'")
  const tied = [
    { id: 'z', eventType: 'GOAL', gameTime: 60, createdAt: '2026-11-01T12:00:00Z' },
    { id: 'm', eventType: 'GOAL', gameTime: 60, createdAt: '2026-11-01T12:00:00Z' },
  ].sort((a, b) => compareFeedEvents(a, b, 900))
  assert.deepEqual(tied.map((item) => item.id), ['m', 'z'])
})

test('a started half with no other events still gets a 0-0 plate', () => {
  const home = 'home'
  const started = buildMirrorFeed([
    { id: 's1', eventType: 'PERIOD_START', period: 1, gameTime: 0 },
    { id: 'g', eventType: 'GOAL', teamId: home, period: 1, gameTime: 9 },
    { id: 'e1', eventType: 'PERIOD_END', period: 1, gameTime: 39 },
    { id: 's2', eventType: 'PERIOD_START', period: 2, gameTime: 1200 },
  ], { homeTeamId: home, periodLengthSeconds: 1200, period: 2 })
  assert.deepEqual(started.blocks.map((block) => [block.period, block.score, block.items.length]), [
    [1, '1-0', 1],
    [2, '0-0', 0],
  ])
  assert.equal(started.total, '1-0')

  const quietFirst = buildMirrorFeed([
    { id: 's2', eventType: 'PERIOD_START', period: 2, gameTime: 900 },
    { id: 'g2', eventType: 'GOAL', teamId: 'away', period: 2, gameTime: 1000 },
  ], { homeTeamId: home, periodLengthSeconds: 900, period: 2 })
  assert.deepEqual(quietFirst.blocks.map((block) => [block.period, block.score, block.items.length]), [
    [1, '0-0', 0],
    [2, '0-1', 1],
  ])

  const notStarted = buildMirrorFeed([
    { id: 'g1', eventType: 'GOAL', teamId: home, period: 1, gameTime: 60 },
  ], { homeTeamId: home, periodLengthSeconds: 900, period: 1 })
  assert.deepEqual(notStarted.blocks.map((block) => block.period), [1])

  const beforeKickoff = buildMirrorFeed([], { homeTeamId: home, period: 1 })
  assert.deepEqual(beforeKickoff.blocks, [])
})
