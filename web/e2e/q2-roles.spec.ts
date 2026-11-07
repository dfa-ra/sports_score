import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

function token() {
  const exp = Math.floor(Date.now() / 1000) + 60 * 60
  const payload = Buffer.from(JSON.stringify({ exp })).toString('base64url')
  return `e30.${payload}.sig`
}

function user(roles: string[]) {
  return {
    id: 'u1',
    email: 'ilya@example.com',
    role: roles[0],
    enabled: true,
    firstName: 'Илья',
    lastName: 'Орлов',
    roles: roles.map((role) => ({ role, status: 'APPROVED' })),
  }
}

const profile = {
  id: 'p1',
  firstName: 'Илья',
  lastName: 'Орлов',
  displayName: 'Орлов',
  avatarUrl: '',
  jerseyNumber: 9,
  position: 'Нападающий',
  bio: '',
}

const history = [
  row('h1', 'FINISHED', '2026-11-05T15:00:00Z', 'Физтех', 'Факультет технологического менеджмента', 2, 1, 'WIN'),
  row('h2', 'FINISHED', '2026-11-01T15:00:00Z', 'Архитекторы', 'Физтех', 1, 1, 'DRAW'),
  row('h3', 'FINISHED', '2026-10-28T15:00:00Z', 'Физтех', 'Медики', 0, 1, 'LOSS'),
  row('h4', 'FINISHED', '2026-10-24T15:00:00Z', 'Юристы', 'Физтех', 0, 2, 'WIN'),
  row('h5', 'FINISHED', '2026-10-20T15:00:00Z', 'Физтех', 'Химики', 3, 3, 'DRAW'),
  row('h6', 'SCHEDULED', '2026-12-02T15:00:00Z', 'Физтех', 'Экономика', 0, 0, null),
]

function row(
  matchId: string,
  status: string,
  scheduledAt: string,
  homeTeamName: string,
  awayTeamName: string,
  homeScore: number,
  awayScore: number,
  outcome: string | null,
) {
  return {
    matchId,
    scheduledAt,
    startedAt: null,
    status,
    homeTeamName,
    awayTeamName,
    home: homeTeamName === 'Физтех',
    homeScore,
    awayScore,
    outcome,
    goals: 0,
    assists: 0,
    yellowCards: 0,
    redCards: 0,
  }
}

const team = { id: 'ft', name: 'Физтех', shortName: 'ФТ', logoUrl: '', captainId: 'p1' }
const members = [
  { id: 'm1', playerId: 'p1', playerFirstName: 'Илья', playerLastName: 'Орлов' },
  { id: 'm2', playerId: 'p2', playerFirstName: 'Максим', playerLastName: 'Волков' },
  { id: 'm3', playerId: 'p3', playerFirstName: 'Егор', playerLastName: 'Морозов' },
  { id: 'm4', playerId: 'p4', playerFirstName: 'Кирилл', playerLastName: 'Оченьдлиннаяфамилия' },
]
const calendar = [
  fixtureMatch('c1', 'SCHEDULED', '2026-12-10T15:00:00Z', 'ft', 'ek', 'Физтех', 'Экономика', 0, 0),
  fixtureMatch('c2', 'FINISHED', '2026-11-05T15:00:00Z', 'ft', 'med', 'Физтех', 'Медики', 2, 1),
  fixtureMatch('c3', 'FINISHED', '2026-11-01T15:00:00Z', 'ar', 'ft', 'Архитекторы', 'Физтех', 1, 1, 4, 3),
  fixtureMatch('c4', 'FINISHED', '2026-10-20T15:00:00Z', 'ft', 'yu', 'Физтех', 'Юристы', 0, 0),
  fixtureMatch('c5', 'SCHEDULED', null, 'ft', 'him', 'Физтех', 'Химики', 0, 0),
  fixtureMatch('c6', 'FINISHED', '2026-10-10T15:00:00Z', 'ft', 'long', 'Физтех', 'Факультет технологического менеджмента и инноваций', 1, 0),
]

