import assert from 'node:assert/strict'
import test from 'node:test'
import { goalsAside, unitWord } from '../src/lib/q2Players.ts'

const forms = [
  { count: 0, goals: 'голов', assists: 'передач' },
  { count: 1, goals: 'гол', assists: 'передача' },
  { count: 2, goals: 'гола', assists: 'передачи' },
  { count: 4, goals: 'гола', assists: 'передачи' },
  { count: 5, goals: 'голов', assists: 'передач' },
  { count: 11, goals: 'голов', assists: 'передач' },
  { count: 12, goals: 'голов', assists: 'передач' },
  { count: 21, goals: 'гол', assists: 'передача' },
  { count: 22, goals: 'гола', assists: 'передачи' },
  { count: 31, goals: 'гол', assists: 'передача' },
  { count: 100, goals: 'голов', assists: 'передач' },
] as const

test('leader metric words follow the existing goal and assist forms', () => {
  for (const row of forms) {
    assert.equal(unitWord(row.count, 'goals'), row.goals, `goals ${row.count}`)
    assert.equal(unitWord(row.count, 'assists'), row.assists, `assists ${row.count}`)
    assert.equal(goalsAside(row.count), `${row.count} ${row.goals}`)
  }
  assert.equal(unitWord(11, 'goals'), 'голов')
  assert.equal(unitWord(21, 'goals'), 'гол')
  assert.equal(unitWord(11, 'assists'), 'передач')
  assert.equal(unitWord(22, 'assists'), 'передачи')
  assert.equal(unitWord(0, 'goals'), 'голов')
  assert.equal(goalsAside(null), '–')
})
