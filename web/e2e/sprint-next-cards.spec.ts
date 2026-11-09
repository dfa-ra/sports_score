import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

function moscowToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

function plusDays(ymd: string, days: number) {
  const [year, month, day] = ymd.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day + days))
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(date.getUTCDate()).padStart(2, '0')
  return `${date.getUTCFullYear()}-${mm}-${dd}`
}

const today = moscowToday()
const mondayOffset = (new Date(`${today}T00:00:00Z`).getUTCDay() + 6) % 7
const weekMonday = plusDays(today, -mondayOffset)
const emptyDay = mondayOffset === 0 ? plusDays(weekMonday, 1) : weekMonday
const dotDay = mondayOffset === 6 ? plusDays(weekMonday, 5) : plusDays(weekMonday, 6)
const dayNumber = (ymd: string) => String(Number(ymd.slice(8)))
const cup = 'cup-1'
const liga = 'liga-1'
const autumn = 'autumn-1'
const outDir = '/opt/cursor/artifacts'

function at(day: string, hour: number, minute: number) {
  const utcHour = hour - 3
  return `${day}T${String(utcHour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00Z`
}

const teams = [
  ['home-j', 'Журналистика', 'ЖУ'],
  ['away-b', 'Биофак', 'БФ'],
  ['home-e', 'Экономика', 'ЭК'],
  ['away-p', 'Педагоги', 'ПЕ'],
  ['home-pu', 'Пумы', 'ПУ'],
  ['away-f', 'Физтех', 'ФИ'],
  ['home-m', 'Медики', 'МЕ'],
  ['away-a', 'Архитекторы', 'АР'],
  ['home-it', 'ИТ-кластер', 'ИК'],
  ['home-ma', 'Математики', 'МА'],
  ['away-h', 'Химики', 'ХИ'],
].map(([id, name, shortName]) => ({ id, name, shortName, logoUrl: null }))

function match(partial: Record<string, unknown>) {
  return {
    homeScore: 0,
    awayScore: 0,
    minute: null,
    period: null,
    stageLabel: null,
    venue: null,
    scheduledAt: at(today, 18, 0),
    startedAt: null,
    penaltyHome: null,
    penaltyAway: null,
    ...partial,
  }
}

const matches = [
  match({
    id: 'm-live',
    tournamentId: cup,
    tournamentName: 'Студ. кубок',
    status: 'LIVE',
    scheduledAt: at(today, 16, 0),
    minute: 34,
    homeTeamId: 'home-j',
    awayTeamId: 'away-b',
    homeTeamName: 'Журналистика',
    awayTeamName: 'Биофак',
    homeScore: 2,
    awayScore: 2,
    stageLabel: '1/4 финала',
  }),
  match({
    id: 'm-fin',
    tournamentId: cup,
    tournamentName: 'Студ. кубок',
    status: 'FINISHED',
    scheduledAt: at(today, 12, 30),
    homeTeamId: 'home-e',
    awayTeamId: 'away-p',
    homeTeamName: 'Экономика',
    awayTeamName: 'Педагоги',
    homeScore: 3,
    awayScore: 2,
    stageLabel: '1/4 финала',
  }),
  match({
    id: 'm-soon',
    tournamentId: liga,
    tournamentName: 'Лига ИТМО',
    status: 'SCHEDULED',
    scheduledAt: at(today, 18, 30),
    homeTeamId: 'home-pu',
    awayTeamId: 'away-f',
    homeTeamName: 'Пумы',
    awayTeamName: 'Физтех',
  }),
  match({
    id: 'm-pause',
    tournamentId: liga,
    tournamentName: 'Лига ИТМО',
    status: 'PAUSED',
    scheduledAt: at(today, 17, 0),
    minute: 19,
    homeTeamId: 'home-m',
    awayTeamId: 'away-a',
    homeTeamName: 'Медики',
    awayTeamName: 'Архитекторы',
    homeScore: 1,
    awayScore: 1,
  }),
  match({
    id: 'm-cancel',
    tournamentId: liga,
    tournamentName: 'Лига ИТМО',
    status: 'CANCELLED',
    scheduledAt: at(today, 20, 30),
    homeTeamId: 'home-it',
    awayTeamId: 'home-e',
    homeTeamName: 'ИТ-кластер',
    awayTeamName: 'Экономика',
    homeScore: 3,
    awayScore: 2,
  }),
  match({
    id: 'm-nil',
    tournamentId: liga,
    tournamentName: 'Лига ИТМО',
    status: 'FINISHED',
    scheduledAt: at(today, 10, 0),
    homeTeamId: 'home-pu',
    awayTeamId: 'away-f',
    homeTeamName: 'Пумы',
    awayTeamName: 'Физтех',
    homeScore: 0,
    awayScore: 0,
  }),
  match({
    id: 'm-autumn',
    tournamentId: autumn,
    tournamentName: 'Осенняя лига',
    status: 'FINISHED',
    scheduledAt: at(today, 11, 45),
    homeTeamId: 'home-ma',
    awayTeamId: 'away-h',
    homeTeamName: 'Математики',
    awayTeamName: 'Химики',
    homeScore: 1,
    awayScore: 4,
  }),
  match({
    id: 'm-dot',
    tournamentId: autumn,
    tournamentName: 'Осенняя лига',
    status: 'SCHEDULED',
    scheduledAt: at(dotDay, 15, 0),
    homeTeamId: 'home-ma',
    awayTeamId: 'away-h',
    homeTeamName: 'Математики',
    awayTeamName: 'Химики',
  }),
  match({
    id: 'm-winter',
    tournamentId: 'winter-1',
    tournamentName: 'Зимний кубок',
    status: 'SCHEDULED',
    scheduledAt: at(dotDay, 19, 0),
    homeTeamId: 'home-ma',
    awayTeamId: 'away-h',
    homeTeamName: 'Математики',
    awayTeamName: 'Химики',
  }),
  match({
    id: 'm-undated',
    tournamentId: liga,
    tournamentName: 'Лига ИТМО',
    status: 'SCHEDULED',
    scheduledAt: null,
    startedAt: at(today, 9, 0),
    homeTeamId: 'home-pu',
    awayTeamId: 'away-f',
    homeTeamName: 'Пумы',
    awayTeamName: 'Физтех',
  }),
  match({
    id: 'm-undated-2',
    tournamentId: cup,
    tournamentName: 'Студ. кубок',
    status: 'SCHEDULED',
    scheduledAt: null,
    homeTeamId: 'home-j',
    awayTeamId: 'away-b',
    homeTeamName: 'Журналистика',
    awayTeamName: 'Биофак',
  }),
  match({
    id: 'm-undated-3',
    tournamentId: autumn,
    tournamentName: 'Осенняя лига',
    status: 'SCHEDULED',
    scheduledAt: null,
    homeTeamId: 'away-h',
    awayTeamId: 'home-ma',
    homeTeamName: 'Химики',
    awayTeamName: 'Математики',
  }),
]

