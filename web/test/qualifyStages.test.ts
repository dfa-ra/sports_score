import assert from 'node:assert/strict'
import test from 'node:test'
import {
  firstPowerError,
  groupHeading,
  legendItems,
  placeOutOfRange,
  powerMessage,
  qualifyLines,
  stageMap,
  summaryTitle,
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

test('a place past the group size uses the same sentence and blocks the save', () => {
  assert.equal(
    placeOutOfRange(
      [{ id: 'a', name: 'Группа A', teamCount: 6 }],
      [{ groupId: 'a', placeFrom: 7, placeTo: 7 }],
    ),
    'Место 7 в группе «Группа A» больше числа команд (6)',
  )
  assert.equal(
    placeOutOfRange(
      [{ id: 'c', name: 'Группа C', teamCount: 3 }],
      [{ groupId: 'c', placeFrom: 1, placeTo: 4 }],
    ),
    'В группе C теперь 3 команды, а правило до места 4',
  )
  assert.equal(
    placeOutOfRange(
      [{ id: 'a', name: 'Группа A', teamCount: 6 }],
      [{ groupId: 'a', placeFrom: 3, placeTo: 2 }],
    ),
    'Диапазон мест 3–2 в группе «Группа A» перепутан',
  )
  assert.equal(
    placeOutOfRange(
      [{ id: 'a', name: 'Группа A', teamCount: 6 }],
      [{ groupId: 'a', placeFrom: 1, placeTo: 6 }],
    ),
    '',
  )
})

test('two teams close the summary on the final row', () => {
  const two = qualifyLines({ SF: 2 })
  assert.equal(summaryTitle(two[0], two, 0), 'Финал')
  const full = qualifyLines({ R8: 8, QF: 4 })
  assert.equal(summaryTitle(full[3], full, 3), 'Финал')
  assert.equal(summaryTitle(full[0], full, 0), '1/8')
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
  assert.equal(stageMap([{ groupId: 'a', place: 5, stage: 'OUT' }]).a[5], 'OUT')
})
