import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const tournamentId = '00000000-0000-4000-8000-0000000000a1'
const OUT = '/opt/cursor/artifacts'

function match(partial: Record<string, unknown>) {
  return {
    id: 'm',
    tournamentId: 'liga',
    tournamentName: 'Лига ИТМО',
    tournamentShortName: 'Лига ИТМО',
    scheduledAt: '2026-11-06T16:30:00Z',
    status: 'SCHEDULED',
    homeTeamId: 'pumas',
    homeTeamName: 'Пумы',
    awayTeamId: 'phystech',
    awayTeamName: 'Физтех',
    homeScore: 0,
    awayScore: 0,
    minute: null,
    stageLabel: '1/4 финала',
    penaltyHome: null,
    penaltyAway: null,
    ...partial,
  }
}

const next = match({ id: 'next-1', scheduledAt: '2099-06-01T16:30:00Z' })
const live = match({
  id: 'live-1',
  status: 'LIVE',
  scheduledAt: '2026-11-06T12:00:00Z',
  homeTeamName: 'Физтех',
  awayTeamName: 'ИТ-кластер',
  homeTeamId: 'phystech',
  awayTeamId: 'it',
  homeScore: 2,
  awayScore: 1,
  minute: 34,
  gameTimeSeconds: 34 * 60,
  period: 2,
  periodCount: 2,
  periodLengthSeconds: 1200,
})
const upcoming = [
  next,
  match({
    id: 'up-2',
    tournamentId: 'cup',
    tournamentName: 'Студенческий кубок',
    tournamentShortName: 'Студ. кубок',
    scheduledAt: '2099-06-02T15:00:00Z',
    homeTeamName: 'Экономика',
    awayTeamName: 'Архитекторы',
    homeTeamId: 'econ',
    awayTeamId: 'arch',
  }),
  match({
    id: 'up-3',
    tournamentId: 'autumn',
    tournamentName: 'Осенняя лига',
    tournamentShortName: 'Осенняя',
    scheduledAt: '2099-06-03T16:00:00Z',
    homeTeamName: 'Педагоги',
    awayTeamName: 'Журналистика',
    homeTeamId: 'ped',
    awayTeamId: 'jour',
  }),
]
const recent = [
  match({
    id: 're-1',
    status: 'FINISHED',
    scheduledAt: '2026-11-03T09:30:00Z',
    finishedAt: '2026-11-03T10:30:00Z',
    tournamentId: 'autumn',
    tournamentName: 'Осенняя лига',
    tournamentShortName: 'Осенняя',
    homeTeamName: 'Экономика',
    awayTeamName: 'Педагоги',
    homeTeamId: 'econ',
    awayTeamId: 'ped',
    homeScore: 3,
    awayScore: 2,
    stageLabel: null,
  }),
  match({
    id: 're-2',
    status: 'FINISHED',
    scheduledAt: '2026-11-03T08:45:00Z',
    finishedAt: '2026-11-03T09:45:00Z',
    homeTeamName: 'Архитекторы',
    awayTeamName: 'Пумы',
    homeTeamId: 'arch',
    awayTeamId: 'pumas',
    homeScore: 1,
    awayScore: 4,
  }),
  match({
    id: 're-3',
    status: 'FINISHED',
    scheduledAt: '2026-11-02T16:00:00Z',
    finishedAt: '2026-11-02T17:00:00Z',
    tournamentId: 'cup',
    tournamentName: 'Студенческий кубок',
    tournamentShortName: 'Студ. кубок',
    homeTeamName: 'Журналистика',
    awayTeamName: 'Физтех',
    homeTeamId: 'jour',
    awayTeamId: 'phystech',
    homeScore: 2,
    awayScore: 2,
    penaltyHome: 4,
    penaltyAway: 3,
  }),
]

function tournament(partial: Record<string, unknown>) {
  return {
    id: 'liga',
    name: 'Лига ИТМО по футзалу',
    shortName: 'Лига ИТМО',
    status: 'ACTIVE',
    seasonYear: 2026,
    seasonLabel: 'Сезон 2026',
    format: 'GROUPS_PLAYOFF',
    formatTitle: 'Группы + плей-офф',
    teamsCount: 12,
    competitionStage: 'GROUPS',
    stageLabel: 'Групповой этап',
    progress: 0.55,
    leader: {
      teamId: 'phystech',
      teamName: 'Физтех',
      points: 16,
      played: 6,
    },
    lastMatch: recent[1],
    nextMatch: next,
    ...partial,
  }
}