const tournaments = [
  { id: cup, name: 'Студ. кубок', status: 'ACTIVE', format: 'CUP', competitionStage: 'PLAYOFF', startDate: '2026-10-03', endDate: '2027-02-28' },
  { id: liga, name: 'Лига ИТМО', status: 'ACTIVE', format: 'GROUPS_PLAYOFF', competitionStage: 'GROUPS', startDate: '2026-10-03', endDate: '2027-02-28' },
  { id: autumn, name: 'Осенняя лига', status: 'ACTIVE', format: 'ROUND_ROBIN', competitionStage: 'GROUPS', startDate: '2026-10-03', endDate: '2026-12-20' },
]

function pageOf(rows: unknown[]) {
  return { content: rows, page: 0, size: 100, totalElements: rows.length, totalPages: rows.length ? 1 : 0 }
}

function filteredMatches(url: URL) {
  const status = url.searchParams.get('status')
  const from = url.searchParams.get('from')
  const to = url.searchParams.get('to')
  return matches.filter((row) => {
    if (status && row.status !== status) return false
    if (from && to) {
      if (!row.scheduledAt) return false
      const day = String(row.scheduledAt).slice(0, 10)
      if (day < from || day > to) return false
    }
    return true
  })
}

const standings = {
  tables: [
    {
      id: 'ga',
      name: 'Группа A',
      sortOrder: 1,
      rows: [
        { teamId: 'away-f', teamName: 'Физтех', played: 6, wins: 5, draws: 1, losses: 0, goalsFor: 21, goalsAgainst: 7, points: 16 },
        { teamId: 'home-pu', teamName: 'Пумы', played: 6, wins: 4, draws: 1, losses: 1, goalsFor: 17, goalsAgainst: 9, points: 13 },
        { teamId: 'home-it', teamName: 'ИТ-кластер', played: 6, wins: 3, draws: 1, losses: 2, goalsFor: 13, goalsAgainst: 12, points: 10 },
        { teamId: 'home-e', teamName: 'Экономика', played: 6, wins: 2, draws: 1, losses: 3, goalsFor: 10, goalsAgainst: 13, points: 7 },
        { teamId: 'away-a', teamName: 'Архитекторы', played: 6, wins: 1, draws: 1, losses: 4, goalsFor: 8, goalsAgainst: 15, points: 4 },
        { teamId: 'home-m', teamName: 'Медики', played: 6, wins: 0, draws: 1, losses: 5, goalsFor: 5, goalsAgainst: 18, points: 1 },
      ],
    },
    {
      id: 'gb',
      name: 'Группа B',
      sortOrder: 2,
      rows: [
        { teamId: 'home-j', teamName: 'Журналистика', played: 6, wins: 5, draws: 0, losses: 1, goalsFor: 17, goalsAgainst: 7, points: 15 },
        { teamId: 'away-b', teamName: 'Биофак', played: 6, wins: 4, draws: 0, losses: 2, goalsFor: 15, goalsAgainst: 9, points: 12 },
        { teamId: 'away-h', teamName: 'Химики', played: 6, wins: 0, draws: 2, losses: 4, goalsFor: 5, goalsAgainst: 9, points: 2 },
      ],
    },
  ],
}

