import assert from 'node:assert/strict'
import test from 'node:test'
import { goalsAside, leaderBoardTitle, unitWord } from '../src/lib/q2Players.ts'

const forms = [
  { count: 0, goals: 'голов', assists: 'ассистов' },
  { count: 1, goals: 'гол', assists: 'ассист' },
  { count: 2, goals: 'гола', assists: 'ассиста' },
  { count: 4, goals: 'гола', assists: 'ассиста' },
  { count: 5, goals: 'голов', assists: 'ассистов' },
  { count: 11, goals: 'голов', assists: 'ассистов' },
  { count: 12, goals: 'голов', assists: 'ассистов' },
  { count: 21, goals: 'гол', assists: 'ассист' },
  { count: 22, goals: 'гола', assists: 'ассиста' },
  { count: 31, goals: 'гол', assists: 'ассист' },
  { count: 100, goals: 'голов', assists: 'ассистов' },
] as const

test('leader metric words follow the existing goal and assist forms', () => {
  for (const row of forms) {
    assert.equal(unitWord(row.count, 'goals'), row.goals, `goals ${row.count}`)
    assert.equal(unitWord(row.count, 'assists'), row.assists, `assists ${row.count}`)
    assert.equal(goalsAside(row.count), `${row.count} ${row.goals}`)
  }
  assert.equal(unitWord(11, 'goals'), 'голов')
  assert.equal(unitWord(21, 'goals'), 'гол')
  assert.equal(unitWord(11, 'assists'), 'ассистов')
  assert.equal(unitWord(22, 'assists'), 'ассиста')
  assert.equal(unitWord(0, 'goals'), 'голов')
  assert.equal(goalsAside(null), '–')
})

test('players leader table title follows the goals and passes tabs', () => {
  assert.equal(leaderBoardTitle('goals'), 'Бомбардиры')
  assert.equal(leaderBoardTitle('assists'), 'Ассистенты')
})
