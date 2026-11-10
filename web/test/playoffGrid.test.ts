import assert from 'node:assert/strict'
import test from 'node:test'
import { matchCardStage, protocolStage } from '../src/lib/playoffLabel.ts'
import {
  arrivalLine,
  championCard,
  bracketConnectorPaths,
  bracketIsEmpty,
  bracketPanels,
  bronzeBoardColumns,
  bronzeChampionCell,
  bronzeEarlyCell,
  bronzeFinalCell,
  bronzeMatchRows,
  bronzeSemiCell,
  bronzeThirdRow,
  columnSlots,
  gridRows,
  linkSlots,
  thirdPlaceSlots,
  championCell,
  championHeaderCell,
  defaultRoundIndex,
  emptySideLabel,
  footerView,
  formatRoundSpan,
  roundNote,
  cssViewportWidth,
  gridColumns,
  gridMinWidth,
  overviewMeta,
  pairWhen,
  scoreView,
  sideKind,
  stackChampion,
  tournamentHasPlayoff,
  useRoundTabs,
  winnerSide,
} from '../src/lib/playoffGrid.ts'

test('round tabs follow team count and width', () => {
  assert.equal(useRoundTabs(2, 375), false)
  assert.equal(useRoundTabs(4, 375), true)
  assert.equal(useRoundTabs(4, 721), false)
  assert.equal(useRoundTabs(8, 720), true)
  assert.equal(useRoundTabs(8, 900), false)
  assert.equal(useRoundTabs(16, 1024), true)
  assert.equal(useRoundTabs(16, 1180), true)
  assert.equal(useRoundTabs(16, 1181), false)
  assert.equal(useRoundTabs(16, 1440), false)
  const boardWidth = gridMinWidth(4, true)
  assert.equal(boardWidth, 220 + 3 * 150 + 3 * 22)
  assert.equal(useRoundTabs(16, 1440, boardWidth - 1), true)
  assert.equal(useRoundTabs(16, 1440, boardWidth), false)
  assert.equal(useRoundTabs(16, 1440, 0), false)
  assert.equal(useRoundTabs(8, 900, 400), false)
  assert.equal(useRoundTabs(4, 1000, 200), false)
  assert.equal(useRoundTabs(2, 400, 100), false)
})

test('zoom uses the visual CSS width and leaves 4 and 8 teams on 720', () => {
  assert.equal(cssViewportWidth(1280, 1024, 1.25), 1024)
  assert.equal(cssViewportWidth(1280, 853, 1.5), 853)
  assert.equal(cssViewportWidth(1280, 731, 1.75), 731)
  assert.equal(cssViewportWidth(1280, 1280, 1), 1280)
  assert.equal(cssViewportWidth(1181, 1166, 1), 1181)
  assert.equal(cssViewportWidth(1180, 1180, 1), 1180)
  assert.equal(useRoundTabs(16, cssViewportWidth(1280, 1024, 1.25)), true)
  assert.equal(useRoundTabs(16, cssViewportWidth(1280, 853, 1.5)), true)
  assert.equal(useRoundTabs(16, cssViewportWidth(1280, 731, 1.75)), true)
  assert.equal(useRoundTabs(16, cssViewportWidth(1280, 1180, 1280 / 1180)), true)
  assert.equal(useRoundTabs(16, cssViewportWidth(1280, 1181, 1280 / 1181)), false)
  assert.equal(useRoundTabs(8, cssViewportWidth(1280, 731, 1.75)), false)
  assert.equal(useRoundTabs(4, cssViewportWidth(1280, 1024, 1.25)), false)
  assert.equal(useRoundTabs(8, cssViewportWidth(720, 720, 1)), true)
  assert.equal(useRoundTabs(2, cssViewportWidth(400, 200, 2)), false)
})

test('a two-team final stacks the champion under the match on a phone', () => {
  assert.equal(stackChampion(1, 375), true)
  assert.equal(stackChampion(1, 720), true)
  assert.equal(stackChampion(1, 721), false)
  assert.equal(stackChampion(2, 375), false)
  assert.equal(stackChampion(4, 375), false)
  assert.equal(gridColumns(1, false, false, true), 'minmax(0, 1fr)')
  assert.equal(gridColumns(1, false, false, false), 'minmax(0, 238px)')
  assert.equal(gridColumns(3, false, false).endsWith('1fr'), false)
  assert.equal(gridColumns(3, true, false).includes('214px'), true)
  assert.deepEqual(championHeaderCell(1, true), { gridColumn: '1', gridRow: '3' })
  assert.deepEqual(championCell(1, 1, true), { gridColumn: '1', gridRow: '4' })
  assert.deepEqual(championHeaderCell(1, false), { gridColumn: '2', gridRow: '1' })
  assert.deepEqual(championCell(3, 4, false), { gridColumn: '7', gridRow: '2 / span 4' })
})