function detail(id: string) {
  return matches.find((row) => row.id === id) || matches[2]
}

const cupFinal = [{
  round: 1,
  title: 'Финал',
  slots: [{
    id: 'slot-final',
    round: 1,
    slotIndex: 0,
    homeTeamId: 'home-j',
    homeTeamName: 'Журналистика',
    awayTeamId: 'away-b',
    awayTeamName: 'Биофак',
    matchStatus: 'SCHEDULED',
    scheduledAt: at(today, 16, 0),
    venue: 'Зал ИТМО',
  }],
}]

async function installApi(page: Page, options?: {
  matchesFail?: boolean
  statsFail?: boolean
  tableFail?: boolean
  incomplete?: boolean
  places?: number[]
  delayMatches?: boolean
  holdMatches?: { hold: boolean; waiters: Array<() => void> }
}) {
  const places = options?.places ?? [1, 2]
  await page.route('**/api/v1/**', async (route: Route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (options?.matchesFail && path.endsWith('/matches') && route.request().method() === 'GET') {
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'down' }) })
      return
    }
    if (path.endsWith('/teams') && !path.includes('/tournaments/')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageOf(teams)) })
      return
    }
    if (path.endsWith('/tournaments')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageOf(tournaments)) })
      return
    }
    if (path.endsWith('/matches') && !path.includes('/tournaments/') && route.request().method() === 'GET') {
      if (options?.holdMatches?.hold) {
        await new Promise<void>((resolve) => options.holdMatches!.waiters.push(resolve))
      }
      if (options?.delayMatches) await new Promise((resolve) => setTimeout(resolve, 1200))
      if (options?.incomplete) {
        const pageNo = Number(url.searchParams.get('page') || 0)
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: pageNo === 0 ? [matches[8]] : [{ ...matches[8], id: `extra-${pageNo}` }],
            page: pageNo,
            size: 100,
            totalElements: 4000,
            totalPages: 40,
          }),
        })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageOf(filteredMatches(url))) })
      return
    }
    const matchId = path.match(/\/matches\/([^/]+)(?:\/(.*))?$/)
    if (matchId) {
      const id = decodeURIComponent(matchId[1])
      const rest = matchId[2] || ''
      const row = detail(id)
      if (rest === 'events') {
        const events = id === 'm-fin' ? [
          { id: 'e1', eventType: 'GOAL', teamId: 'home-e', voided: false, period: 1, gameTime: 360, playerId: 'p1', playerFirstName: 'Артём', playerLastName: 'Белов', secondaryPlayerFirstName: 'Егор', secondaryPlayerLastName: 'Морозов' },
          { id: 'e2', eventType: 'GOAL', teamId: 'home-e', voided: false, period: 1, gameTime: 840, playerId: 'p2', playerFirstName: 'Егор', playerLastName: 'Морозов' },
          { id: 'e3', eventType: 'GOAL', teamId: 'away-p', voided: false, period: 2, gameTime: 1680, playerId: 'p3', playerFirstName: 'Павел', playerLastName: 'Орлов' },
        ] : []
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(events) })
        return
      }
      if (rest === 'referees') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
          { id: 'r1', refereeId: 'u1', firstName: 'Иван', lastName: 'Петров', email: 'hidden-ref@example.com' },
          { id: 'r2', refereeId: 'u2', firstName: 'Мария', lastName: 'Соколова', email: 'hidden-2@example.com' },
        ]) })
        return
      }
      if (rest === 'lineups') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
          home: { teamId: row.homeTeamId, teamName: row.homeTeamName, confirmed: false, starters: [], bench: [
            { playerId: 'p1', firstName: 'Артём', lastName: 'Белов', jerseyNumber: 9, starter: false },
          ] },
          away: { teamId: row.awayTeamId, teamName: row.awayTeamName, confirmed: false, starters: [], bench: [
            { playerId: 'p9', firstName: 'Илья', lastName: 'Орлов', jerseyNumber: 7, starter: false },
          ] },
        }) })
        return
      }
      if (rest === 'analyst-stats') {
        if (options?.statsFail) {
          await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'down' }) })
          return
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
          home: { shots: 0, shotsOnTarget: 0, saves: 0, corners: 0, fouls: 0, freeKicks: 0, kickIns: 0, woodwork: 0 },
          away: { shots: 0, shotsOnTarget: 0, saves: 0, corners: 0, fouls: 0, freeKicks: 0, kickIns: 0, woodwork: 0 },
          possessionTracked: false,
          homePossessionSeconds: 0,
          awayPossessionSeconds: 0,
          homePossessionPercent: null,
          awayPossessionPercent: null,
        }) })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        ...row,
        sportCode: 'FUTSAL',
        periodCount: 2,
        periodLengthSeconds: 1200,
        venue: id === 'm-soon' ? null : 'Зал ИТМО',
      }) })
      return
    }
    if (path.includes('/tournaments/') && path.endsWith('/overview')) {
      const cupGrid = path.includes(`/${cup}/`)
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        matchesCount: 99,
        teamsCount: cupGrid ? 2 : 12,
        format: cupGrid ? 'CUP' : 'GROUPS_PLAYOFF',
        stage: cupGrid ? 'PLAYOFF' : 'GROUPS',
        seasonYear: 2026,
        startDate: '2026-10-03',
        endDate: '2027-02-28',
        hasPlayoff: true,
      }) })
      return
    }
    if (path.includes('/tournaments/') && path.endsWith('/standings')) {
      if (options?.tableFail) {
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'down' }) })
        return
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(standings) })
      return
    }
    if (path.includes('/tournaments/') && path.endsWith('/playoff')) {
      const cupGrid = path.includes(`/${cup}/`)
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        competitionStage: cupGrid ? 'PLAYOFF' : 'GROUPS',
        advancingPlaces: places,
        rounds: cupGrid ? cupFinal : [],
      }) })
      return
    }
    if (path.endsWith('/home/overview')) {
      const asHome = (row: Record<string, unknown>) => ({
        ...row,
        tournamentShortName: row.tournamentName,
        scheduledAt: row.scheduledAt || at(today, 18, 0),
      })
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        live: matches.filter((row) => row.status === 'LIVE').map(asHome),
        upcoming: matches.filter((row) => row.status === 'SCHEDULED' && row.scheduledAt).map(asHome),
        recent: matches.filter((row) => row.status === 'FINISHED').map(asHome),
        next: asHome(matches.find((row) => row.id === 'm-soon')!),
        tournaments: tournaments.map((item) => ({
          id: item.id,
          name: item.name,
          shortName: item.name,
          status: item.status,
          format: item.format,
          competitionStage: item.competitionStage,
          teamsCount: item.id === liga ? 9 : 2,
          startDate: item.startDate,
          endDate: item.endDate,
        })),
        scorersByTournament: [],
        summary: { matchesPlayed: 36, goals: 140, goalsPerMatch: 3.9, teams: 11, players: 80 },
      }) })
      return
    }
    if (path.includes('/tournaments/') && path.endsWith('/teams')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
      return
    }
    if (path.includes('/tournaments/') && path.endsWith('/tables')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
      return
    }
    if (path.includes('/tournaments/') && path.endsWith('/matches')) {
      const upcoming = matches.filter((row) => row.tournamentId === liga)
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageOf(upcoming)) })
      return
    }
    if (path.includes('/statistics/summary')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        matchesPlayed: 36,
        goals: 140,
        goalsPerMatch: 3.9,
        teams: 12,
        players: 80,
      }) })
      return
    }
    if (path.includes('/statistics/board')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        scorers: [
          { playerId: 'p3', displayName: 'Илья Орлов', firstName: 'Илья', lastName: 'Орлов', goals: 9, teamId: 'away-f', avatarUrl: null },
          { playerId: 'p2', displayName: 'Егор Морозов', firstName: 'Егор', lastName: 'Морозов', goals: 7, teamId: 'home-pu', avatarUrl: null },
        ],
        assists: [],
        goalkeepers: [],
      }) })
      return
    }
    const tour = tournaments.find((item) => path.endsWith(`/tournaments/${item.id}`))
    if (tour) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(tour) })
      return
    }
    if (path.includes('/teams/') && path.endsWith('/form')) {
      const teamId = path.split('/').slice(-2, -1)[0]
      const rows = teamId === 'home-pu'
        ? [match({ id: 'f1', status: 'FINISHED', homeTeamId: 'home-pu', awayTeamId: 'away-f', homeScore: 2, awayScore: 1, scheduledAt: at('2026-10-01', 18, 0) })]
        : []
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(rows) })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [] }) })
  })
}

