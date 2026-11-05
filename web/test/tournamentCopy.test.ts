import assert from 'node:assert/strict'
import test from 'node:test'
import { publicTournamentDescription } from '../src/lib/tournamentCopy.ts'

const DEMO = 'Демо-сезон, чтобы таблица и статистика не были пустыми.'

test('the seeded demo sentence is not shown', () => {
  assert.equal(publicTournamentDescription(DEMO), '')
  assert.equal(publicTournamentDescription(`  ${DEMO}  `), '')
  assert.equal(publicTournamentDescription(DEMO.slice(0, -1)), '')
})

test('any Демо-сезон description is omitted', () => {
  assert.equal(publicTournamentDescription('Демо-сезон для пустой таблицы.'), '')
  assert.equal(publicTournamentDescription('демо-сезон, короткий вариант'), '')
  assert.equal(publicTournamentDescription('Демо-сезон\nчтобы не было пусто'), '')
})

test('a real description stays, and the demo sentence is cut out of it', () => {
  assert.equal(publicTournamentDescription('Кубок факультетов ИТМО.'), 'Кубок факультетов ИТМО.')
  assert.equal(
    publicTournamentDescription(`Кубок факультетов. ${DEMO}`),
    'Кубок факультетов.',
  )
  assert.equal(
    publicTournamentDescription(`Сезон 2026. ${DEMO} Игры по субботам.`),
    'Сезон 2026. Игры по субботам.',
  )
  assert.equal(
    publicTournamentDescription(`${DEMO}\nИгры по субботам.`),
    'Игры по субботам.',
  )
  assert.equal(publicTournamentDescription(null), '')
  assert.equal(publicTournamentDescription(undefined), '')
  assert.equal(publicTournamentDescription(''), '')
})
