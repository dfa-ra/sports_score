import assert from 'node:assert/strict'
import test from 'node:test'
import { finishConfirmQuestion, publicScore, refereeBoardScore } from '../src/lib/match.ts'

test('a finished shootout uses the public score and a match without one stays spaced', () => {
  const drawn = { status: 'FINISHED', homeScore: 1, awayScore: 1, penaltyHome: 5, penaltyAway: 3 }
  assert.equal(publicScore(drawn), '1:1 (пен. 5:3)')
  assert.equal(refereeBoardScore(drawn), '1:1 (пен. 5:3)')
  assert.equal(refereeBoardScore({ status: 'FINISHED', homeScore: 2, awayScore: 1 }), '2 : 1')
  assert.equal(refereeBoardScore({ status: 'LIVE', homeScore: 1, awayScore: 1, penaltyHome: 5, penaltyAway: 3 }), '1 : 1')
  assert.equal(
    refereeBoardScore(
      { status: 'LIVE', homeScore: 1, awayScore: 1 },
      { kicks: [{ teamId: 'h' }], penaltyHome: 1, penaltyAway: 0 },
      true,
    ),
    '1:1 (пен. 1:0)',
  )
})

test('finish confirmation names a decided shootout and keeps the early-finish question otherwise', () => {
  assert.equal(finishConfirmQuestion({
    halfStillOpen: true,
    homeScore: 1,
    awayScore: 1,
    penaltyHome: 5,
    penaltyAway: 3,
    decisive: true,
  }), 'Завершить матч? Счёт 1:1, пен. 5:3')
  assert.equal(finishConfirmQuestion({
    halfStillOpen: true,
    homeScore: 2,
    awayScore: 0,
  }), 'Завершить матч до конца тайма? Счёт зафиксируется как есть.')
  assert.equal(finishConfirmQuestion({
    halfStillOpen: false,
    homeScore: 2,
    awayScore: 0,
  }), 'Завершить матч?')
  assert.equal(finishConfirmQuestion({
    halfStillOpen: true,
    homeScore: 1,
    awayScore: 1,
    penaltyHome: 1,
    penaltyAway: 0,
    decisive: false,
  }), 'Завершить матч до конца тайма? Счёт зафиксируется как есть.')
})