function fixtureMatch(
  id: string,
  status: string,
  scheduledAt: string | null,
  homeTeamId: string,
  awayTeamId: string,
  homeTeamName: string,
  awayTeamName: string,
  homeScore: number,
  awayScore: number,
  penaltyHome: number | null = null,
  penaltyAway: number | null = null,
) {
  return {
    id,
    status,
    scheduledAt,
    startedAt: null,
    homeTeamId,
    awayTeamId,
    homeTeamName,
    awayTeamName,
    homeScore,
    awayScore,
    penaltyHome,
    penaltyAway,
    thirdPlace: false,
  }
}

const going = {
  going: [{ playerId: 'p1', status: 'GOING', playerFirstName: 'Илья', playerLastName: 'Орлов' }],
  notGoing: [{ playerId: 'p2', status: 'NOT_GOING', playerFirstName: 'Максим', playerLastName: 'Волков' }],
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
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

async function expectPenaltyUnderScore(page: Page) {
  const row = page.locator('.cal', { hasText: 'пен. 4 : 3' })
  const main = row.locator('.score .main')
  const pens = row.locator('.score .pens')
  await expect(main).toHaveText('1 : 1')
  await expect(pens).toHaveText('пен. 4 : 3')
  const metrics = await row.evaluate((node) => {
    const score = node.querySelector('.score .main') as HTMLElement
    const caption = node.querySelector('.score .pens') as HTMLElement
    const scoreBox = score.getBoundingClientRect()
    const captionBox = caption.getBoundingClientRect()
    return {
      stacked: captionBox.top >= scoreBox.bottom - 1,
      scoreSize: Number.parseFloat(getComputedStyle(score).fontSize),
      captionSize: Number.parseFloat(getComputedStyle(caption).fontSize),
    }
  })
  expect(metrics.stacked).toBe(true)
  expect(metrics.captionSize).toBeLessThan(metrics.scoreSize)
}

async function noHorizontalScroll(page: Page) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBe(metrics.clientWidth)
}

