import assert from 'node:assert/strict'
import test from 'node:test'
import { boardParams, goalsAside, goalsFromCard, highlightParts, playerSearchParams, playerSearchQuery, teamFromCard } from '../src/lib/q2Players.ts'
import {
  appliedLine,
  assembleTour,
  compareTours,
  countLive,
  dateSpan,
  filterRows,
  groupRows,
  heroCounts,
  matchProgress,
  playoffChampion,
  safeRoundTitle,
  stageLine,
  tableChampion,
} from '../src/lib/q2Tournaments.ts'

const sample = [
  { status: 'ACTIVE' as const },
  { status: 'ACTIVE' as const },
  { status: 'ACTIVE' as const },
  { status: 'REGISTRATION' as const },
  { status: 'FINISHED' as const },
  { status: 'FINISHED' as const },
]

test('hero figures stay on the full set when a chip filters the list', () => {
  assert.deepEqual(heroCounts(sample), { active: 3, registration: 1, total: 6 })
  assert.deepEqual(heroCounts(filterRows(sample, 'registration')), { active: 0, registration: 1, total: 1 })
  assert.deepEqual(heroCounts(sample), { active: 3, registration: 1, total: 6 })
  assert.deepEqual(groupRows(filterRows(sample, 'finished')).map((group) => group.title), ['Завершены'])
  assert.deepEqual(groupRows(sample).map((group) => group.title), ['Идут сейчас', 'Регистрация', 'Завершены'])
})

test('registration copy never invents a slot limit', () => {
  assert.equal(appliedLine('REGISTRATION', 3), 'Заявлено 3 команды')
  assert.equal(appliedLine('REGISTRATION', 5), 'Заявлено 5 команд')
  assert.equal(appliedLine('REGISTRATION', 1), 'Заявлено 1 команда')
  assert.equal(appliedLine('REGISTRATION', 0), 'Заявлено 0 команд')
  assert.equal(appliedLine('REGISTRATION', null), '')
  assert.equal(appliedLine('ACTIVE', 3), '')
  const row = assembleTour(
    { id: 's', name: 'Суперкубок', status: 'REGISTRATION', format: 'CUP', startDate: '2026-11-14' },
    { teamsCount: 3, liveKnown: true, progress: 0.5, roundTitle: 'Тур 2' },
  )
  assert.equal(row?.applied, 'Заявлено 3 команды')
  assert.equal(row?.progress, null)
  assert.equal(row?.stage, '')
  assert.equal(row?.badge, 'Регистрация')
  assert.doesNotMatch(JSON.stringify(row), /из |Тур \d|maxSquad/)
})

test('live counts ignore a pause and a winner is only a finished final or a single table', () => {
  const counts = countLive([
    { status: 'LIVE', tournamentId: 'cup' },
    { status: 'PAUSED', tournamentId: 'cup' },
    { status: 'LIVE', tournamentId: 'liga' },
    { status: 'FINISHED', tournamentId: 'liga' },
  ])
  assert.equal(counts.get('cup'), 1)
  assert.equal(counts.get('liga'), 1)
  const open = {
    rounds: [{ title: 'Финал', slots: [{ matchStatus: 'SCHEDULED', homeTeamName: 'А', awayTeamName: 'Б' }] }],
  }
  assert.equal(playoffChampion(open), null)
  const done = {
    rounds: [{
      title: 'Финал',
      slots: [{
        matchStatus: 'FINISHED',
        homeTeamName: 'Журналистика',
        awayTeamName: 'Физтех',
        homeTeamId: 'zh',
        awayTeamId: 'ft',
        homeScore: 2,
        awayScore: 1,
      }],
    }],
  }
  assert.deepEqual(playoffChampion(done), { name: 'Журналистика', teamId: 'zh' })
  assert.equal(tableChampion({
    tables: [
      { rows: [{ teamName: 'А', teamId: 'a' }] },
      { rows: [{ teamName: 'Б', teamId: 'b' }] },
    ],
  }), null)
  assert.deepEqual(tableChampion({
    tables: [{ rows: [{ teamName: 'Физтех', teamId: 'ft' }, { teamName: 'Вторая', teamId: 'v' }] }],
  }), { name: 'Физтех', teamId: 'ft' })
  const active = assembleTour(
    { id: 'cup', name: 'Кубок', status: 'ACTIVE', format: 'CUP', competitionStage: 'PLAYOFF' },
    { liveKnown: true, liveCount: 1, winnerName: 'Никто', roundTitle: 'Тур 2' },
  )
  assert.equal(active?.liveText, '1 матч live')
  assert.equal(active?.winnerName, '')
  assert.equal(active?.stage, 'Плей-офф')
  assert.equal(stageLine({
    status: 'ACTIVE',
    format: 'CUP',
    competitionStage: 'PLAYOFF',
    roundTitle: '1/4 финала',
  }), '1/4 финала')
  assert.equal(stageLine({ status: 'ACTIVE', format: 'ROUND_ROBIN', competitionStage: 'GROUPS' }), '')
  assert.equal(stageLine({ status: 'ACTIVE', format: 'GROUPS_PLAYOFF', competitionStage: 'GROUPS' }), 'Групповой этап')
  assert.equal(safeRoundTitle('Групповой этап · тур 2 из 5'), '')
  assert.equal(assembleTour({ id: 'd', name: 'Черновик', status: 'DRAFT' }, { liveKnown: true }), null)
})

