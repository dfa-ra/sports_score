import { expect, test, type Page, type Route } from '@playwright/test'

const tournamentA = '00000000-0000-4000-8000-0000000000a2'
const tournamentB = '00000000-0000-4000-8000-0000000000b2'
const matchId = '00000000-0000-4000-8000-0000000000c2'
const teamId = '00000000-0000-4000-8000-0000000000d2'
const otherTeamId = '00000000-0000-4000-8000-0000000000e2'
const playerId = '00000000-0000-0000-0000-000000000000'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function quietApi(page: Page) {
  await page.route('**/api/v1/**', (route) => json(route, {}))
}

test('home shows retry when the overview never answers', async ({ page }) => {
  test.setTimeout(40_000)
  await quietApi(page)
  await page.route('**/api/v1/home/overview', () => {})
  await page.goto('/')
  const hero = page.getByRole('alert').filter({ hasText: 'Не удалось загрузить матчи' })
  await expect(hero.getByRole('button', { name: 'Повторить' })).toBeVisible({ timeout: 20_000 })
})

test('table shows retry when the tournament list never answers', async ({ page }) => {
  test.setTimeout(40_000)
  await quietApi(page)
  await page.route('**/api/v1/teams**', (route) => json(route, { content: [] }))
  await page.route('**/api/v1/tournaments**', () => {})
  await page.goto('/table')
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible({ timeout: 20_000 })
})

test('a match shows retry when the match never answers', async ({ page }) => {
  test.setTimeout(40_000)
  await quietApi(page)
  await page.route('**/api/v1/teams**', (route) => json(route, { content: [] }))
  await page.route(`**/api/v1/matches/${matchId}**`, () => {})
  await page.goto(`/matches/${matchId}`)
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible({ timeout: 20_000 })
})

test('scorers show retry when the board never answers', async ({ page }) => {
  test.setTimeout(40_000)
  await quietApi(page)
  await page.route('**/api/v1/teams**', (route) => json(route, { content: [] }))
  await page.route('**/api/v1/tournaments**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/tournaments')) {
      return json(route, { content: [{ id: tournamentA, name: 'Кубок А', competitionStage: 'GROUPS' }] })
    }
    if (path.endsWith(`/tournaments/${tournamentA}`)) {
      return json(route, { id: tournamentA, name: 'Кубок А', status: 'ACTIVE', format: 'ROUND_ROBIN' })
    }
    if (path.endsWith('/standings')) return json(route, [])
    if (path.endsWith('/playoff')) return json(route, { rounds: [] })
    return json(route, {})
  })
  await page.route('**/api/v1/matches**', (route) => json(route, { content: [] }))
  await page.route('**/api/v1/statistics/board**', () => {})
  await page.goto(`/table?tournament=${tournamentA}&tab=scorers`)
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible({ timeout: 20_000 })
})