test('empty slot keeps the source pair number', () => {
  assert.equal(emptySideLabel({ slotIndex: 0 }, true, '1/4 финала'), 'Победитель 1/4 №1')
  assert.equal(emptySideLabel({ slotIndex: 0 }, false, '1/4 финала'), 'Победитель 1/4 №2')
  assert.equal(emptySideLabel({ slotIndex: 1, awayWaitingLabel: 'Победитель 1/2 №2' }, false, '1/2 финала'), 'Победитель 1/2 №2')
})

test('scores, penalties and an unplayed dash', () => {
  assert.deepEqual(scoreView({ matchStatus: 'SCHEDULED', homeScore: 0, awayScore: 0 }, true), { text: '–', pens: null, dash: true })
  assert.equal(sideKind({ matchStatus: 'FINISHED', homeTeamId: 'a', awayTeamId: 'b', homeScore: 3, awayScore: 1 }, true), 'win')
  assert.equal(sideKind({ matchStatus: 'FINISHED', homeTeamId: 'a', awayTeamId: 'b', homeScore: 3, awayScore: 1 }, false), 'lose')
  assert.equal(sideKind({ matchStatus: 'LIVE', homeTeamId: 'a', awayTeamId: 'b', homeScore: 2, awayScore: 1 }, true), 'open')
  assert.equal(winnerSide({ matchStatus: 'FINISHED', homeScore: 2, awayScore: 2, penaltyHome: 4, penaltyAway: 3 }), 'home')
  assert.equal(winnerSide({ matchStatus: 'FINISHED', homeScore: 2, awayScore: 2 }), null)
  assert.equal(footerView({ matchStatus: 'LIVE', minute: 24, venue: 'Зал' }).label, 'Live 24′')
  assert.equal(footerView({ matchStatus: 'LIVE' }).label, 'Live')
  assert.equal(footerView({ matchStatus: 'FINISHED', homeScore: 2, awayScore: 2, penaltyHome: 4, penaltyAway: 3 }).label, 'По пен. 4:3')
  assert.equal(footerView({ matchStatus: 'SCHEDULED' }).label, '')
  assert.equal(footerView({ matchStatus: 'SCHEDULED' }).right, 'Дата уточняется')
  assert.notEqual(footerView({ matchStatus: 'SCHEDULED' }).label, 'Скоро')
  assert.equal(footerView({ matchStatus: 'SCHEDULED', scheduledAt: '2026-11-05T16:00:00Z' }).label, 'Скоро')
  assert.equal(footerView({ matchStatus: 'SCHEDULED', scheduledAt: '2026-11-05T16:00:00Z' }).right, '05/11 · 19:00')
  assert.equal(pairWhen('2026-11-05T16:00:00Z'), '05/11 · 19:00')
  assert.equal(pairWhen('2026-11-04T22:30:00Z'), '05/11 · 01:30')
  assert.equal(
    footerView({
      matchStatus: 'FINISHED',
      scheduledAt: '2026-11-05T16:00:00Z',
      startedAt: '2026-11-03T12:00:00Z',
    }).right,
    '05/11 · 19:00',
  )
  assert.equal(
    footerView({ matchStatus: 'FINISHED', scheduledAt: null, startedAt: '2026-11-03T12:00:00Z' }).right,
    '03/11 · 15:00',
  )
  assert.equal(
    footerView({ matchStatus: 'FINISHED', scheduledAt: null, startedAt: null }).right,
    'Дата уточняется',
  )
  assert.equal(
    roundNote([{ matchStatus: 'FINISHED', scheduledAt: null, startedAt: '2026-11-03T12:00:00Z' }]).text,
    '3 нояб · завершён',
  )
})

test('round span, default tab and arrival line', () => {
  assert.equal(formatRoundSpan(['2026-10-28T15:00:00Z', '2026-10-31T15:00:00Z']), '28–31 окт')
  const rounds = [
    { slots: [{ matchStatus: 'FINISHED' }, { matchStatus: 'FINISHED' }] },
    { slots: [{ matchStatus: 'LIVE' }, { matchStatus: 'SCHEDULED' }] },
    { slots: [{ matchStatus: null }] },
  ]
  assert.equal(defaultRoundIndex(rounds), 1)
  assert.equal(defaultRoundIndex([{ slots: [{ matchStatus: 'FINISHED' }] }]), 0)
  const line = arrivalLine({
    matchStatus: 'FINISHED',
    homeTeamName: 'Юристы',
    awayTeamName: 'Медики',
    homeScore: 2,
    awayScore: 2,
    penaltyHome: 4,
    penaltyAway: 3,
  }, '1/4 финала')
  assert.deepEqual(line, { name: 'Юристы', detail: '1/4: 2:2 (пен. 4:3), Медики' })
})