test('running progress skips a registration tournament and a cancelled match', () => {
  assert.equal(matchProgress([
    { status: 'FINISHED', bracketRound: null },
    { status: 'CANCELLED', bracketRound: null },
    { status: 'SCHEDULED', bracketRound: null },
  ], false), 0.5)
  assert.equal(matchProgress([
    { status: 'FINISHED', bracketRound: 1 },
    { status: 'SCHEDULED', bracketRound: null },
  ], true), 1)
})

test('tournament rows sort by start date descending and keep an undated row last', () => {
  const rows = [
    { startDate: '2026-10-01', name: 'Ранний' },
    { startDate: '', name: 'Без даты' },
    { startDate: '2026-12-01', name: 'Поздний' },
  ].sort(compareTours)
  assert.deepEqual(rows.map((row) => row.name), ['Поздний', 'Ранний', 'Без даты'])
})

test('the all-tournaments board omits tournamentId and search highlights the typed name', () => {
  assert.deepEqual(boardParams(null, 10), { limit: 10 })
  assert.deepEqual(boardParams('liga', 20), { limit: 20, tournamentId: 'liga' })
  const parts = highlightParts('Орлов Илья', 'орл')
  assert.deepEqual(parts.filter((part) => part.hit).map((part) => part.text), ['Орл'])
  assert.equal(parts.map((part) => part.text).join(''), 'Орлов Илья')
  const full = highlightParts('Орлов Илья', '  ОРЛОВ   илья ')
  assert.equal(full.filter((part) => part.hit).map((part) => part.text).join(''), 'Орлов Илья')
  assert.equal(playerSearchQuery('  Орлов   Илья  '), 'Орлов Илья')
  assert.equal(playerSearchQuery('  игроков    толян '), 'игроков толян')
  assert.deepEqual(playerSearchParams('  Орлов   Илья ', 0, null), {
    q: 'Орлов Илья',
    page: 0,
    size: 10,
    sort: 'lastName,asc',
  })
  assert.deepEqual(playerSearchParams('Толян', 1, 'liga'), {
    q: 'Толян',
    page: 1,
    size: 10,
    sort: 'lastName,asc',
    tournamentId: 'liga',
  })
  assert.equal(goalsAside(null), '–')
  assert.equal(goalsAside(1), '1 гол')
  assert.equal(goalsAside(2), '2 гола')
  assert.equal(goalsAside(5), '5 голов')
  assert.equal(goalsFromCard({}), null)
  assert.equal(goalsFromCard({ statistics: { goals: 0 } }), 0)
  assert.equal(goalsFromCard({ matchHistory: [{ goals: 1 }, { goals: 3 }] }), 4)
  assert.deepEqual(teamFromCard({ team: { name: 'Физтех', logoUrl: '/a.png' } }), { name: 'Физтех', logo: '/a.png' })
  assert.equal(teamFromCard({}), null)
  assert.equal(dateSpan('2026-11-01', '2026-11-01'), '1 нояб. 2026')
  assert.equal(dateSpan('2026-11-01', '2026-12-01'), '1 нояб.\u00A0– 1 дек.')
  assert.equal(dateSpan('2025-11-01', '2026-01-01'), '1 нояб. 2025\u00A0– 1 янв. 2026')
})