const overview = {
  generatedAt: '2026-11-06T12:00:00Z',
  next,
  live: [live],
  upcoming,
  recent,
  tournaments: [
    tournament({}),
    tournament({
      id: 'cup',
      name: 'Студенческий кубок 2026',
      shortName: 'Студ. кубок',
      competitionStage: 'PLAYOFF',
      stageLabel: 'Плей-офф · 1/4 финала',
      playoffRoundTitle: '1/4 финала',
      playoffPlayedPairs: 2,
      playoffTotalPairs: 4,
      teamsCount: 8,
      progress: 0.7,
      leader: null,
      lastMatch: recent[2],
      nextMatch: upcoming[1],
    }),
    tournament({
      id: 'draft',
      name: 'Суперкубок ИТМО',
      shortName: 'Суперкубок',
      status: 'REGISTRATION',
      format: 'CUP',
      formatTitle: 'Кубок / плей-офф',
      teamsCount: 3,
      competitionStage: null,
      stageLabel: 'Регистрация',
      progress: null,
      leader: null,
      registrationClosesOn: '2026-11-10',
      lastMatch: null,
      nextMatch: null,
    }),
  ],
  scorersByTournament: [
    {
      tournamentId: 'liga',
      tournamentName: 'Лига ИТМО по футзалу',
      shortName: 'Лига ИТМО',
      scorers: [
        { playerId: 'p1', displayName: 'Илья Орлов', goals: 9, assists: 3, appearances: 4, teamName: 'Физтех' },
        { playerId: 'p2', displayName: 'Максим Волков', goals: 8, assists: 1, appearances: 4, teamName: 'ИТ-кластер' },
      ],
    },
  ],
  summary: { matchesPlayed: 87, goals: 412, goalsPerMatch: 4.7, teams: 24, players: 318 },
}

function slot(partial: Record<string, unknown>) {
  return {
    id: 'slot',
    round: 1,
    slotIndex: 0,
    roundTitle: '1/4 финала',
    matchId: 'match',
    homeTeamId: 'a',
    homeTeamName: 'Физтех',
    awayTeamId: 'b',
    awayTeamName: 'Педагоги',
    homeScore: null,
    awayScore: null,
    matchStatus: 'SCHEDULED',
    scheduledAt: '2026-11-05T16:00:00Z',
    venue: 'Зал ИТМО-1',
    ...partial,
  }
}

function eightTeamRounds() {
  const quarters = ['Физтех', 'Юристы', 'ИТ-кластер', 'Архитекторы'].map((home, index) => {
    const away = ['Педагоги', 'Медики', 'Экономика', 'Журналистика'][index]
    const homeScore = [3, 2, 1, 1][index]
    const awayScore = [1, 2, 0, 2][index]
    return slot({
      id: `qf-${index}`,
      slotIndex: index,
      matchId: `mq-${index}`,
      homeTeamId: `h-${index}`,
      homeTeamName: home,
      homeGroupName: index % 2 === 0 ? 'Группа A' : 'Группа B',
      homePlace: 1,
      awayTeamId: `a-${index}`,
      awayTeamName: away,
      awayGroupName: index % 2 === 0 ? 'Группа B' : 'Группа A',
      awayPlace: 4,
      homeScore,
      awayScore,
      penaltyHome: index === 1 ? 4 : null,
      penaltyAway: index === 1 ? 3 : null,
      matchStatus: 'FINISHED',
      scheduledAt: `2026-10-${28 + index}T15:00:00Z`,
    })
  })
  return [
    { round: 1, title: '1/4 финала', slots: quarters },
    {
      round: 2,
      title: '1/2 финала',
      slots: [
        slot({
          id: 'sf-0',
          round: 2,
          slotIndex: 0,
          roundTitle: '1/2 финала',
          matchId: 'ms-0',
          homeTeamName: 'Физтех',
          awayTeamName: 'Юристы',
          homeTeamId: 'h-0',
          awayTeamId: 'h-1',
          homeScore: 2,
          awayScore: 1,
          matchStatus: 'LIVE',
          minute: 24,
          scheduledAt: '2026-11-03T16:00:00Z',
          venue: 'Зал ИТМО-1',
        }),
        slot({
          id: 'sf-1',
          round: 2,
          slotIndex: 1,
          roundTitle: '1/2 финала',
          matchId: 'ms-1',
          homeTeamName: 'ИТ-кластер',
          awayTeamName: 'Журналистика',
          homeTeamId: 'h-2',
          awayTeamId: 'a-3',
          matchStatus: 'SCHEDULED',
          scheduledAt: '2026-11-05T16:00:00Z',
        }),
      ],
    },
    {
      round: 3,
      title: 'Финал',
      slots: [
        slot({
          id: 'final',
          round: 3,
          slotIndex: 0,
          roundTitle: 'Финал',
          matchId: null,
          homeTeamId: null,
          awayTeamId: null,
          homeTeamName: null,
          awayTeamName: null,
          homeWaitingLabel: 'Победитель 1/2 №1',
          awayWaitingLabel: 'Победитель 1/2 №2',
          matchStatus: 'SCHEDULED',
          scheduledAt: '2026-11-07T15:00:00Z',
          venue: null,
        }),
      ],
    },
  ]
}

