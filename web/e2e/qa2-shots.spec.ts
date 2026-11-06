import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const tournamentId = '00000000-0000-4000-8000-0000000000a2'
const teamId = '00000000-0000-4000-8000-0000000000d2'
const otherId = '00000000-0000-4000-8000-0000000000e2'
const matchId = '00000000-0000-4000-8000-0000000000c2'

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

function pair(round: number, index: number, home: string, away: string) {
  return {
    id: `s-${round}-${index}`,
    round,
    slotIndex: index,
    homeTeamId: `h-${round}-${index}`,
    homeTeamName: home,
    awayTeamId: `a-${round}-${index}`,
    awayTeamName: away,
    matchStatus: 'SCHEDULED',
    scheduledAt: '2026-11-20T16:00:00Z',
    venue: 'Зал',
  }
}

const rounds = [
  {
    round: 0,
    title: '1/8 финала',
    slots: Array.from({ length: 8 }, (_, index) => pair(0, index, `Хозяева ${index + 1}`, `Гости ${index + 1}`)),
  },
  {
    round: 1,
    title: '1/4 финала',
    slots: Array.from({ length: 4 }, (_, index) => pair(1, index, `Четверть ${index + 1}А`, `Четверть ${index + 1}Б`)),
  },
  { round: 2, title: '1/2 финала', slots: [pair(2, 0, 'Полуфинал А', 'Полуфинал Б'), pair(2, 1, 'Полуфинал В', 'Полуфинал Г')] },
  { round: 3, title: 'Финал', slots: [pair(3, 0, 'Финал дома', 'Финал в гостях')] },
]

async function shot(page: Page, name: string) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
}

