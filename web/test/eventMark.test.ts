import assert from 'node:assert/strict'
import test from 'node:test'
import { cardTally, eventMark } from '../src/lib/eventMark.ts'

const backendTypes = [
  'GOAL',
  'ASSIST',
  'YELLOW_CARD',
  'RED_CARD',
  'FOUL',
  'TIMEOUT',
  'SUBSTITUTION',
  'POINT',
  'PERIOD_START',
  'PERIOD_END',
  'OTHER',
] as const

test('every backend event type has an icon kind and a Russian label', () => {
  const expected: Record<string, string> = {
    GOAL: 'Гол',
    ASSIST: 'Передача',
    YELLOW_CARD: 'Жёлтая',
    RED_CARD: 'Красная',
    FOUL: 'Фол',
    TIMEOUT: 'Тайм-аут',
    SUBSTITUTION: 'Замена',
    POINT: 'Очко',
    PERIOD_START: 'Начало тайма',
    PERIOD_END: 'Конец тайма',
    OTHER: 'Событие',
  }
  for (const type of backendTypes) {
    const mark = eventMark({ eventType: type })
    assert.equal(mark.label, expected[type], type)
    assert.ok(mark.kind, type)
    assert.notEqual(mark.label, '')
    assert.notEqual(mark.label, type)
  }
})

test('own goal, second yellow, and penalties keep a mark when the API sends them as flags or types', () => {
  assert.deepEqual(eventMark({ eventType: 'GOAL', metadata: { ownGoal: true } }), { kind: 'own_goal', label: 'Автогол' })
  assert.deepEqual(eventMark({ eventType: 'OWN_GOAL' }), { kind: 'own_goal', label: 'Автогол' })
  assert.deepEqual(eventMark({ eventType: 'SECOND_YELLOW' }), { kind: 'second_yellow', label: 'Вторая жёлтая' })
  assert.deepEqual(eventMark({ eventType: 'YELLOW_CARD', metadata: { secondYellow: true } }), { kind: 'second_yellow', label: 'Вторая жёлтая' })
  assert.deepEqual(eventMark({ eventType: 'PENALTY_SCORED' }), { kind: 'penalty_scored', label: 'Пенальти' })
  assert.deepEqual(eventMark({ eventType: 'GOAL', metadata: { penalty: true } }), { kind: 'penalty_scored', label: 'Пенальти' })
  assert.deepEqual(eventMark({ eventType: 'PENALTY_MISSED' }), { kind: 'penalty_missed', label: 'Пенальти мимо' })
  assert.deepEqual(eventMark({ eventType: 'GOAL', metadata: { penaltyMissed: true } }), { kind: 'penalty_missed', label: 'Пенальти мимо' })
})

test('an unknown type is a labelled fallback, not a blank row', () => {
  const mark = eventMark({ eventType: 'WATER_BREAK' })
  assert.equal(mark.kind, 'other')
  assert.equal(mark.label, 'Событие')
  assert.notEqual(mark.label, 'WATER_BREAK')
  assert.equal(eventMark({ eventType: '' }).label, 'Событие')
  assert.equal(eventMark(null).label, 'Событие')
})

test('card counters follow the team and ignore a voided card', () => {
  const home = 'home'
  const away = 'away'
  const tally = cardTally([
    { eventType: 'YELLOW_CARD', teamId: away, voided: false },
    { eventType: 'YELLOW_CARD', teamId: home, voided: true },
    { eventType: 'RED_CARD', teamId: home, voided: false },
    { eventType: 'SECOND_YELLOW', teamId: away, voided: false },
    { eventType: 'GOAL', teamId: home, voided: false },
  ], home)
  assert.deepEqual(tally, { homeYellow: 0, awayYellow: 2, homeRed: 1, awayRed: 1 })
})