async function stamp(page: Page, side: 'left' | 'right' = 'right') {
  await page.evaluate((edge) => {
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = `position:fixed;${edge}:8px;bottom:8px;z-index:10000;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 Tektur,sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);`
    document.body.appendChild(note)
  }, side)
}

async function expectTabLabelsReadable(page: Page, selector: string, oneRow?: boolean) {
  const report = await page.locator(selector).evaluate((bar) => {
    const buttons = [...bar.querySelectorAll('button')].filter((node): node is HTMLElement => {
      if (!(node instanceof HTMLElement)) return false
      const style = getComputedStyle(node)
      return style.display !== 'none' && style.visibility !== 'hidden' && node.getClientRects().length > 0
    })
    const barBox = bar.getBoundingClientRect()
    const items = buttons.map((button) => {
      const box = button.getBoundingClientRect()
      return {
        text: (button.innerText || '').replace(/\s+/g, ' ').trim(),
        labelClipped: button.scrollWidth > button.clientWidth + 1,
        fullyVisible: box.width > 0
          && box.left >= barBox.left - 1
          && box.right <= barBox.right + 1
          && box.top >= barBox.top - 1
          && box.bottom <= barBox.bottom + 1,
        top: box.top,
      }
    })
    const tops = items.map((item) => item.top)
    return {
      pageOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      items,
      oneRow: tops.length === 0 || Math.max(...tops) - Math.min(...tops) < 4,
    }
  })
  expect(report.pageOverflow).toBe(false)
  expect(report.items.length).toBeGreaterThan(1)
  if (oneRow !== undefined) expect(report.oneRow).toBe(oneRow)
  for (const item of report.items) {
    expect(item.labelClipped, item.text).toBe(false)
    expect(item.fullyVisible, item.text).toBe(true)
    expect(item.text.length).toBeGreaterThan(1)
  }
}