function sixteenTeamRounds() {
  const titles = ['1/8 финала', '1/4 финала', '1/2 финала', 'Финал']
  return titles.map((title, index) => {
    const count = 2 ** (titles.length - index - 1)
    return {
      round: index + 1,
      title,
      slots: Array.from({ length: count }, (_, slotIndex) => slot({
        id: `s16-${index}-${slotIndex}`,
        round: index + 1,
        slotIndex,
        roundTitle: title,
        matchId: index === 0 ? `m16-${slotIndex}` : null,
        homeTeamId: index < 2 ? `h16-${index}-${slotIndex}` : null,
        awayTeamId: index < 2 ? `a16-${index}-${slotIndex}` : null,
        homeTeamName: index < 2 ? `Команда ${index + 1}.${slotIndex + 1}А` : null,
        awayTeamName: index < 2 ? `Команда ${index + 1}.${slotIndex + 1}Б` : null,
        homeWaitingLabel: index < 2 ? null : `Победитель ${titles[index - 1].replace(' финала', '')} №${slotIndex * 2 + 1}`,
        awayWaitingLabel: index < 2 ? null : `Победитель ${titles[index - 1].replace(' финала', '')} №${slotIndex * 2 + 2}`,
        homeScore: index === 0 ? 2 : null,
        awayScore: index === 0 ? 1 : null,
        matchStatus: index === 0 ? 'FINISHED' : 'SCHEDULED',
        scheduledAt: '2026-11-09T15:00:00Z',
      })),
    }
  })
}

async function fulfill(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockHome(page: Page, body: unknown, status = 200) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/home/overview')) return fulfill(route, body, status)
    if (path.endsWith('/tournaments')) return fulfill(route, { content: [], totalElements: 3 })
    return fulfill(route, { content: [] })
  })
}

async function mockTournament(page: Page, playoff: unknown, playoffStatus = 200) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    return mockTournamentBody(route, path, playoff, playoffStatus)
  })
}

async function noOverflow(page: Page) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBe(metrics.clientWidth)
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('home fixture matches the approved blocks on desktop and phone', async ({ page }) => {
  await mockHome(page, overview)
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 1400 : 1600 })
    await page.goto('/')
    await expect(page.locator('.eyebrow', { hasText: 'Ближайший матч' })).toBeVisible()
    await expect(page.getByText('Лига в цифрах')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Матчи' })).toBeVisible()
    await expect(page.getByText('Все турниры · ближайшие и последние')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Идут сейчас' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Бомбардиры по турнирам' })).toBeVisible()
    await expect(page.getByText('Live сейчас')).toBeVisible()
    await expect(page.getByRole('link', { name: 'К матчу' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Календарь игр' })).toBeVisible()
    await expect(page.getByText('Опционально')).toHaveCount(0)
    await expect(page.getByText('Тур 6')).toHaveCount(0)
    if (width === 1440) {
      const edges = await page.locator('.stbl').evaluate((table) => {
        const inkCenter = (el: Element) => {
          const range = document.createRange()
          range.selectNodeContents(el)
          const rect = range.getBoundingClientRect()
          return (rect.left + rect.right) / 2
        }
        const headers = [...table.querySelectorAll('th.num')]
        const rows = [...table.querySelectorAll('tbody tr')].map((row) => [...row.querySelectorAll('td.num')])
        const box = (el: Element) => {
          const style = getComputedStyle(el)
          return {
            center: inkCenter(el),
            width: el.getBoundingClientRect().width,
            tabular: style.fontVariantNumeric,
            margin: style.margin,
            padding: style.padding,
            align: style.textAlign,
          }
        }
        return {
          columns: headers.map((header, index) => ({
            header: box(header),
            cells: rows.map((row) => box(row[index])),
          })),
          player: getComputedStyle(table.querySelector('tbody td:nth-child(2)')!).textAlign,
          team: getComputedStyle(table.querySelector('tbody td.tm')!).textAlign,
        }
      })
      const widths = edges.columns.flatMap((column) => [column.header.width, ...column.cells.map((cell) => cell.width)])
      expect(Math.max(...widths) - Math.min(...widths)).toBeLessThanOrEqual(1)
      for (const column of edges.columns) {
        expect(column.header.tabular).toBe('tabular-nums')
        expect(column.header.align).toBe('center')
        for (const cell of column.cells) {
          expect(Math.abs(column.header.center - cell.center)).toBeLessThanOrEqual(2)
          expect(cell.tabular).toBe('tabular-nums')
          expect(cell.align).toBe('center')
        }
      }
      const goals = edges.columns[2]
      const assists = edges.columns[1].cells[0]
      expect(goals.header.padding).toBe(goals.cells[0].padding)
      expect(goals.cells[0].margin).toBe('0px')
      expect(goals.cells[0].padding).toBe(assists.padding)
      expect(goals.cells[0].width).toBe(assists.width)
      expect(edges.player).toBe('start')
      expect(edges.team).toBe('start')
      await page.locator('.block').filter({ has: page.getByRole('heading', { name: 'Бомбардиры по турнирам' }) })
        .screenshot({ path: `${OUT}/home-scorers-desktop-centered.png` })
    }
    if (width === 375) await noOverflow(page)
    await page.screenshot({ path: `${OUT}/home-a-${width === 1440 ? 'desktop' : 'mobile'}-fixture.png`, fullPage: true })
  }
})

