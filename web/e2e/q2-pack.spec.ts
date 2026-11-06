import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

const tours = [
  { id: 'liga', name: 'Лига ИТМО по футзалу', status: 'ACTIVE', format: 'GROUPS_PLAYOFF', competitionStage: 'GROUPS', startDate: '2026-10-03', endDate: '2027-02-28', seasonYear: 2026, maxSquadSize: 12 },
  { id: 'cup', name: 'Студенческий кубок 2026', status: 'ACTIVE', format: 'CUP', competitionStage: 'PLAYOFF', startDate: '2026-10-20', endDate: '2026-12-01', maxSquadSize: 8 },
  { id: 'autumn', name: 'Осенняя лига факультетов', status: 'ACTIVE', format: 'ROUND_ROBIN', competitionStage: 'GROUPS', startDate: '2026-10-26', endDate: '2026-12-14', maxSquadSize: 15 },
  { id: 'signup', name: 'Суперкубок ИТМО', status: 'REGISTRATION', format: 'CUP', startDate: '2026-11-14', endDate: '2027-03-01', maxSquadSize: 99 },
  { id: 'old-cup', name: 'Студенческий кубок 2025', status: 'FINISHED', format: 'CUP', competitionStage: 'PLAYOFF', startDate: '2025-09-01', endDate: '2026-01-20', maxSquadSize: 10 },
  { id: 'old-rr', name: 'Круговая лига 2025', status: 'FINISHED', format: 'ROUND_ROBIN', startDate: '2025-02-01', endDate: '2025-05-20', maxSquadSize: 7 },
  { id: 'open-final', name: 'Кубок без финала', status: 'FINISHED', format: 'CUP', competitionStage: 'PLAYOFF', startDate: '2024-09-01', endDate: '2025-01-10', maxSquadSize: 6 },
  { id: 'draft', name: 'Черновик скрытый', status: 'DRAFT', format: 'CUP', startDate: '2026-12-01', maxSquadSize: 4 },
]

const homeTournaments = [
  { id: 'liga', teamsCount: 12, progress: 0.4, competitionStage: 'GROUPS', playoffRoundTitle: 'Групповой этап · тур 2 из 5' },
  { id: 'cup', teamsCount: 8, progress: 0.25, competitionStage: 'PLAYOFF', playoffRoundTitle: 'Тур 3' },
  { id: 'autumn', teamsCount: 6, progress: 0.5, competitionStage: 'GROUPS', playoffRoundTitle: null },
  { id: 'signup', teamsCount: 3, progress: null, competitionStage: 'GROUPS', playoffRoundTitle: null },
]

const teams = [
  { id: 'zh', name: 'Журналистика', logoUrl: '' },
  { id: 'ft', name: 'Физтех', logoUrl: '' },
  { id: 'pu', name: 'Пумы', logoUrl: '' },
]

function player(id: string, first: string, last: string, goals: number, assists: number, teamId: string) {
  return {
    playerId: id,
    id,
    firstName: first,
    lastName: last,
    goals,
    assists,
    appearances: goals + 1,
    teamId,
    teamLogoUrl: '',
    avatarUrl: null,
  }
}

const scorers = [
  player('p1', 'Илья', 'Орлов', 9, 2, 'ft'),
  player('p2', 'Максим', 'Волков', 8, 1, 'pu'),
  player('p3', 'Егор', 'Морозов', 7, 0, 'ft'),
  player('p4', 'Кирилл', 'Лебедев', 6, 3, 'pu'),
  player('p5', 'Павел', 'Орлов', 5, 1, 'ft'),
  player('p6', 'Артём', 'Белов', 5, 7, 'pu'),
  player('p7', 'Денис', 'Крылов', 4, 0, 'ft'),
  player('p8', 'Руслан', 'Смирнов', 4, 2, 'pu'),
  player('p9', 'Антон', 'Жуков', 3, 1, 'ft'),
  player('p10', 'Олег', 'Новиков', 2, 0, 'pu'),
  player('p11', 'Игорь', 'Соколов', 1, 0, 'ft'),
  player('p12', 'Глеб', 'Павлов', 1, 4, 'pu'),
]

const assists = [
  player('p6', 'Артём', 'Белов', 5, 7, 'pu'),
  ...scorers.filter((row) => row.playerId !== 'p6'),
]

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function finalSlot(status: string, home: string, away: string, homeScore: number | null, awayScore: number | null) {
  return {
    title: 'Финал',
    slots: [{
      matchStatus: status,
      homeTeamName: home,
      awayTeamName: away,
      homeTeamId: 'zh',
      awayTeamId: 'ft',
      homeScore,
      awayScore,
      kind: 'MAIN',
    }],
  }
}