test('calendar keeps one set, separates live and paused, and hides undated from the day', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/calendar')
  await expect(page.getByRole('heading', { name: /ноября/ })).toBeVisible()
  await expect(page.getByText('Осенняя лига').first()).toBeVisible()
  await expect(page.getByText('Студ. кубок').first()).toBeVisible()
  await expect(page.getByText('Лига ИТМО').first()).toBeVisible()
  await expect(page.getByText(/в игре/)).toBeVisible()
  await expect(page.getByText(/на паузе/)).toBeVisible()
  await expect(page.locator('body')).not.toContainText('1 live')
  await expect(page.locator('body')).not.toContainText(/Тур \d/)
  const body = await page.locator('body').innerText()
  expect(body).toContain('– : –')
  expect(body).toContain('0 : 0')
  expect(body).toContain('3 : 2')
  const cancelled = page.locator('.line', { hasText: 'ИТ-кластер' })
  await expect(cancelled).toContainText('Отменён')
  await expect(cancelled).toContainText('– : –')
  await expect(cancelled).not.toContainText('3 : 2')
  await page.getByRole('button', { name: 'Без даты' }).click()
  await expect(page.getByRole('heading', { name: 'Дата уточняется' })).toBeVisible()
  await expect(page.getByText('Журналистика').first()).toBeVisible()
  await expect(page.locator('.day')).toHaveCount(0)
})

test('empty day and local favorites stay separate from the full list', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/calendar')
  await expect(page.getByText('Осенняя лига').first()).toBeVisible()
  await page.locator('.day').filter({ has: page.locator('b', { hasText: new RegExp(`^${dayNumber(emptyDay)}$`) }) }).click()
  await expect(page.getByText('Матчей на этот день пока нет')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Перейти' })).toBeVisible()
  await page.locator('.day').filter({ has: page.locator('b', { hasText: new RegExp(`^${dayNumber(today)}$`) }) }).click()
  await page.locator('.line', { hasText: 'Журналистика' }).locator('.star').click()
  await page.getByRole('button', { name: /Избранн/ }).click()
  await expect(page.locator('.line', { hasText: 'Журналистика' })).toBeVisible()
  await expect(page.locator('.card-head', { hasText: 'Лига ИТМО' })).toHaveCount(0)
  await expect(page.locator('.card-head', { hasText: 'Осенняя лига' })).toHaveCount(0)
})

test('tournament chip keeps only that tournament on the day', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/calendar')
  await expect(page.getByText('Осенняя лига').first()).toBeVisible()
  const saturday = page.locator('.day').filter({ has: page.locator('b', { hasText: new RegExp(`^${dayNumber(dotDay)}$`) }) })
  await expect(saturday.locator('.dot')).toHaveCount(1)
  await page.getByRole('button', { name: 'Студ. кубок' }).click()
  await expect(page.locator('.card-head', { hasText: 'Студ. кубок' })).toBeVisible()
  await expect(page.locator('.card-head', { hasText: 'Осенняя лига' })).toHaveCount(0)
  await expect(page.locator('.card-head', { hasText: 'Лига ИТМО' })).toHaveCount(0)
  await expect(saturday.locator('.dot')).toHaveCount(0)
})

test('an empty tournament or favorites filter does not leave the hero loading', async ({ page }) => {
  const holdMatches = { hold: false, waiters: [] as Array<() => void> }
  await installApi(page, { holdMatches })
  await page.clock.install()
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/calendar')
  await expect(page.locator('.hero .meta span')).toContainText(/\d+ матч/)
  await expect(page.locator('.hero')).not.toContainText('Загрузка матчей…')
  holdMatches.hold = true
  await page.clock.fastForward(13_000)
  await expect.poll(() => holdMatches.waiters.length).toBeGreaterThan(0)
  await page.getByRole('button', { name: /Избр/ }).click()
  await expect(page.getByText('Избранных пока нет').first()).toBeVisible()
  await expect(page.locator('.skeleton')).toHaveCount(0)
  await expect(page.locator('.hero')).not.toContainText('Загрузка матчей…')
  await expect(page.locator('.hero .meta span')).toContainText('0 матчей')
  await page.getByRole('button', { name: 'Зимний кубок' }).click()
  await expect(page.getByText('Матчей на этот день пока нет')).toBeVisible()
  await expect(page.locator('.skeleton')).toHaveCount(0)
  await expect(page.locator('.hero')).not.toContainText('Загрузка матчей…')
  await expect(page.locator('.hero .meta span')).toContainText('0 матчей')
  holdMatches.hold = false
  for (const release of holdMatches.waiters.splice(0)) release()
})