test('home error hides the numbers and each block can be retried', async ({ page }) => {
  await mockHome(page, { message: 'down' }, 500)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await expect(page.getByText('Не удалось загрузить матчи')).toBeVisible()
  await expect(page.getByText('Матчи не загрузились')).toBeVisible()
  await expect(page.getByText('Турниры не загрузились')).toBeVisible()
  await expect(page.getByText('Статистика не загрузилась')).toBeVisible()
  await expect(page.getByText('Лига в цифрах')).toHaveCount(0)
  await expect(page.getByText('Матчей пока нет')).toHaveCount(0)
  await expect(page.getByText('Голов пока нет')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Повторить' })).toHaveCount(4)
  await page.screenshot({ path: `${OUT}/home-a-error-fixture.png`, fullPage: true })
})

test('home keeps skeleton height until the overview arrives', async ({ page }) => {
  let release: () => void = () => {}
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/home/overview')) {
      await gate
      return fulfill(route, overview)
    }
    return fulfill(route, { content: [], totalElements: 0 })
  })
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  const hero = page.locator('.sk-hero')
  await expect(hero).toBeVisible()
  const height = await hero.evaluate((el) => Math.round(el.getBoundingClientRect().height))
  expect(height).toBe(253)
  await page.screenshot({ path: `${OUT}/home-a-loading-desktop-fixture.png` })
  await page.setViewportSize({ width: 375, height: 900 })
  const phone = await page.locator('.sk-hero').evaluate((el) => Math.round(el.getBoundingClientRect().height))
  expect(phone).toBe(446)
  release()
  await expect(page.locator('.eyebrow', { hasText: 'Ближайший матч' })).toBeVisible()
})

test('an empty upcoming column keeps its caption', async ({ page }) => {
  await mockHome(page, { ...overview, upcoming: [], next: null, live: [] })
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await expect(page.getByText('Нет ближайших игр')).toBeVisible()
  await expect(page.getByText('Результатов пока нет')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Лига ИТМО', exact: true })).toBeVisible()
})

test('playoff variant A keeps the title, states, phone width and the 16-team board', async ({ page }) => {
  await mockTournament(page, { competitionStage: 'PLAYOFF', rounds: eightTeamRounds() })
  await page.setViewportSize({ width: 1440, height: 1100 })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Студенческий кубок 2026' })).toBeVisible()
  await expect(page.getByText('Идёт', { exact: true })).toBeVisible()
  await expect(page.getByText('Сезон 2026')).toBeVisible()
  await expect(page.getByText('По пен. 4:3')).toBeVisible()
  await expect(page.getByText('Победитель 1/2 №1')).toBeVisible()
  await expect(page.getByText('Время московское (МСК)')).toBeVisible()
  await page.screenshot({ path: `${OUT}/playoff-a-desktop-fixture.png`, fullPage: true })

  await page.setViewportSize({ width: 375, height: 1400 })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('tab', { name: /1\/2/ })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByText('← свайп между раундами → · время МСК')).toBeVisible()
  await expect(page.getByText('Время московское (МСК) · наведите')).toHaveCount(0)
  await noOverflow(page)
  await page.screenshot({ path: `${OUT}/playoff-a-mobile-fixture.png`, fullPage: true })

  await page.setViewportSize({ width: 1440, height: 1400 })
  await page.unroute('**/api/v1/**')
  await mockTournament(page, { competitionStage: 'PLAYOFF', rounds: sixteenTeamRounds() })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: '1/8 финала' }).first()).toBeVisible()
  await expect(page.locator('.po-grid')).toBeVisible()
  await expect(page.getByRole('tablist', { name: 'Раунды' })).toHaveCount(0)
  const headers = page.locator('.po-grid h3.rh')
  await expect(headers).toHaveCount(5)
  await page.screenshot({ path: `${OUT}/playoff-a-16-desktop-fixture.png`, fullPage: true })
})