test('empty bracket, playoff tab and header meta skip blanks', () => {
  assert.equal(bracketIsEmpty([]), true)
  assert.equal(bracketIsEmpty([{ slots: [{ homeWaitingLabel: 'Победитель 1/4 №1' }] }]), false)
  assert.equal(tournamentHasPlayoff({ format: 'GROUPS_PLAYOFF', stage: 'GROUPS' }), true)
  assert.equal(tournamentHasPlayoff({ format: 'ROUND_ROBIN', stage: 'GROUPS' }), false)
  assert.equal(tournamentHasPlayoff({ format: 'ROUND_ROBIN', hasPlayoff: false, rounds: [{ slots: [] }] }), true)
  assert.deepEqual(overviewMeta({
    seasonYear: 2026,
    format: 'GROUPS_PLAYOFF',
    teamsCount: 8,
    startDate: '2026-08-01',
    endDate: '2026-12-15',
    stage: 'PLAYOFF',
  }), ['Сезон 2026', 'Группы + плей-офф', '8 команд', '01.08 – 15.12.2026', 'Стадия: плей-офф'])
  assert.deepEqual(overviewMeta({ format: 'CUP', teamsCount: 1 }), ['Кубок / плей-офф', '1 команда'])
})

test('third-place slot is not a final column or a bracket link', () => {
  const finalSlot = { slotIndex: 0, roundTitle: 'Финал', kind: 'MAIN' }
  const bronze = { slotIndex: 1, roundTitle: 'Матч за 3-е место', kind: 'THIRD' }
  const semis = [{ slotIndex: 0 }, { slotIndex: 1 }]
  assert.deepEqual(columnSlots([finalSlot, bronze], true), [finalSlot])
  assert.deepEqual(linkSlots([finalSlot, bronze]), [finalSlot])
  assert.deepEqual(thirdPlaceSlots([finalSlot, bronze]), [bronze])
  assert.equal(linkSlots(semis).length, 2)
  assert.equal(matchCardStage({ thirdPlace: true, stageLabel: 'За 3-е место' }), 'Матч за 3-е')
  assert.equal(protocolStage({ thirdPlace: true, stageLabel: 'Финал' }), 'За 3-е место')
  assert.equal(protocolStage({ thirdPlace: false, stageLabel: 'Финал' }), 'Финал')
  assert.equal(matchCardStage({ thirdPlace: false, stageLabel: 'Финал' }), '')
  assert.notEqual(matchCardStage({ thirdPlace: true }), 'Финал')
  assert.equal(emptySideLabel({ kind: 'THIRD', slotIndex: 1, homeWaitingLabel: 'Победитель 1/2 №3' }, true, 'Финал'), 'Проигравший 1/2 №1')
  assert.equal(emptySideLabel({ kind: 'THIRD', slotIndex: 1 }, false, 'Финал'), 'Проигравший 1/2 №2')
  assert.equal(bronzeBoardColumns(3, false, false), '238px 30px 238px 30px 238px')
  assert.equal(bronzeMatchRows(4, 2), 4)
  assert.deepEqual(bronzeEarlyCell(3, 4, 4), { gridRow: '5 / span 1' })
  assert.deepEqual(bronzeSemiCell(0, 2, 4), { gridRow: '2 / span 2' })
  assert.deepEqual(bronzeSemiCell(1, 2, 4), { gridRow: '4 / span 1' })
  assert.equal(bronzeThirdRow(4), '5')
  assert.deepEqual(bronzeFinalCell(4), { gridRow: '2 / span 2' })
  assert.deepEqual(bronzeChampionCell(4), { gridRow: '4 / span 2' })
  assert.equal(gridRows(4, false), 'auto repeat(4, 142px)')
  const panels = bracketPanels([
    { round: 1, title: '1/4 финала', slots: [{ slotIndex: 0 }] },
    { round: 2, title: '1/2 финала', slots: [{ slotIndex: 0 }, { slotIndex: 1 }] },
    {
      round: 3,
      title: 'Финал',
      slots: [
        { slotIndex: 0, kind: 'MAIN', roundTitle: 'Финал' },
        { slotIndex: 1, kind: 'THIRD', roundTitle: 'Матч за 3-е место' },
      ],
    },
  ])
  assert.deepEqual(panels.map((panel) => panel.tab), ['1/4', '1/2', 'За 3-е', 'Финал'])
  assert.equal(panels[2].title, 'Матч за 3-е место')
  assert.equal(panels[2].thirdPlace, true)
  assert.equal(panels[3].champion, true)
  assert.equal(panels[3].slots.length, 1)
  assert.deepEqual(bracketPanels([
    { title: '1/2 финала', slots: [{}] },
    { title: 'Финал', slots: [{ kind: 'MAIN' }] },
  ]).map((panel) => panel.tab), ['1/2', 'Финал'])
})