async function stamp(page: Page) {
  await page.evaluate(() => {
    document.getElementById('fixture-stamp')?.remove()
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = 'margin:12px 8px 4px;display:inline-block;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);'
    document.body.appendChild(note)
  })
}

async function shot(page: Page, name: string) {
  mkdirSync(OUT, { recursive: true })
  await page.evaluate(() => document.fonts.ready)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
}

async function noHorizontalScroll(page: Page) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBe(metrics.clientWidth)
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('tournaments hybrid keeps hero totals, registration copy, and real winners', async ({ page }) => {
  let listMode: 'ready' | 'hold' | 'empty' | 'fail' | 'active' = 'ready'
  let releaseList: () => void = () => {}
  const hold = new Promise<void>((resolve) => {
    releaseList = resolve
  })
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/tournaments') {
      if (listMode === 'hold') await hold
      if (listMode === 'fail') return json(route, { message: 'down' }, 500)
      if (listMode === 'empty') return json(route, { content: [], totalElements: 0, totalPages: 0 })
      if (listMode === 'active') {
        const active = tours.filter((item) => item.status === 'ACTIVE')
        return json(route, { content: active, totalElements: active.length, totalPages: 1 })
      }
      return json(route, { content: tours, totalElements: tours.length, totalPages: 1 })
    }
    if (path === '/api/v1/home/overview') {
      return json(route, { tournaments: homeTournaments, live: [], upcoming: [], recent: [] })
    }
    if (path === '/api/v1/matches') {
      const status = url.searchParams.get('status')
      if (status === 'LIVE') {
        return json(route, {
          content: [{ id: 'live-1', status: 'LIVE', tournamentId: 'cup' }],
          totalPages: 1,
        })
      }
      return json(route, {
        content: [{ id: 'paused-1', status: 'PAUSED', tournamentId: 'liga' }],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/teams') return json(route, { content: teams, totalPages: 1 })
    if (path.endsWith('/overview')) {
      const id = path.split('/')[4]
      const home = homeTournaments.find((item) => item.id === id)
      return json(route, { teamsCount: home?.teamsCount ?? 4, stage: home?.competitionStage || 'GROUPS' })
    }
    if (path.endsWith('/tournaments/cup/playoff')) {
      return json(route, { rounds: [{ title: '1/4 финала', slots: [{ matchStatus: 'LIVE', homeTeamName: 'А', awayTeamName: 'Б' }] }] })
    }
    if (path.endsWith('/tournaments/old-cup/playoff')) {
      return json(route, { rounds: [finalSlot('FINISHED', 'Журналистика', 'Физтех', 2, 1)] })
    }
    if (path.endsWith('/tournaments/open-final/playoff')) {
      return json(route, { rounds: [finalSlot('SCHEDULED', 'Альфа', 'Бета', null, null)] })
    }
    if (path.endsWith('/tournaments/old-rr/standings')) {
      return json(route, { tables: [{ rows: [{ teamId: 'ft', teamName: 'Физтех' }, { teamId: 'pu', teamName: 'Пумы' }] }] })
    }
    if (path.endsWith('/playoff')) return json(route, { rounds: [] })
    if (path.endsWith('/standings')) return json(route, { tables: [] })
    return json(route, { content: [] })
  })

  listMode = 'hold'
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/tournaments')
  await expect(page.getByRole('heading', { name: 'Турниры' })).toBeVisible()
  await expect(page.locator('.figure')).toHaveCount(0)
  await expect(page.locator('.fig')).toHaveCount(3)
  await shot(page, 'q2-tournaments-loading-1440')
  listMode = 'ready'
  releaseList()

  await expect(page.getByRole('link', { name: /Лига ИТМО по футзалу/ })).toBeVisible()
  await expect(page.getByText('Черновик скрытый')).toHaveCount(0)
  await expect(page.getByText('Набор')).toHaveCount(0)
  await expect(page.getByText(/Тур \d/)).toHaveCount(0)
  await expect(page.getByText(/из \d/)).toHaveCount(0)
  await expect(page.getByText('Заявлено 3 команды')).toBeVisible()
  await expect(page.getByText('1 матч live')).toBeVisible()
  await expect(page.getByText('2 матча live')).toHaveCount(0)
  await expect(page.getByText('1/4 финала')).toBeVisible()
  await expect(page.getByText('Победитель · Журналистика')).toBeVisible()
  await expect(page.getByText('Победитель · Физтех')).toBeVisible()
  const openFinal = page.getByRole('link', { name: /Кубок без финала/ })
  await expect(openFinal).toBeVisible()
  await expect(openFinal).not.toContainText('Победитель')
  const signup = page.getByRole('link', { name: /Суперкубок ИТМО/ })
  await expect(signup).toContainText('Регистрация')
  await expect(signup.locator('.bar')).toHaveCount(0)
  await expect(page.locator('.figure').nth(0)).toContainText('3')
  await expect(page.locator('.figure').nth(1)).toContainText('1')
  await expect(page.locator('.figure').nth(2)).toContainText('7')
  const historyBefore = await page.evaluate(() => history.length)
  await page.getByRole('button', { name: 'Регистрация', exact: true }).click()
  await expect(page.getByRole('link', { name: /Лига ИТМО по футзалу/ })).toHaveCount(0)
  await expect(signup).toBeVisible()
  await expect(page.locator('.figure').nth(0)).toContainText('3')
  await expect(page.locator('.figure').nth(2)).toContainText('7')
  expect(await page.evaluate(() => history.length)).toBe(historyBefore)
  await shot(page, 'q2-tournaments-filter-1440')
  await page.getByRole('button', { name: 'Все', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Идут сейчас' })).toBeVisible()
  await shot(page, 'q2-tournaments-1440')

  await page.setViewportSize({ width: 375, height: 812 })
  await expect(page.getByText('Заявлено 3 команды')).toBeVisible()
  const chips = page.getByRole('group', { name: 'Фильтр турниров' }).getByRole('button')
  await expect(chips).toHaveCount(4)
  const boxes = await chips.evaluateAll((nodes) => nodes.map((node) => {
    const box = node.getBoundingClientRect()
    return { left: box.left, right: box.right, text: node.textContent || '' }
  }))
  for (const box of boxes) {
    expect(box.left, box.text).toBeGreaterThanOrEqual(-1)
    expect(box.right, box.text).toBeLessThanOrEqual(376)
  }
  const clipped = await chips.evaluateAll((nodes) => nodes
    .filter((node) => node.scrollWidth > node.clientWidth + 1)
    .map((node) => node.textContent || ''))
  expect(clipped).toEqual([])
  await noHorizontalScroll(page)
  await shot(page, 'q2-tournaments-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  listMode = 'active'
  await page.reload()
  await expect(page.getByRole('link', { name: /Осенняя лига факультетов/ })).toBeVisible()
  await page.getByRole('button', { name: 'Завершены', exact: true }).click()
  await expect(page.getByText('Турниров пока нет')).toBeVisible()
  await expect(page.getByText('В этой категории ничего нет')).toBeVisible()
  await expect(page.locator('.figure').nth(0)).toContainText('3')
  await expect(page.locator('.figure').nth(2)).toContainText('3')
  await shot(page, 'q2-tournaments-filter-empty-1440')
  await page.getByRole('button', { name: 'Показать все' }).click()
  await expect(page.getByRole('link', { name: /Осенняя лига факультетов/ })).toBeVisible()

  listMode = 'empty'
  await page.reload()
  await expect(page.getByText('Турниров пока нет')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Показать все' })).toHaveCount(0)
  await shot(page, 'q2-tournaments-empty-1440')

  listMode = 'fail'
  await page.reload()
  await expect(page.getByText('Турниры не загрузились')).toBeVisible()
  await shot(page, 'q2-tournaments-error-1440')
  listMode = 'ready'
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByRole('link', { name: /Лига ИТМО по футзалу/ })).toBeVisible()
  await expect(page.getByText('Турниры не загрузились')).toHaveCount(0)
})

test('players board, search highlight, and phone column', async ({ page }) => {
  const boardCalls: string[] = []
  let boardMode: 'ready' | 'hold' | 'fail' = 'ready'
  let searchMode: 'ready' | 'fail' = 'ready'
  let releaseBoard: () => void = () => {}
  const hold = new Promise<void>((resolve) => {
    releaseBoard = resolve
  })
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/statistics/board') {
      boardCalls.push(url.search)
      if (boardMode === 'hold') await hold
      if (boardMode === 'fail') return json(route, { message: 'down' }, 500)
      const limit = Number(url.searchParams.get('limit') || '10')
      return json(route, {
        scorers: scorers.slice(0, limit),
        assists: assists.slice(0, limit),
        goalkeepers: [{ playerId: 'gk', firstName: 'Сухой', lastName: 'Вратарей', cleanSheets: 6, teamId: 'ft' }],
      })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, {
        content: [
          { id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' },
          { id: 'cup', name: 'Очень длинный студенческий кубок факультетов', status: 'ACTIVE' },
          { id: 'autumn', name: 'Осенняя лига сразу нескольких кампусов', status: 'FINISHED' },
          { id: 'signup', name: 'Регистрация нового сезона первокурсников', status: 'REGISTRATION' },
          { id: 'old', name: 'Архивный турнир прошлых выпусков университета', status: 'FINISHED' },
          { id: 'draft', name: 'Скрытый черновик', status: 'DRAFT' },
        ],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/teams') return json(route, { content: teams, totalPages: 1 })
    if (path === '/api/v1/players') {
      if (searchMode === 'fail') return json(route, { message: 'down' }, 500)
      const q = url.searchParams.get('q') || ''
      if (q === 'Хрюков') return json(route, { content: [], totalPages: 0 })
      return json(route, {
        content: [
          { id: 'p2', firstName: 'Максим', lastName: 'Волков' },
          { id: 'p13', firstName: 'Анна', lastName: 'Волкова' },
        ],
      })
    }
    if (path === '/api/v1/players/p2/card') {
      return json(route, { team: { name: 'Пумы', logoUrl: '' }, statistics: { goals: 8 } })
    }
    if (path.endsWith('/card')) return json(route, { message: 'down' }, 500)
    return json(route, { content: [] })
  })

  boardMode = 'hold'
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/players')
  await expect(page.getByRole('heading', { name: 'Игроки' })).toBeVisible()
  await expect(page.getByLabel('Поиск игрока')).toBeVisible()
  await expect(page.getByText('Орлов')).toHaveCount(0)
  await shot(page, 'q2-players-loading-1440')
  boardMode = 'ready'
  releaseBoard()
  await expect(page.getByText('Бомбардир')).toBeVisible()
  await expect(page.getByText('Ассистент')).toBeVisible()
  await expect(page.getByText('Вратарь')).toHaveCount(0)
  await expect(page.getByText('Вратарей')).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Орлов Илья', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Показать ещё' })).toBeVisible()
  expect(boardCalls[0] || '').not.toContain('tournamentId')
  const before = await page.evaluate(() => history.length)
  await page.getByRole('button', { name: 'Лига ИТМО', exact: true }).click()
  await expect.poll(() => boardCalls.some((call) => call.includes('tournamentId=liga'))).toBe(true)
  expect(await page.evaluate(() => history.length)).toBe(before)
  await page.getByRole('button', { name: 'Все турниры' }).click()
  await expect(page.getByRole('link', { name: 'Орлов Илья', exact: true })).toBeVisible()
  await shot(page, 'q2-players-1440')

  await page.setViewportSize({ width: 375, height: 812 })
  const chipOverflow = await page.locator('.chips').first().evaluate((el) => el.scrollWidth > el.clientWidth + 1)
  if (chipOverflow) await expect(page.locator('.scroll-fade-plate').first()).toBeVisible()
  await expect(page.locator('.stat-m').first()).toBeHidden()
  const headerCells = await page.locator('.cols.head span').evaluateAll((nodes) => nodes
    .filter((node) => getComputedStyle(node).display !== 'none')
    .map((node) => (node.textContent || '').trim()))
  expect(headerCells).toEqual(['#', 'Игрок', 'Г'])
  await noHorizontalScroll(page)
  await shot(page, 'q2-players-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByLabel('Поиск игрока').fill('Волк')
  await expect(page.getByText('Найдено · 2')).toBeVisible()
  await expect(page.locator('mark', { hasText: 'Волк' }).first()).toBeVisible()
  await expect(page.getByText('Волкова Анна')).toBeVisible()
  await expect(page.getByText('–').first()).toBeVisible()
  await expect(page.getByText('Пумы').first()).toBeVisible()
  await shot(page, 'q2-players-search-1440')
  await page.getByLabel('Поиск игрока').fill('Хрюков')
  await expect(page.getByText('Игроков с таким именем нет')).toBeVisible()
  await expect(page.getByText('Проверьте написание')).toBeVisible()
  await shot(page, 'q2-players-search-empty-1440')

  searchMode = 'fail'
  await page.getByLabel('Поиск игрока').fill('Орлов')
  await expect(page.getByText('Игроки не загрузились')).toBeVisible()
  await shot(page, 'q2-players-error-1440')
  searchMode = 'ready'
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByText('Найдено · 2')).toBeVisible()

  boardMode = 'fail'
  await page.reload()
  await expect(page.getByText('Игроки не загрузились')).toBeVisible()
  await expect(page.getByLabel('Поиск игрока')).toBeVisible()
  await expect(page.getByText('Бомбардир')).toHaveCount(0)
})