function tournamentShell(route: Route, path: string) {
  if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) return fulfill(route, {})
  return mockTournamentBody(route, path, { competitionStage: 'PLAYOFF', rounds: [] })
}

function mockTournamentBody(route: Route, path: string, playoff: unknown, playoffStatus = 200) {
  if (path === '/api/v1/teams') return fulfill(route, { content: [] })
  if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
    return fulfill(route, {
      name: 'Студенческий кубок 2026',
      format: 'GROUPS_PLAYOFF',
      stage: 'PLAYOFF',
      hasPlayoff: true,
      teamsCount: 8,
      matchesCount: 18,
      seasonYear: 2026,
      startDate: '2026-08-01',
      endDate: '2026-12-15',
      playoffLive: true,
    })
  }
  if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) return fulfill(route, playoff, playoffStatus)
  if (path.endsWith(`/tournaments/${tournamentId}/teams`)) return fulfill(route, [])
  if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return fulfill(route, [])
  if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return fulfill(route, { content: [] })
  if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return fulfill(route, [])
  if (path.endsWith(`/tournaments/${tournamentId}`)) {
    return fulfill(route, {
      id: tournamentId,
      name: 'Студенческий кубок 2026',
      status: 'ACTIVE',
      format: 'GROUPS_PLAYOFF',
      competitionStage: 'PLAYOFF',
      seasonYear: 2026,
      startDate: '2026-08-01',
      endDate: '2026-12-15',
      regulations: 'Играем по правилам мини-футбола.',
    })
  }
  return fulfill(route, {})
}

test('playoff loading shows skeletons and keeps the card title', async ({ page }) => {
  let release: () => void = () => {}
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/playoff')) {
      await gate
      return fulfill(route, { competitionStage: 'PLAYOFF', rounds: eightTeamRounds() })
    }
    return tournamentShell(route, path)
  })
  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('status', { name: 'Сетка загружается' })).toBeVisible()
  await expect(page.locator('.sk-stack .sk')).toHaveCount(2)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await noOverflow(page)
  await page.screenshot({ path: `${OUT}/playoff-a-loading-mobile-fixture.png` })
  release()
  await expect(page.getByRole('tab', { name: /1\/4/ })).toBeVisible()
})

test('playoff error can be retried and an empty bracket offers the table', async ({ page }) => {
  let playoffMode: 'error' | 'empty' = 'error'
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/playoff')) {
      if (playoffMode === 'error') return fulfill(route, { message: 'down' }, 500)
      return fulfill(route, { competitionStage: 'PLAYOFF', rounds: [] })
    }
    return tournamentShell(route, path)
  })
  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByText('Не удалось загрузить сетку')).toBeVisible()
  await expect(page.getByText('Проверьте соединение и попробуйте ещё раз.')).toBeVisible()
  await expect(page.getByRole('tab', { name: 'Таблица' })).toBeEnabled()
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await page.screenshot({ path: `${OUT}/playoff-a-error-mobile-fixture.png` })

  playoffMode = 'empty'
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByText('Сетка ещё не сформирована')).toBeVisible()
  await expect(page.getByText('Плей-офф начнётся после группового этапа. Следите за таблицей.')).toBeVisible()
  await page.screenshot({ path: `${OUT}/playoff-a-empty-mobile-fixture.png` })
  await page.getByRole('button', { name: 'К таблице' }).click()
  await expect(page.getByRole('tab', { name: 'Таблица' })).toHaveAttribute('aria-selected', 'true')
})