test('375 calendar shows the same tournaments and no bottom nav', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/calendar')
  await expect(page.getByText('Осенняя лига').first()).toBeVisible()
  await expect(page.getByText('Студ. кубок').first()).toBeVisible()
  await expect(page.getByText('Лига ИТМО').first()).toBeVisible()
  await expect(page.locator('.menu-button')).toBeVisible()
  await expect(page.locator('.bottom-nav, nav.bottom')).toHaveCount(0)
  const scroll = await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)
  expect(scroll).toBe(true)
})

test('match card uses dashes, cumulative goals, and does not invent missing fields', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/matches/m-soon')
  await expect(page.getByText('– : –').first()).toBeVisible()
  await expect(page.getByText('Место уточняется').first()).toBeVisible()
  await expect(page.locator('body')).not.toContainText('0 : 0')
  await expect(page.locator('.form-row i')).toHaveCount(1)
  await page.goto('/matches/m-fin')
  await expect(page.getByText('3 : 2').first()).toBeVisible()
  await expect(page.getByText('Итог')).toBeVisible()
  await expect(page.getByText('1 : 0')).toBeVisible()
  await expect(page.getByText('2 : 0').first()).toBeVisible()
  await expect(page.locator('.form-row i')).toHaveCount(0)
  await expect(page.getByText('нет матчей').first()).toBeVisible()
  await expect(page.locator('body')).not.toContainText('Очные')
  await expect(page.locator('body')).not.toContainText('Секретар')
  await expect(page.locator('body')).not.toContainText('hidden-ref@')
  await expect(page.locator('body')).not.toContainText(/Тур \d/)
  await page.getByRole('button', { name: 'Составы' }).click()
  await expect(page.getByText('Состав не подтверждён').first()).toBeVisible()
  await expect(page.locator('body')).not.toContainText('Скрыт до старта')
  await page.getByRole('button', { name: 'Статистика' }).click()
  await expect(page.getByText('0 / 0').first()).toBeVisible()
  await expect(page.getByText(/нет данных/i)).toBeVisible()
  await expect(page.locator('body')).not.toContainText('Статистики пока нет')
})

test('analyst error is a retry, not an empty statistic', async ({ page }) => {
  await installApi(page, { statsFail: true })
  await page.goto('/matches/m-fin')
  await page.getByRole('button', { name: /^Стат/ }).click()
  await expect(page.getByText('Статистика не загрузилась')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible()
  await expect(page.locator('body')).not.toContainText('Статистики пока нет')
})

test('calendar error is separate from an empty day', async ({ page }) => {
  await installApi(page, { matchesFail: true })
  await page.goto('/calendar')
  await expect(page.getByText('Матчи не загрузились')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible()
  await expect(page.locator('body')).not.toContainText('Матчей на этот день пока нет')
})

test('table uses summary numbers, both groups, and only declared places', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 1100 })
  await page.goto('/tournaments/liga-1?tab=table')
  const figures = page.locator('.figures')
  await expect(figures).toContainText('36')
  await expect(figures).toContainText('140')
  await expect(figures).toContainText('3,9')
  await expect(figures).not.toContainText('99')
  await expect(page.getByText('Группа A')).toBeVisible()
  await expect(page.getByText('Группа B')).toBeVisible()
  await expect(page.getByText('Выходят в плей-офф')).toBeVisible()
  await expect(page.locator('.desk .rank.ice')).toHaveCount(4)
  await expect(page.locator('body')).not.toContainText('advancingPlaces')
  await expect(page.locator('body')).not.toContainText(/Тур \d/)
  await expect(page.getByText('Дата уточняется').first()).toBeVisible()
})

test('empty advancing places do not highlight ranks', async ({ page }) => {
  await installApi(page, { places: [] })
  await page.goto('/tournaments/liga-1?tab=table')
  await expect(page.getByText('Группа B')).toBeVisible()
  await expect(page.locator('.rank.ice')).toHaveCount(0)
  await expect(page.locator('body')).not.toContainText('Выходят в плей-офф')
})

test('table error has retry', async ({ page }) => {
  await installApi(page, { tableFail: true })
  await page.goto('/tournaments/liga-1?tab=table')
  await expect(page.getByText('Таблица не загрузилась')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible()
})

test('375 table shows both groups without horizontal scroll', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/tournaments/liga-1?tab=table')
  await expect(page.getByText('Группа A')).toBeVisible()
  await expect(page.getByText('Группа B')).toBeVisible()
  await expect(page.locator('.phone', { hasText: 'Химики' })).toBeVisible()
  const scroll = await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)
  expect(scroll).toBe(true)
})

