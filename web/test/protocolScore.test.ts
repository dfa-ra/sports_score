import assert from 'node:assert/strict'
import test from 'node:test'
import { protocolScore } from '../src/lib/match.ts'

const match = { homeTeamId: 'home', awayTeamId: 'away', sportCode: 'FOOTBALL' }

test('protocol score counts a goal and drops it when the same event is voided', () => {
  const goal = { id: 'g1', eventType: 'GOAL', teamId: 'home', voided: false, metadata: {} }
  assert.deepEqual(protocolScore(match, [goal]), { home: 1, away: 0 })
  assert.deepEqual(protocolScore(match, [{ ...goal, voided: true }]), { home: 0, away: 0 })
  assert.deepEqual(
    protocolScore(match, [{ ...goal, teamId: 'away', metadata: { ownGoal: true } }]),
    { home: 1, away: 0 },
  )
})

test('a repeated event id does not add a second goal when the caller replaces the row', () => {
  const goal = { eventType: 'GOAL', teamId: 'home', voided: false }
  assert.deepEqual(protocolScore(match, [goal, goal]), { home: 2, away: 0 })
  assert.deepEqual(protocolScore(match, [goal]), { home: 1, away: 0 })
})