async function signIn(page: Page, roles: string[]) {
  const access = token()
  const account = user(roles)
  await page.addInitScript(({ access, account }) => {
    localStorage.setItem('sl_access', access)
    localStorage.setItem('sl_user', JSON.stringify(account))
  }, { access, account })
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('fan profile has no player stats, player profile keeps the full history', async ({ page }) => {
  let cardMode: 'ok' | 'fail' = 'ok'
  let saveMode: 'ok' | 'fail' = 'ok'
  await signIn(page, ['FAN'])
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me') return json(route, user(['FAN']))
    if (path === '/api/v1/players/me') return json(route, { message: 'нет профиля' }, 404)
    if (path === '/api/v1/teams') return json(route, { content: [team] })
    if (path === '/api/v1/matches') return json(route, { content: [] })
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: 'Орлов Илья' })).toBeVisible()
  await expect(page.getByText('Зритель', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Стать игроком' })).toBeVisible()
  await expect(page.getByText('Избранных команд пока нет')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Игры' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Цифры' })).toHaveCount(0)
  await expect(page.getByText('Голы')).toHaveCount(0)
  await shot(page, 'q2r-profile-fan-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2r-profile-fan-375')

  await page.unroute('**/api/v1/**')
  await signIn(page, ['PLAYER', 'REFEREE'])
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    if (path === '/api/v1/auth/me') return json(route, user(['PLAYER', 'REFEREE']))
    if (path === '/api/v1/players/me' && method === 'PUT') {
      if (saveMode === 'fail') return json(route, { message: 'Профиль не сохранился.' }, 500)
      return json(route, profile)
    }
    if (path === '/api/v1/players/me') return json(route, profile)
    if (path === '/api/v1/players/p1/card') {
      if (cardMode === 'fail') return json(route, { message: 'down' }, 500)
      return json(route, {
        ...profile,
        team: { id: 'ft', name: 'Физтех', shortName: 'ФТ', logoUrl: '', captain: false },
        captainTeams: [],
        statistics: { appearances: 6, goals: 3, assists: 1, yellowCards: 0, redCards: 0 },
        matchHistory: history,
      })
    }
    if (path === '/api/v1/teams') return json(route, { content: [team] })
    if (path === '/api/v1/matches') return json(route, { content: [] })
    return json(route, { content: [] })
  })

  cardMode = 'fail'
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/profile')
  await expect(page.getByText('Не удалось загрузить профиль')).toBeVisible()
  await expect(page.getByText('Голы')).toHaveCount(0)
  await shot(page, 'q2r-profile-error-375')
  cardMode = 'ok'
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByRole('link', { name: /Факультет технологического менеджмента/ })).toHaveCount(1)
  await expect(page.locator('.game')).toHaveCount(6)
  await expect(page.getByText('– : –')).toBeVisible()

  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(page.getByRole('link', { name: 'Моя команда' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Пульт судьи' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Админка' })).toHaveCount(0)
  const jump = await page.locator('[data-section="jump"]').boundingBox()
  const games = await page.locator('[data-section="history"]').boundingBox()
  expect(jump && games && jump.x < games.x).toBeTruthy()
  await shot(page, 'q2r-profile-player-1440')

  await page.setViewportSize({ width: 375, height: 812 })
  const order = await page.locator('[data-section]').evaluateAll((nodes) =>
    nodes
      .map((node) => ({
        name: node.getAttribute('data-section'),
        top: node.getBoundingClientRect().top + window.scrollY,
      }))
      .sort((a, b) => a.top - b.top)
      .map((item) => item.name),
  )
  expect(order.indexOf('jump')).toBeLessThan(order.indexOf('history'))
  expect(order.indexOf('history')).toBeLessThan(order.indexOf('teams'))
  expect(order.indexOf('teams')).toBeLessThan(order.indexOf('account'))
  await noHorizontalScroll(page)
  await shot(page, 'q2r-profile-player-375')

  for (const width of [719, 1000]) {
    await page.setViewportSize({ width, height: 900 })
    await noHorizontalScroll(page)
  }

  await page.getByRole('button', { name: 'Редактировать' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Имя').fill('')
  await dialog.getByRole('button', { name: 'Сохранить' }).click()
  await expect(dialog.getByText('Укажите имя.')).toBeVisible()
  await dialog.getByLabel('Имя').fill('Илья')
  await dialog.getByLabel('Как писать на майке').fill('ОРЁЛ')
  saveMode = 'fail'
  await dialog.getByRole('button', { name: 'Сохранить' }).click()
  await expect(dialog.getByText('Профиль не сохранился.')).toBeVisible()
  await expect(dialog.getByLabel('Как писать на майке')).toHaveValue('ОРЁЛ')
  await dialog.getByRole('button', { name: 'Закрыть' }).click()
  await expect(dialog).toHaveCount(0)
})