test('calendar shows the loading line before the match list arrives', async ({ page }) => {
  await installApi(page, { delayMatches: true })
  await page.goto('/calendar')
  await expect(page.getByText('Загрузка матчей…').first()).toBeVisible()
  await expect(page.locator('.meta span')).toHaveCount(0)
  await expect(page.getByText('Осенняя лига').first()).toBeVisible()
  await expect(page.getByText(/в игре/)).toBeVisible()
})

test('live page counts only live matches and still lists a pause', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/live')
  await expect(page.getByRole('heading', { name: 'Сейчас' })).toBeVisible()
  await expect(page.getByText('Игры · Live')).toBeVisible()
  await expect(page.getByText(/1 в игре/)).toBeVisible()
  await expect(page.getByText(/1 на паузе/)).toBeVisible()
  await expect(page.locator('.line', { hasText: 'Журналистика' })).toBeVisible()
  await expect(page.locator('.line', { hasText: 'Медики' })).toContainText('Пауза')
  await expect(page.locator('.week')).toHaveCount(0)
  await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
})

test('table hub highlights only declared places', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/table?tournament=liga-1')
  await expect(page.getByText('Группа A')).toBeVisible()
  await expect(page.getByText('Группа B')).toBeVisible()
  await expect(page.locator('.rank.ice')).toHaveCount(4)
  await expect(page.locator('.rank.navy')).toHaveCount(0)
  await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
})

test('table hub with an empty place list has no zone colours', async ({ page }) => {
  mkdirSync(outDir, { recursive: true })
  await installApi(page, { places: [] })
  for (const [file, width, height] of [
    ['table-hub-1440-empty-places.png', 1440, 1100],
    ['table-hub-375-empty-places.png', 375, 1400],
  ] as const) {
    await page.setViewportSize({ width, height })
    await page.goto('/table?tournament=liga-1')
    await expect(page.getByText('Химики').first()).toBeVisible()
    await expect(page.locator('.rank.ice, .rank.navy')).toHaveCount(0)
    await stamp(page)
    await page.screenshot({ path: `${outDir}/${file}`, fullPage: true })
  }
})

test('home and the playoff grid keep a static header', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
  await page.goto('/tournaments/cup-1')
  await expect(page.getByText('Финал').first()).toBeVisible()
  await expect(page.getByText('Журналистика').first()).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
  await page.goto('/table?tournament=cup-1&tab=playoff')
  await expect(page.getByText('Финал').first()).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
})

test('375 table hub shows both groups without horizontal scroll', async ({ page }) => {
  await installApi(page)
  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/table?tournament=liga-1')
  await expect(page.getByText('Группа A')).toBeVisible()
  await expect(page.getByText('Группа B')).toBeVisible()
  await expect(page.locator('.rank.navy')).toHaveCount(0)
  const scroll = await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)
  expect(scroll).toBe(true)
})

test('incomplete calendar does not invent a total', async ({ page }) => {
  await installApi(page, { incomplete: true })
  await page.goto('/calendar')
  await expect(page.getByText('Показаны не все матчи').first()).toBeVisible()
  await expect(page.locator('.meta span')).toHaveCount(0)
})

