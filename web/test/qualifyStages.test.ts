import assert from 'node:assert/strict'
import test from 'node:test'
import {
  firstPowerError,
  groupHeading,
  legendItems,
  powerMessage,
  qualifyLines,
  stageMap,
} from '../src/lib/qualifyStages.ts'

test('Zakhar groups: round of 16 has 8, quarterfinal is 4 direct plus 4 winners', () => {
  const lines = qualifyLines({ R8: 8, QF: 4 })
  assert.deepEqual(
    lines.map((line) => [line.stage, line.direct, line.fromPrev, line.total, line.powerOfTwo]),
    [
      ['R8', 8, 0, 8, true],
      ['QF', 4, 4, 8, true],
      ['SF', 0, 4, 4, true],
      ['F', 0, 2, 2, true],
    ],
  )
  assert.equal(firstPowerError(lines), '')
})

test('power-of-two copy names the stage, the direct places and the previous winners', () => {
  const line = { stage: 'QF' as const, direct: 4, fromPrev: 4, total: 8, powerOfTwo: false }
  assert.equal(
    powerMessage(line, 'R8'),
    'В 1/4 должно быть 8 команд: 4 напрямую + 4 победителя 1/8',
  )
  const broken = qualifyLines({ R8: 7, QF: 4 })
  assert.match(firstPowerError(broken), /В 1\/8 должно быть 8 команд: 7 напрямую/)
})

test('group heading and legend follow the stage colours', () => {
  assert.equal(groupHeading('Группа A', 6), 'ГРУППА A · 6 команд')
  assert.equal(groupHeading('Группа C', 1), 'ГРУППА C · 1 команда')
  const items = legendItems([
    { groupId: 'a', place: 1, stage: 'QF' },
    { groupId: 'a', place: 3, stage: 'R8' },
    { groupId: 'a', place: 5, stage: 'OUT' },
    { groupId: 'c', place: 8, stage: 'SILVER' },
  ])
  assert.deepEqual(items.map((item) => item.label), ['1/8', '1/4', 'Серебряная лига', 'Выбывает'])
  assert.equal(stageMap([{ groupId: 'a', place: 1, stage: 'QF' }]).a[1], 'QF')
})