test('captain manages the team and a player only reads it', async ({ page }) => {
  let rsvpMode: 'ok' | 'hold' | 'fail' = 'ok'
  let releaseRsvp: () => void = () => {}
  let rsvpPosts = 0
  let addPosts = 0
  let deleteMode: 'ok' | 'fail' = 'fail'
  let saveMode: 'ok' | 'fail' = 'fail'
  const pages: number[] = []
  await signIn(page, ['PLAYER', 'CAPTAIN'])
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    if (path === '/api/v1/auth/me') return json(route, user(['PLAYER', 'CAPTAIN']))
    if (path === '/api/v1/players/me') return json(route, profile)
    if (path === '/api/v1/teams/mine') return json(route, team)
    if (path === '/api/v1/teams/ft/members' && method === 'POST') {
      addPosts += 1
      return json(route, members[1], 201)
    }
    if (path.startsWith('/api/v1/teams/ft/members/') && method === 'DELETE') {
      if (deleteMode === 'fail') return json(route, { message: 'Не удалось убрать игрока' }, 500)
      return json(route, {}, 204)
    }
    if (path === '/api/v1/teams/ft/members') return json(route, members)
    if (path === '/api/v1/teams/ft/matches') {
      const pageNumber = Number(url.searchParams.get('page') || '0')
      const size = Number(url.searchParams.get('size') || '20')
      pages.push(pageNumber)
      const start = pageNumber * size
      return json(route, {
        content: calendar.slice(start, start + size),
        totalElements: calendar.length,
        last: start + size >= calendar.length,
      })
    }
    if (path === '/api/v1/teams/ft' && method === 'PUT') {
      if (saveMode === 'fail') return json(route, { message: 'down' }, 500)
      return json(route, { ...team, name: 'Физтех Север' })
    }
    if (path === '/api/v1/teams/ek') return json(route, { id: 'ek', name: 'Экономика' })
    if (path === '/api/v1/teams') return json(route, { content: [team, { id: 'ek', name: 'Экономика' }] })
    if (path === '/api/v1/statistics/teams') {
      return json(route, [{ teamId: 'ft', teamName: 'Физтех', wins: 3, draws: 4, losses: 1, points: 13, goalsFor: 8, goalsAgainst: 6 }])
    }
    if (path === '/api/v1/matches/c1/availability' && method === 'POST') {
      rsvpPosts += 1
      if (rsvpMode === 'hold') {
        await new Promise<void>((resolve) => {
          releaseRsvp = resolve
        })
      }
      if (rsvpMode === 'fail') return json(route, { message: 'Не удалось сохранить ответ' }, 500)
      return json(route, going)
    }
    if (path === '/api/v1/matches/c1/availability') return json(route, going)
    if (path === '/api/v1/players') {
      return json(route, { content: [{ id: 'p9', firstName: 'Олег', lastName: 'Новиков' }], totalElements: 1 })
    }
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/my-team')
  await expect(page.getByRole('heading', { name: 'Физтех' })).toBeVisible()
  await expect(page.getByText('Вы капитан')).toBeVisible()
  await expect(page.getByText('3 победы · 4 ничьи · 1 поражение')).toBeVisible()
  await expect(page.getByText('Будут: 1 · Не будут: 1')).toBeVisible()
  await expect(page.getByText('Ваш ответ: буду')).toBeVisible()
  await expect(page.locator('.people li')).toHaveCount(4)
  await expect(page.locator('.cal')).toHaveCount(6)
  await expect(page.locator('.cal .when', { hasText: 'Дата уточняется' })).toBeVisible()
  await expect(page.getByText('0 : 0')).toBeVisible()
  await expectPenaltyUnderScore(page)
  await page.locator('.cal', { hasText: 'пен. 4 : 3' }).screenshot({ path: `${OUT}/q2r-my-team-penalty-1440.png` })
  await expect(page.locator('.cal .score', { hasText: '– : –' }).first()).toBeVisible()
  expect(pages).toContain(0)
  const card = await page.locator('[data-section="card"]').boundingBox()
  const match = await page.locator('[data-section="match"]').boundingBox()
  expect(card && match && card.x > match.x).toBeTruthy()
  await shot(page, 'q2r-my-team-captain-1440')

  await page.setViewportSize({ width: 375, height: 812 })
  const order = await page.locator('[data-section]').evaluateAll((nodes) =>
    nodes
      .map((node) => ({ name: node.getAttribute('data-section'), top: node.getBoundingClientRect().top + window.scrollY }))
      .sort((a, b) => a.top - b.top)
      .map((item) => item.name),
  )
  expect(order).toEqual(['match', 'roster', 'card', 'calendar'])
  await expectPenaltyUnderScore(page)
  await page.locator('.cal', { hasText: 'пен. 4 : 3' }).screenshot({ path: `${OUT}/q2r-my-team-penalty-375.png` })
  await noHorizontalScroll(page)
  await shot(page, 'q2r-my-team-captain-375')
  for (const width of [719, 1000]) {
    await page.setViewportSize({ width, height: 900 })
    await noHorizontalScroll(page)
  }

  await page.setViewportSize({ width: 375, height: 812 })
  rsvpMode = 'hold'
  await page.getByRole('button', { name: 'Не буду', exact: true }).click()
  await expect.poll(() => rsvpPosts).toBe(1)
  await expect(page.getByRole('button', { name: 'Буду', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Не буду', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Буду', exact: true }).click({ force: true })
  expect(rsvpPosts).toBe(1)
  rsvpMode = 'fail'
  releaseRsvp()
  await expect(page.getByText('Не удалось сохранить ответ')).toBeVisible()
  await expect(page.getByText('Ваш ответ: буду')).toBeVisible()
  await expect(page.getByText('Будут: 0')).toHaveCount(0)

  await page.getByRole('button', { name: 'Убрать' }).first().click()
  await expect(page.getByRole('heading', { name: 'Убрать «Волков Максим» из команды?' })).toBeVisible()
  await shot(page, 'q2r-my-team-delete-375')
  await page.getByRole('button', { name: 'Отмена' }).click()
  await expect(page.getByRole('link', { name: 'Волков Максим' })).toBeVisible()
  await page.getByRole('button', { name: 'Убрать' }).first().click()
  await page.getByRole('button', { name: 'Убрать из команды' }).click()
  await expect(page.getByText('Не удалось убрать игрока')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Волков Максим' })).toBeVisible()
  await page.getByRole('button', { name: 'Отмена' }).click()

  await page.getByRole('button', { name: 'Добавить игрока' }).click()
  await page.getByPlaceholder('Фамилия или имя').fill('Но')
  await page.getByRole('button', { name: 'Новиков Олег' }).click()
  expect(addPosts).toBe(0)
  await page.getByRole('button', { name: 'Добавить', exact: true }).click()
  await expect.poll(() => addPosts).toBe(1)

  await page.getByRole('textbox', { name: /Название/ }).fill('Физтех Север')
  await page.getByRole('button', { name: 'Сохранить карточку' }).click()
  await expect(page.getByText('Не удалось сохранить. Повторите ещё раз')).toBeVisible()
  await expect(page.getByRole('textbox', { name: /Название/ })).toHaveValue('Физтех Север')

  await page.unroute('**/api/v1/**')
  await signIn(page, ['PLAYER'])
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me') return json(route, user(['PLAYER']))
    if (path === '/api/v1/players/me') return json(route, profile)
    if (path === '/api/v1/teams/mine') return json(route, team)
    if (path === '/api/v1/teams/ft/members') return json(route, members)
    if (path === '/api/v1/teams/ft/matches') {
      return json(route, { content: calendar, totalElements: calendar.length, last: true })
    }
    if (path === '/api/v1/teams') return json(route, { content: [team] })
    if (path === '/api/v1/statistics/teams') {
      return json(route, [{ teamId: 'ft', wins: 3, draws: 4, losses: 1, points: 13, goalsFor: 8, goalsAgainst: 6 }])
    }
    if (path === '/api/v1/matches/c1/availability') return json(route, going)
    if (path === '/api/v1/teams/ek') return json(route, { id: 'ek', name: 'Экономика' })
    return json(route, { content: [] })
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/my-team')
  await expect(page.getByText('Вы игрок · просмотр')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Убрать' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Добавить игрока' })).toHaveCount(0)
  await expect(page.getByRole('textbox', { name: /Название/ })).toHaveCount(0)
  await expect(page.locator('.cal')).toHaveCount(6)
  await shot(page, 'q2r-my-team-player-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2r-my-team-player-375')
})