test('champion card uses only a decided final and keeps every slot', () => {
  const rounds = [
    { title: '1/2 финала', slots: [{ id: 's1', slotIndex: 0 }, { id: 's2', slotIndex: 1 }] },
    {
      title: 'Финал',
      slots: [{
        id: 'final',
        slotIndex: 0,
        matchId: 'match-final',
        matchStatus: 'FINISHED',
        homeTeamId: 'home',
        homeTeamName: '  Факультет   журналистики  ',
        awayTeamId: 'away',
        awayTeamName: 'Экономика',
        homeScore: 0,
        awayScore: 0,
        penaltyHome: 5,
        penaltyAway: 3,
      }],
    },
  ]
  const slots = rounds.reduce((sum, round) => sum + (round.slots?.length ?? 0), 0)
  assert.equal(slots, 3)
  assert.equal(bracketPanels(rounds).length, 2)
  assert.deepEqual(championCard(rounds[1].slots[0]), {
    teamId: 'home',
    teamName: 'Факультет журналистики',
    matchId: 'match-final',
    score: '0 : 0',
    penalties: 'пен. 5 : 3',
  })
  assert.equal(championCard({ matchStatus: 'FINISHED', homeScore: 1, awayScore: 0, homeTeamId: 'a', homeTeamName: 'Альфа', awayTeamId: 'b', awayTeamName: 'Бета' })?.score, '1 : 0')
  assert.equal(championCard({ matchStatus: 'FINISHED', homeScore: 1, awayScore: 0, homeTeamId: 'a', homeTeamName: 'Альфа', awayTeamId: 'b', awayTeamName: 'Бета' })?.penalties, null)
  assert.equal(championCard({ matchStatus: 'FINISHED', homeScore: 1, awayScore: 1, homeTeamId: 'a', awayTeamId: 'b', homeTeamName: 'Альфа', awayTeamName: 'Бета' }), null)
  assert.equal(championCard({ matchStatus: 'SCHEDULED', homeScore: 0, awayScore: 0, homeTeamId: 'a', awayTeamId: 'b', homeTeamName: 'Альфа', awayTeamName: 'Бета' }), null)
  assert.equal(championCard({ matchStatus: 'LIVE', homeScore: 1, awayScore: 0, homeTeamId: 'a', awayTeamId: 'b', homeTeamName: 'Альфа', awayTeamName: 'Бета' }), null)
  assert.equal(championCard({ matchStatus: 'FINISHED', homeScore: 2, awayScore: 1, homeTeamId: 'a', awayTeamName: 'Бета' }), null)
  assert.equal(slots, 3)
})

test('a hidden bye does not leave a stub: the remaining card reaches the next match', () => {
  const single = bracketConnectorPaths(
    [{ slotIndex: 1, y: 18, kind: 'pend', id: 'm1' }],
    [{ slotIndex: 0, y: 12 }],
  )
  assert.deepEqual(single.map((seg) => seg.d), ['M0 18 H50 V12', 'M50 12 H100'])
  assert.deepEqual(single[0].ids, ['m1'])

  const pair = bracketConnectorPaths(
    [
      { slotIndex: 0, y: 20, kind: 'plain', id: 'a' },
      { slotIndex: 1, y: 40, kind: 'plain', id: 'b' },
    ],
    [{ slotIndex: 0, y: 30 }],
  )
  assert.deepEqual(pair.map((seg) => seg.d), ['M0 20 H50 V30', 'M0 40 H50 V30', 'M50 30 H100'])

  const zakhar = bracketConnectorPaths(
    [1, 3, 5, 7].map((slotIndex, index) => ({ slotIndex, y: 10 + index * 20, kind: 'pend', id: `m${index}` })),
    [0, 1, 2, 3].map((slotIndex) => ({ slotIndex, y: 15 + slotIndex * 20 })),
  )
  assert.equal(zakhar.length, 8)
  assert.deepEqual(
    zakhar.filter((_, index) => index % 2 === 0).map((seg) => seg.d),
    ['M0 10 H50 V15', 'M0 30 H50 V35', 'M0 50 H50 V55', 'M0 70 H50 V75'],
  )
  assert.ok(zakhar.every((seg, index) => index % 2 === 0 || seg.d.endsWith('H100')))
})