test('qa2 screenshots', async ({ page }) => {
  test.setTimeout(60_000)
  mkdirSync(OUT, { recursive: true })
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/home/overview')) {
      return json(route, {
        generatedAt: '2026-11-06T12:00:00Z',
        next: {
          id: 'next-1',
          tournamentId,
          tournamentName: 'Кубок Толяна',
          tournamentShortName: 'Кубок',
          scheduledAt: '2099-06-01T16:30:00Z',
          status: 'SCHEDULED',
          homeTeamId: 'pumas',
          homeTeamName: 'Пумы',
          awayTeamId: 'phystech',
          awayTeamName: 'Физтех',
          homeScore: 0,
          awayScore: 0,
        },
        live: [],
        upcoming: [],
        recent: [],
        tournaments: [],
        scorersByTournament: [{
          tournamentId,
          tournamentName: 'Кубок Толяна',
          shortName: 'Кубок',
          scorers: [{
            playerId: 'p1',
            firstName: 'Толян',
            lastName: 'Игроков',
            displayName: 'Толян Игроков',
            goals: 7,
            assists: 2,
            appearances: 4,
            teamName: 'Пумы',
          }],
        }],
        summary: { matchesPlayed: 12, goals: 40, goalsPerMatch: 3.3, teams: 8, players: 90 },
      })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, { content: [{ id: tournamentId, name: 'Кубок Толяна', competitionStage: 'PLAYOFF' }] })
    }
    if (path === `/api/v1/tournaments/${tournamentId}`) {
      return json(route, {
        id: tournamentId,
        name: 'Кубок Толяна',
        status: 'ACTIVE',
        format: 'GROUPS_PLAYOFF',
        seasonYear: 2026,
        competitionStage: 'PLAYOFF',
      })
    }
    if (path.endsWith('/overview')) {
      return json(route, {
        name: 'Кубок Толяна',
        format: 'GROUPS_PLAYOFF',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: 2,
        matchesCount: 1,
        seasonYear: 2026,
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) {
      return json(route, [
        { id: 'e1', teamId, teamName: 'Пумы', status: 'APPROVED' },
        { id: 'e2', teamId: otherId, teamName: 'Физтех', status: 'APPROVED' },
      ])
    }
    if (path.endsWith('/playoff')) return json(route, { competitionStage: 'PLAYOFF', rounds })
    if (path.endsWith('/standings') || path.endsWith('/tables')) return json(route, [])
    if (path.includes('/statistics/summary')) return json(route, { matchesPlayed: 4, goals: 11, goalsPerMatch: 2.8 })
    if (path.includes('/statistics/board')) {
      return json(route, {
        scorers: [{
          playerId: 'p1',
          firstName: 'Толян',
          lastName: 'Игроков',
          displayName: 'Толян Игроков',
          goals: 7,
          appearances: 4,
          assists: 1,
        }],
        assists: [],
        goalkeepers: [],
      })
    }
    if (path === '/api/v1/teams') {
      return json(route, { content: [
        { id: teamId, name: 'QA Толян Альфа Очень Длинное Название', shortName: 'Альфа' },
        { id: otherId, name: 'QA Толян Бета Другое Длинное Название', shortName: 'Бета' },
      ] })
    }
    if (path === `/api/v1/teams/${teamId}`) {
      return json(route, { id: teamId, name: 'QA Толян Альфа Очень Длинное Название', shortName: 'Альфа' })
    }
    if (path === `/api/v1/teams/${teamId}/members` || path === `/api/v1/teams/${teamId}/player-stats`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}`) {
      return json(route, {
        id: matchId,
        tournamentId,
        homeTeamId: teamId,
        awayTeamId: otherId,
        homeTeamName: 'QA Толян Альфа',
        awayTeamName: 'QA Толян Бета',
        status: 'FINISHED',
        homeScore: 2,
        awayScore: 1,
        scheduledAt: '2026-11-01T12:00:00Z',
      })
    }
    if (path.endsWith('/form')) {
      const home = path.includes(teamId)
      return json(route, home ? [{
        id: 'f1', status: 'FINISHED', homeTeamId: teamId, awayTeamId: otherId, homeScore: 2, awayScore: 0,
      }] : [])
    }
    if (path === '/api/v1/matches') {
      return json(route, { content: [
        {
          id: 'soon-1',
          tournamentId,
          homeTeamId: teamId,
          awayTeamId: otherId,
          homeTeamName: 'QA Толян Альфа Очень Длинное Название',
          awayTeamName: 'QA Толян Бета Другое Длинное Название',
          status: 'SCHEDULED',
          homeScore: 0,
          awayScore: 0,
          scheduledAt: '2099-06-01T12:00:00Z',
        },
      ], page: 0, totalPages: 1 })
    }
    if (path.startsWith('/api/v1/matches/')) return json(route, [])
    if (path.includes('/players/') && path.endsWith('/card')) {
      return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
    }
    return json(route, { content: [], page: 0, totalPages: 1 })
  })

  for (const width of [375, 1440]) {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
    await page.goto('/')
    await expect(page.locator('.hero-card .team-name').first()).toHaveText('Пумы')
    const overlap = await page.evaluate(() => {
      const login = document.querySelector('.login-pill')?.getBoundingClientRect()
      const hero = document.querySelector('.hero-card')?.getBoundingClientRect()
      const header = getComputedStyle(document.querySelector('.chrome') as Element).position
      return {
        header,
        gap: hero && login ? hero.top - login.bottom : -1,
      }
    })
    expect(overlap.header).not.toBe('sticky')
    expect(overlap.header).not.toBe('fixed')
    expect(overlap.gap).toBeGreaterThan(0)
    await shot(page, `qa2-home-${width}`)

    await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
    if (width === 375) {
      await expect(page.getByRole('tabpanel', { name: /1\/8/ }).getByText('Четверть 1А')).toBeVisible()
    } else {
      await expect(page.getByText('Четверть 1А').first()).toBeVisible()
    }
    await shot(page, `qa2-playoff-${width}`)

    await page.goto(`/table?tournament=${tournamentId}&tab=scorers`)
    await expect(page.getByText('Игроков Толян')).toBeVisible()
    await shot(page, `qa2-table-scorers-${width}`)

    await page.goto(`/matches/${matchId}`)
    await expect(page.locator('.marks i')).toHaveText(['В'])
    await shot(page, `qa2-match-${width}`)
  }

  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/tournaments/${tournamentId}?tab=teams`)
  await expect(page.getByRole('tab', { name: /Команды/ })).toContainText('2')
  await expect(page.getByRole('link', { name: 'Пумы' })).toBeVisible()
  await shot(page, 'qa2-tournament-teams-375')

  await page.goto('/players/00000000-0000-0000-0000-000000000000')
  await expect(page.getByRole('heading', { name: 'Игрок не найден' })).toBeVisible()
  await shot(page, 'qa2-player-missing-375')

  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'Показать пароль' })).toBeVisible()
  await shot(page, 'qa2-login-375')
  await page.goto('/register')
  await shot(page, 'qa2-register-375')

  await page.goto(`/teams/${teamId}?tab=calendar`)
  await expect(page.getByText('Скоро')).toBeVisible()
  await shot(page, 'qa2-team-calendar-375')

  await page.goto('/calendar')
  await expect(page).toHaveURL(/date=/)
  await shot(page, 'qa2-calendar-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')
  await expect(page.getByRole('link', { name: 'Команды' })).toBeVisible()
  await shot(page, 'qa2-nav-teams-1440')
})