test('narrow tabs stay readable and the header keeps room for login', async ({ page }) => {
  mkdirSync(outDir, { recursive: true })
  await installApi(page)

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/matches/m-fin')
  await expect(page.getByRole('button', { name: 'Протокол' })).toBeVisible()
  await expectTabLabelsReadable(page, '.match-tabs')
  await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/match-tabs-375.png` })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/matches/m-fin')
  await expect(page.getByRole('button', { name: 'Статистика' })).toBeVisible()
  await expectTabLabelsReadable(page, '.match-tabs', true)
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/match-tabs-1440.png` })

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/tournaments/liga-1')
  await expect(page.getByRole('tab', { name: /Команды/ })).toBeVisible()
  await expect(page.getByRole('tab', { name: 'Регламент' })).toBeVisible()
  await expectTabLabelsReadable(page, '.ttabs')
  const tournamentFooter = await page.locator('.foot-ver').evaluate((el) => {
    const box = el.getBoundingClientRect()
    return {
      text: (el.textContent || '').trim(),
      clipped: el.scrollWidth > el.clientWidth + 1,
      left: box.left,
      right: box.right,
      view: window.innerWidth,
    }
  })
  expect(tournamentFooter.clipped).toBe(false)
  expect(tournamentFooter.left).toBeGreaterThanOrEqual(0)
  expect(tournamentFooter.right).toBeLessThanOrEqual(tournamentFooter.view - 1)
  expect(tournamentFooter.text).toMatch(/^v\d/)
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/tournament-tabs-375.png` })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/tournaments/liga-1')
  await expect(page.getByRole('tab', { name: 'Регламент' })).toBeVisible()
  await expectTabLabelsReadable(page, '.ttabs', true)
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/tournament-tabs-1440.png` })

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/table?tournament=liga-1')
  await expect(page.getByRole('button', { name: 'Лидеры по передачам' })).toBeVisible()
  await expectTabLabelsReadable(page, '.league-tabs')
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/table-tabs-375.png` })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/table?tournament=liga-1')
  await expect(page.getByRole('button', { name: 'Лидеры по передачам' })).toBeVisible()
  await expectTabLabelsReadable(page, '.league-tabs', true)
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/table-tabs-1440.png` })

  await page.setViewportSize({ width: 375, height: 1400 })
  await page.goto('/')
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
  const header = await page.evaluate(() => {
    const login = document.querySelector('.login-pill')!.getBoundingClientRect()
    const burger = document.querySelector('.menu-button')!.getBoundingClientRect()
    const ver = document.querySelector('.foot-ver') as HTMLElement
    const box = ver.getBoundingClientRect()
    return {
      gap: burger.left - login.right,
      burgerRight: burger.right,
      loginLeft: login.left,
      view: window.innerWidth,
      verLeft: box.left,
      verRight: box.right,
      verText: (ver.textContent || '').trim(),
      verClipped: ver.scrollWidth > ver.clientWidth + 1,
    }
  })
  expect(header.gap).toBeGreaterThanOrEqual(18)
  expect(header.loginLeft).toBeGreaterThan(40)
  expect(header.burgerRight).toBeLessThanOrEqual(header.view - 4)
  expect(header.verClipped).toBe(false)
  expect(header.verLeft).toBeGreaterThanOrEqual(0)
  expect(header.verRight).toBeLessThanOrEqual(header.view - 4)
  expect(header.verText).toMatch(/^v\d/)
  await stamp(page, 'left')
  await page.screenshot({ path: `${outDir}/header-footer-375.png`, fullPage: true })
})

test('fixture screenshots of calendar, match and table', async ({ page }) => {
  mkdirSync(outDir, { recursive: true })
  await installApi(page)
  const shots: Array<[string, string, number, number, () => Promise<void>]> = [
    ['calendar-1440-cards.png', '/calendar', 1440, 1100, async () => {
      await expect(page.getByText('Осенняя лига').first()).toBeVisible()
    }],
    ['calendar-375-cards.png', '/calendar', 375, 1400, async () => {
      await expect(page.getByText('Осенняя лига').first()).toBeVisible()
    }],
    ['calendar-nodate-1440-cards.png', '/calendar', 1440, 1000, async () => {
      await page.getByRole('button', { name: 'Без даты' }).click()
      await expect(page.getByRole('heading', { name: 'Дата уточняется' })).toBeVisible()
    }],
    ['calendar-nodate-375-cards.png', '/calendar', 375, 1200, async () => {
      await page.getByRole('button', { name: 'Без даты' }).click()
      await expect(page.getByRole('heading', { name: 'Дата уточняется' })).toBeVisible()
    }],
    ['match-1440-cards.png', '/matches/m-fin', 1440, 1200, async () => {
      await expect(page.getByText('Итог')).toBeVisible()
    }],
    ['match-375-cards.png', '/matches/m-fin', 375, 1400, async () => {
      await expect(page.getByText('Итог')).toBeVisible()
    }],
    ['table-1440-cards.png', '/tournaments/liga-1?tab=table', 1440, 1400, async () => {
      await expect(page.getByText('Группа B')).toBeVisible()
    }],
    ['table-375-cards.png', '/tournaments/liga-1?tab=table', 375, 1600, async () => {
      await expect(page.getByText('Группа B')).toBeVisible()
    }],
    ['table-hub-1440-places.png', '/table?tournament=liga-1', 1440, 1200, async () => {
      await expect(page.locator('.rank.ice')).toHaveCount(4)
    }],
    ['table-hub-375-places.png', '/table?tournament=liga-1', 375, 1400, async () => {
      await expect(page.getByText('Группа B')).toBeVisible()
    }],
    ['home-1440-header.png', '/', 1440, 1100, async () => {
      await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
    }],
    ['home-375-header.png', '/', 375, 1200, async () => {
      await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
    }],
    ['playoff-1440-header.png', '/tournaments/cup-1', 1440, 1100, async () => {
      await expect(page.getByText('Финал').first()).toBeVisible()
      await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
    }],
    ['playoff-375-header.png', '/tournaments/cup-1', 375, 1200, async () => {
      await expect(page.getByText('Финал').first()).toBeVisible()
      await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
    }],
  ]
  for (const [file, url, width, height, ready] of shots) {
    await page.setViewportSize({ width, height })
    await page.goto(url)
    await ready()
    await stamp(page)
    await page.screenshot({ path: `${outDir}/${file}`, fullPage: true })
  }
})