test('an unknown player is a not-found page with a back link', async ({ page }) => {
  await quietApi(page)
  await page.route(`**/api/v1/players/${playerId}/card`, (route) => json(route, { message: 'missing' }, 404))
  await page.goto(`/players/${playerId}`)
  await expect(page.getByRole('heading', { name: 'Игрок не найден' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Назад' })).toBeVisible()
  await expect(page).toHaveTitle('Не найдено — Лига ИТМО')
  await page.goto('/qwerty')
  await expect(page.getByRole('heading', { name: 'Не найдено' })).toBeVisible()
  await expect(page).toHaveTitle('Не найдено — Лига ИТМО')
})

test('match form letters stay the ones the endpoint returned', async ({ page }) => {
  await quietApi(page)
  const match = {
    id: matchId,
    tournamentId: tournamentA,
    homeTeamId: teamId,
    awayTeamId: otherTeamId,
    homeTeamName: 'Альфа',
    awayTeamName: 'Бета',
    status: 'FINISHED',
    homeScore: 1,
    awayScore: 0,
    scheduledAt: '2026-11-01T12:00:00Z',
  }
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') {
      return json(route, { content: [
        { id: teamId, name: 'Альфа', shortName: 'Альфа' },
        { id: otherTeamId, name: 'Бета', shortName: 'Бета' },
      ] })
    }
    if (path === `/api/v1/matches/${matchId}`) return json(route, match)
    if (path === `/api/v1/matches/${matchId}/events`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/referees`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/lineups`) return json(route, {})
    if (path.endsWith('/form')) {
      const home = path.includes(teamId)
      return json(route, home
        ? [{ id: 'f1', status: 'FINISHED', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 2, awayScore: 0 }]
        : [])
    }
    return json(route, {})
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/matches/${matchId}`)
  await expect(page.locator('.marks i')).toHaveText(['В'])
  await page.reload()
  await expect(page.locator('.marks i')).toHaveText(['В'])
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.reload()
  await expect(page.locator('.marks i')).toHaveText(['В'])
  await expect(page.getByText('нет матчей')).toHaveCount(1)
})

test('the tournament name stays in the title on every tab', async ({ page }) => {
  await quietApi(page)
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === `/api/v1/tournaments/${tournamentA}`) {
      return json(route, { id: tournamentA, name: 'Кубок Толяна', status: 'ACTIVE', format: 'ROUND_ROBIN', seasonYear: 2026 })
    }
    if (path.endsWith('/overview')) {
      return json(route, { name: 'Кубок Толяна', format: 'ROUND_ROBIN', teamsCount: 0, matchesCount: 0, seasonYear: 2026 })
    }
    if (path.endsWith('/teams')) return json(route, [])
    if (path.endsWith('/tables')) return json(route, [])
    if (path.endsWith('/standings')) return json(route, [])
    if (path.endsWith('/playoff')) return json(route, { rounds: [] })
    if (path.endsWith('/matches')) return json(route, { content: [], totalPages: 1, number: 0 })
    if (path.includes('/statistics/')) return json(route, { scorers: [], matchesPlayed: 0, goals: 0, goalsPerMatch: 0 })
    return json(route, {})
  })
  await page.goto(`/tournaments/${tournamentA}?tab=teams`)
  await expect(page.getByRole('heading', { name: 'Кубок Толяна' })).toBeVisible()
  await expect(page).toHaveTitle('Кубок Толяна — Лига ИТМО')
  await page.getByRole('tab', { name: 'Регламент' }).click()
  await expect(page).toHaveURL(new RegExp(`tab=rules`))
  await expect(page).toHaveTitle('Кубок Толяна — Лига ИТМО')
})

test('the players tab asks for the selected tournament', async ({ page }) => {
  await quietApi(page)
  const seen: string[] = []
  await page.route('**/api/v1/**', (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/statistics/board') {
      seen.push(url.searchParams.get('tournamentId') || '')
      const id = url.searchParams.get('tournamentId')
      const row = id === tournamentB
        ? { playerId: id, firstName: 'Бэ', lastName: 'Игроков', displayName: 'Бэ Игроков', goals: 4, assists: 0 }
        : { playerId: id, firstName: 'Аа', lastName: 'Другой', displayName: 'Аа Другой', goals: 1, assists: 0 }
      return json(route, { scorers: [row], assists: [] })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, { content: [
        { id: tournamentA, name: 'Турнир А', competitionStage: 'GROUPS' },
        { id: tournamentB, name: 'Турнир Б', competitionStage: 'GROUPS' },
      ] })
    }
    if (path === '/api/v1/tournaments/current') return json(route, { id: tournamentA, name: 'Турнир А' })
    if (path === `/api/v1/tournaments/${tournamentA}` || path === `/api/v1/tournaments/${tournamentB}`) {
      const id = path.endsWith(tournamentB) ? tournamentB : tournamentA
      return json(route, { id, name: id === tournamentB ? 'Турнир Б' : 'Турнир А', status: 'ACTIVE' })
    }
    if (path.endsWith('/standings') || path.endsWith('/playoff')) return json(route, path.endsWith('/playoff') ? { rounds: [] } : [])
    if (path === '/api/v1/teams') return json(route, { content: [] })
    if (path === '/api/v1/matches') return json(route, { content: [], page: 0, totalPages: 1 })
    return json(route, {})
  })
  await page.goto(`/table?tab=players&tournament=${tournamentB}`)
  await expect(page.getByText('Игроков Бэ')).toBeVisible()
  expect(seen).toContain(tournamentB)
  expect(seen.every((id) => id === tournamentB)).toBe(true)
})

test('calendar date and tournament stay after reload without a new history entry', async ({ page }) => {
  await quietApi(page)
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/tournaments') {
      return json(route, { content: [{ id: tournamentA, name: 'Кубок фильтра' }] })
    }
    if (path === '/api/v1/teams') return json(route, { content: [] })
    if (path === '/api/v1/matches' && new URL(route.request().url()).searchParams.get('status') === 'LIVE') {
      return json(route, {
        content: [{
          id: 'live-filter',
          tournamentId: tournamentA,
          tournamentName: 'Кубок фильтра',
          status: 'LIVE',
          homeTeamName: 'Альфа',
          awayTeamName: 'Бета',
          homeScore: 1,
          awayScore: 0,
          scheduledAt: '2026-11-06T12:00:00Z',
        }],
        page: 0,
        totalPages: 1,
      })
    }
    return json(route, { content: [], page: 0, totalPages: 1 })
  })
  await page.goto('/calendar')
  await expect(page).toHaveURL(/date=\d{4}-\d{2}-\d{2}/)
  const before = await page.evaluate(() => history.length)
  await page.getByRole('button', { name: 'Следующая неделя' }).click()
  await expect(page).toHaveURL(/date=\d{4}-\d{2}-\d{2}/)
  expect(await page.evaluate(() => history.length)).toBe(before)
  const url = page.url()
  await page.reload()
  await expect(page).toHaveURL(url)
  await page.goto('/live')
  await expect(page.getByRole('button', { name: 'Кубок фильтра' })).toBeVisible()
  const liveBefore = await page.evaluate(() => history.length)
  await page.getByRole('button', { name: 'Кубок фильтра' }).click()
  await expect(page).toHaveURL(new RegExp(`tournament=${tournamentA}`))
  expect(await page.evaluate(() => history.length)).toBe(liveBefore)
  await page.reload()
  await expect(page).toHaveURL(new RegExp(`tournament=${tournamentA}`))
})

test('match and team tabs replace history and stay after reload', async ({ page }) => {
  await quietApi(page)
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') {
      return json(route, { content: [{ id: teamId, name: 'QA Толян Альфа', shortName: 'Альфа' }] })
    }
    if (path === `/api/v1/teams/${teamId}`) return json(route, { id: teamId, name: 'QA Толян Альфа', shortName: 'Альфа' })
    if (path === `/api/v1/teams/${teamId}/members`) return json(route, [])
    if (path === `/api/v1/teams/${teamId}/player-stats`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}`) {
      return json(route, {
        id: matchId,
        homeTeamId: teamId,
        awayTeamId: otherTeamId,
        status: 'SCHEDULED',
        homeScore: 0,
        awayScore: 0,
        scheduledAt: '2026-11-08T12:00:00Z',
      })
    }
    if (path === '/api/v1/matches') return json(route, { content: [] })
    if (path.startsWith('/api/v1/matches/')) return json(route, [])
    return json(route, {})
  })
  await page.goto('/calendar')
  await page.goto(`/matches/${matchId}`)
  const before = await page.evaluate(() => history.length)
  await page.getByRole('button', { name: 'Составы' }).click()
  await expect(page).toHaveURL(/tab=lineups/)
  expect(await page.evaluate(() => history.length)).toBe(before)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Составы' })).toHaveClass(/on/)
  await page.goBack()
  await expect(page).toHaveURL(/\/calendar/)

  await page.goto(`/teams/${teamId}`)
  const teamBefore = await page.evaluate(() => history.length)
  await page.getByRole('button', { name: 'Состав' }).click()
  await expect(page).toHaveURL(/tab=squad/)
  expect(await page.evaluate(() => history.length)).toBe(teamBefore)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Состав' })).toHaveClass(/on/)
  await page.goBack()
  await expect(page).not.toHaveURL(new RegExp(`/teams/${teamId}`))
})