test('missing team and a role without access stay different from a load error', async ({ page }) => {
  await signIn(page, ['REFEREE'])
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') return json(route, user(['REFEREE']))
    return json(route, { content: [] })
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/my-team')
  await expect(page.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'К профилю' })).toBeVisible()
  await expect(page.getByText('0 побед')).toHaveCount(0)

  await page.unroute('**/api/v1/**')
  await signIn(page, ['PLAYER'])
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') return json(route, user(['PLAYER']))
    if (path === '/api/v1/teams/mine') return json(route, { message: 'Вы ещё не в команде' }, 404)
    return json(route, { content: [] })
  })
  await page.goto('/my-team')
  await expect(page.getByRole('heading', { name: 'Вы пока не в команде' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'К профилю' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Буду' })).toHaveCount(0)
  await noHorizontalScroll(page)
})

test('profile editor keeps keyboard focus inside the dialog', async ({ page }) => {
  await signIn(page, ['PLAYER'])
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') return json(route, user(['PLAYER']))
    if (path === '/api/v1/players/me') return json(route, profile)
    if (path === '/api/v1/players/p1/card') {
      return json(route, {
        ...profile,
        team: { id: 'ft', name: 'Физтех', shortName: 'ФТ', logoUrl: '', captain: false },
        captainTeams: [],
        statistics: { appearances: 6, goals: 3, assists: 1, yellowCards: 0, redCards: 0 },
        matchHistory: history,
      })
    }
    if (path === '/api/v1/teams') return json(route, { content: [team] })
    if (path === '/api/v1/matches') return json(route, { content: [] })
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/profile')
  const edit = page.getByRole('button', { name: 'Редактировать' })
  const teamLink = page.getByRole('link', { name: 'Моя команда' })
  await expect(edit).toBeVisible()
  await expect(teamLink).toBeVisible()
  await edit.click()

  const dialog = page.getByRole('dialog')
  const close = dialog.getByRole('button', { name: 'Закрыть' })
  const cancel = dialog.getByRole('button', { name: 'Отмена' })
  await expect(dialog).toBeVisible()
  await expect(dialog).toHaveAttribute('aria-modal', 'true')
  await expect(dialog.getByLabel('Имя')).toBeFocused()
  await shot(page, 'q2r-profile-editor-open')

  async function expectInside() {
    const state = await page.evaluate(() => {
      const active = document.activeElement
      const dialogEl = document.querySelector('[role="dialog"]')
      const team = document.querySelector('a[href="/my-team"]')
      const inside = !!active && !!dialogEl && dialogEl.contains(active)
      const onTeam = !!active && !!team && (active === team || team.contains(active))
      return { inside, onTeam }
    })
    expect(state.inside).toBe(true)
    expect(state.onTeam).toBe(false)
  }

  for (let step = 0; step < 24; step += 1) {
    await page.keyboard.press('Tab')
    await expectInside()
  }

  await close.focus()
  await page.keyboard.press('Shift+Tab')
  await expect(cancel).toBeFocused()
  await expectInside()
  await page.keyboard.press('Tab')
  await expect(close).toBeFocused()
  await expectInside()

  for (let step = 0; step < 24; step += 1) {
    await page.keyboard.press('Shift+Tab')
    await expectInside()
  }

  await cancel.click()
  await expect(dialog).toHaveCount(0)
  await expect(edit).toBeFocused()

  await edit.click()
  await expect(dialog.getByLabel('Имя')).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(edit).toBeFocused()

  await edit.click()
  await expect(dialog.getByLabel('Имя')).toBeFocused()
  await page.locator('.overlay').click({ position: { x: 8, y: 8 } })
  await expect(dialog).toHaveCount(0)
  await expect(edit).toBeFocused()

  await expect(page.locator('#app')).not.toHaveAttribute('aria-hidden', 'true')
  await expect.poll(() => page.locator('#app').evaluate((node) => node.hasAttribute('inert'))).toBe(false)
  await teamLink.focus()
  await expect(teamLink).toBeFocused()
})
