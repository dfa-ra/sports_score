import assert from 'node:assert/strict'
import test from 'node:test'
import { goalsAside, unitWord } from '../src/lib/q2Players.ts'

const forms = [
  { count: 1, goals: 'гол', assists: 'пас' },
  { count: 2, goals: 'гола', assists: 'паса' },
  { count: 5, goals: 'голов', assists: 'пасов' },
  { count: 100, goals: 'голов', assists: 'пасов' },
] as const

test('leader metric words follow the existing goal and assist forms', () => {
  for (const row of forms) {
    assert.equal(unitWord(row.count, 'goals'), row.goals, `goals ${row.count}`)
    assert.equal(unitWord(row.count, 'assists'), row.assists, `assists ${row.count}`)
    assert.equal(goalsAside(row.count), `${row.count} ${row.goals}`)
  }
  assert.equal(unitWord(11, 'goals'), 'голов')
  assert.equal(unitWord(21, 'goals'), 'гол')
  assert.equal(unitWord(11, 'assists'), 'пасов')
  assert.equal(unitWord(22, 'assists'), 'паса')
  assert.equal(unitWord(0, 'goals'), 'голов')
  assert.equal(goalsAside(null), '–')
})