test('an unplayed team match is a dash and Скоро, a finished 0:0 stays', async ({ page }) => {
  await quietApi(page)
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') {
      return json(route, { content: [
        { id: teamId, name: 'QA Толян Альфа Длинное Имя', shortName: 'Альфа' },
        { id: otherTeamId, name: 'QA Толян Бета Длинное Имя', shortName: 'Бета' },
      ] })
    }
    if (path === `/api/v1/teams/${teamId}`) {
      return json(route, { id: teamId, name: 'QA Толян Альфа Длинное Имя', shortName: 'Альфа' })
    }
    if (path === `/api/v1/teams/${teamId}/members` || path === `/api/v1/teams/${teamId}/player-stats`) return json(route, [])
    if (path === '/api/v1/matches') {
      return json(route, { content: [
        {
          id: 'soon-1',
          homeTeamId: teamId,
          awayTeamId: otherTeamId,
          status: 'SCHEDULED',
          homeScore: 0,
          awayScore: 0,
          scheduledAt: '2099-06-01T12:00:00Z',
        },
        {
          id: 'nil-1',
          homeTeamId: teamId,
          awayTeamId: otherTeamId,
          status: 'FINISHED',
          homeScore: 0,
          awayScore: 0,
          scheduledAt: '2026-10-01T12:00:00Z',
        },
      ] })
    }
    return json(route, {})
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/teams/${teamId}?tab=calendar`)
  await expect(page.locator('.nums').first()).toContainText('–')
  await expect(page.getByText('Скоро')).toBeVisible()
  await page.getByRole('button', { name: 'Результаты' }).click()
  await expect(page.locator('.nums').first()).toContainText('0')
  await expect(page.getByText('Скоро')).toHaveCount(0)
})