test('login copy, field errors, and the phone plate', async ({ page }) => {
  let loginMode: 'ok' | 'pair' | 'down' | 'hold' = 'pair'
  let releaseLogin: () => void = () => {}
  const hold = new Promise<void>((resolve) => {
    releaseLogin = resolve
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/login' && route.request().method() === 'POST') {
      if (loginMode === 'hold') await hold
      if (loginMode === 'down') return json(route, { message: 'Неверный email или пароль' }, 500)
      if (loginMode === 'pair') return json(route, { message: 'Серверная формулировка' }, 401)
      return json(route, { accessToken: 'a', refreshToken: 'b' })
    }
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Вход' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Показать пароль' })).toBeVisible()
  await expect(page.getByText('болельщик')).toHaveCount(0)
  await expect(page.getByPlaceholder('name@niuitmo.ru')).toBeVisible()
  await expect(page.getByPlaceholder('Пароль')).toBeVisible()
  const emailBox = await page.getByPlaceholder('name@niuitmo.ru').boundingBox()
  const passwordBox = await page.getByPlaceholder('Пароль').boundingBox()
  expect(emailBox && passwordBox).toBeTruthy()
  expect(Math.abs(emailBox!.width - passwordBox!.width)).toBeLessThan(2)
  const logo = await page.locator('.promo-logo').boundingBox()
  const brand = await page.locator('.promo strong').boundingBox()
  expect(logo && brand).toBeTruthy()
  expect(logo!.y + logo!.height).toBeLessThanOrEqual(brand!.y + 4)
  await shot(page, 'q2-login-1440')

  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(page.getByText('Введите email', { exact: true })).toBeVisible()
  await expect(page.getByText('Введите пароль', { exact: true })).toBeVisible()
  await expect(page.locator('.form-error')).toHaveCount(0)
  await shot(page, 'q2-login-empty-1440')

  await page.getByPlaceholder('name@niuitmo.ru').fill('abc')
  await page.getByPlaceholder('Пароль').fill('secret')
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(page.getByText('Введите email полностью')).toBeVisible()

  await page.getByPlaceholder('name@niuitmo.ru').fill('name@niuitmo.ru')
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(page.getByText('Неверный email или пароль')).toBeVisible()
  await expect(page.getByText('Серверная формулировка')).toHaveCount(0)
  await expect(page.getByText('Не удалось войти. Повторите позже')).toHaveCount(0)
  await expect(page.locator('.field.invalid input').last()).toBeVisible()

  loginMode = 'down'
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(page.getByText('Не удалось войти. Повторите позже')).toBeVisible()
  await expect(page.getByText('Неверный email или пароль')).toHaveCount(0)
  const network = page.getByText('Не удалось войти. Повторите позже')
  const button = page.getByRole('button', { name: 'Войти', exact: true })
  const networkBox = await network.boundingBox()
  const buttonBox = await button.boundingBox()
  expect(networkBox && buttonBox).toBeTruthy()
  expect(networkBox!.y).toBeGreaterThan(buttonBox!.y)
  await shot(page, 'q2-login-network-1440')

  loginMode = 'hold'
  await page.getByRole('button', { name: 'Войти', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Входим…' })).toBeDisabled()
  await shot(page, 'q2-login-pending-1440')
  loginMode = 'pair'
  releaseLogin()
  await expect(page.getByRole('button', { name: 'Войти', exact: true })).toBeEnabled()

  await page.getByRole('button', { name: 'Войти через Google' }).click()
  await expect(page.getByText('Не удалось загрузить вход через Google.')).toBeVisible()
  await expect(page.locator('label.field', { hasText: 'Пароль' })).not.toContainText('Не удалось загрузить вход через Google.')

  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/login')
  const phoneLogo = await page.locator('.promo-logo').boundingBox()
  const phoneBrand = await page.locator('.promo strong').boundingBox()
  expect(phoneLogo && phoneBrand).toBeTruthy()
  expect(phoneLogo!.x + phoneLogo!.width).toBeLessThanOrEqual(phoneBrand!.x + 2)
  expect(Math.abs((phoneLogo!.y + phoneLogo!.height / 2) - (phoneBrand!.y + phoneBrand!.height / 2))).toBeLessThan(20)
  await noHorizontalScroll(page)
  await shot(page, 'q2-login-375')
})
