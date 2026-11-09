import { expect, test, type Page, type Route } from '@playwright/test'

const widths = [1440, 375, 320, 1440]

function tournament(index: number) {
  const name = `Кубок факультета программной инженерии ${index}`
  return {
    id: `tour-${index}`,
    name,
    shortName: name,
    status: 'ACTIVE',
    seasonYear: 2026,
    seasonLabel: 'Сезон 2026',
    format: 'CUP',
    formatTitle: 'Кубок',
    teamsCount: 8,
    competitionStage: 'GROUPS',
    stageLabel: 'Групповой этап',
    currentRound: 1,
    roundsTotal: 3,
    progress: 0.25,
    leader: null,
    playoffRoundTitle: null,
    startDate: '2026-09-01',
    endDate: '2026-12-20',
  }
}

const tournaments = Array.from({ length: 13 }, (_, index) => tournament(index + 1))
const scorers = tournaments.slice(0, 9).map((row) => ({
  tournamentId: row.id,
  tournamentName: row.name,
  shortName: row.shortName,
  scorers: [{
    playerId: 'p-1',
    displayName: 'Иван Иванов',
    firstName: 'Иван',
    lastName: 'Иванов',
    goals: 3,
    assists: 1,
    appearances: 2,
    teamId: 'team-1',
    teamName: 'Север',
    avatarUrl: null,
  }],
}))

const overview = {
  generatedAt: '2026-11-03T12:00:00Z',
  next: null,
  live: [],
  upcoming: [],
  recent: [],
  tournaments,
  scorersByTournament: scorers,
  summary: { matchesPlayed: 12, goals: 30, goalsPerMatch: 2.5, teams: 40, players: 180 },
}

const match = {
  id: 'm-1',
  tournamentId: 'tour-1',
  homeTeamId: 'team-1',
  awayTeamId: 'team-2',
  status: 'SCHEDULED',
  homeScore: 0,
  awayScore: 0,
  scheduledAt: '2099-06-01T12:00:00Z',
}

async function install(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('response', (response) => {
    if (response.status() === 404) errors.push(`404 ${response.url()}`)
  })
  await page.route('**/ws/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/info')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json;charset=UTF-8',
        body: JSON.stringify({ websocket: false, cookie_needed: false, origins: ['*:*'], entropy: 1 }),
      })
      return
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: 'o' })
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/refresh') || path.endsWith('/auth/me')) {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
      return
    }
    let body: unknown = {}
    if (path.endsWith('/home/overview')) body = overview
    else if (path === '/api/v1/tournaments') body = { content: tournaments, totalElements: tournaments.length, totalPages: 1 }
    else if (path === '/api/v1/tournaments/current') body = tournaments[0]
    else if (path === '/api/v1/tournaments/tour-1') body = tournaments[0]
    else if (path.endsWith('/standings')) body = { tables: [{ id: 'g', name: 'Группа A', rows: [{ teamId: 'team-1', teamName: 'Север', played: 1, wins: 1, draws: 0, losses: 0, goalsFor: 2, goalsAgainst: 0, points: 3 }] }] }
    else if (path.endsWith('/playoff')) body = { rounds: [] }
    else if (path.endsWith('/statistics/board')) body = { scorers: [], assists: [], goalkeepers: [] }
    else if (path === '/api/v1/teams') body = { content: [{ id: 'team-1', name: 'Север', shortName: 'СЕВ' }, { id: 'team-2', name: 'Юг', shortName: 'ЮГ' }], totalPages: 1 }
    else if (path === '/api/v1/teams/team-1') body = { id: 'team-1', name: 'Север', shortName: 'СЕВ' }
    else if (path.endsWith('/members') || path.endsWith('/player-stats') || path.endsWith('/form')) body = []
    else if (path === '/api/v1/matches/m-1') body = match
    else if (path === '/api/v1/matches') body = { content: [match], totalPages: 1 }
    else if (path.startsWith('/api/v1/matches/')) body = []
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  return errors
}

async function settle(page: Page) {
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve(null)))))
}

test('resizing home, table, team, and match does not overflow the stack', async ({ page }) => {
  const errors = await install(page)
  const steps: { path: string; ready: string }[] = [
    { path: '/', ready: '.tabs button' },
    { path: '/table?tournament=tour-1', ready: 'text=Группа A' },
    { path: '/teams/team-1', ready: 'text=Север' },
    { path: '/matches/m-1', ready: 'text=Север' },
  ]
  for (const step of steps) {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto(step.path, { waitUntil: 'load' })
    await expect(page.locator(step.ready).first()).toBeVisible()
    await expect(page.locator('.skeleton')).toHaveCount(0)
    for (let pass = 0; pass < 3; pass += 1) {
      for (const width of widths) {
        await page.setViewportSize({ width, height: 900 })
        await settle(page)
        await expect(page.locator(step.ready).first()).toBeVisible()
        await expect(page.locator('.skeleton')).toHaveCount(0)
      }
    }
  }
  expect(errors).toEqual([])
})
