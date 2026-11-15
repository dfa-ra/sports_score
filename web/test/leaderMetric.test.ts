import assert from 'node:assert/strict'
import test from 'node:test'
import { goalsAside, leaderBoardTitle, unitWord } from '../src/lib/q2Players.ts'

const forms = [
  { count: 0, goals: 'голов', assists: 'ассистентов' },
  { count: 1, goals: 'гол', assists: 'ассистент' },
  { count: 2, goals: 'гола', assists: 'ассистента' },
  { count: 4, goals: 'гола', assists: 'ассистента' },
  { count: 5, goals: 'голов', assists: 'ассистентов' },
  { count: 11, goals: 'голов', assists: 'ассистентов' },
  { count: 12, goals: 'голов', assists: 'ассистентов' },
  { count: 21, goals: 'гол', assists: 'ассистент' },
  { count: 22, goals: 'гола', assists: 'ассистента' },
  { count: 31, goals: 'гол', assists: 'ассистент' },
  { count: 100, goals: 'голов', assists: 'ассистентов' },
] as const

test('leader metric words follow the existing goal and assist forms', () => {
  for (const row of forms) {
    assert.equal(unitWord(row.count, 'goals'), row.goals, `goals ${row.count}`)
    assert.equal(unitWord(row.count, 'assists'), row.assists, `assists ${row.count}`)
    assert.equal(goalsAside(row.count), `${row.count} ${row.goals}`)
  }
  assert.equal(unitWord(11, 'goals'), 'голов')
  assert.equal(unitWord(21, 'goals'), 'гол')
  assert.equal(unitWord(11, 'assists'), 'ассистентов')
  assert.equal(unitWord(22, 'assists'), 'ассистента')
  assert.equal(unitWord(0, 'goals'), 'голов')
  assert.equal(goalsAside(null), '–')
})

test('players leader table title follows the goals and passes tabs', () => {
  assert.equal(leaderBoardTitle('goals'), 'Бомбардиры')
  assert.equal(leaderBoardTitle('assists'), 'Ассистенты')
})
