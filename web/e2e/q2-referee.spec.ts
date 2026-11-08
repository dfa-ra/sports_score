import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

const referee = {
  id: 'ref-1',
  email: 'referee@example.com',
  role: 'REFEREE',
  enabled: true,
  firstName: 'Илья',
  lastName: 'Орлов',
  roles: [{ role: 'REFEREE', status: 'APPROVED' }],
}

const player = {
  id: 'fan-1',
  email: 'player@example.com',
  role: 'PLAYER',
  enabled: true,
  firstName: 'Пётр',
  lastName: 'Петров',
  roles: [{ role: 'PLAYER', status: 'APPROVED' }],
}

const home = '11111111-1111-4111-8111-111111111111'
const away = '22222222-2222-4222-8222-222222222222'
const matchId = '054a825d-e176-4a5f-8c00-93b36f8958b1'

function token() {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  const exp = Math.floor(Date.now() / 1000) + 60 * 60
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ exp })}.sig`
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function signIn(page: Page, user: typeof referee) {
  await page.context().addInitScript(({ access, storedUser }) => {
    localStorage.setItem('sl_access', access)
    localStorage.setItem('sl_user', storedUser)
  }, { access: token(), storedUser: JSON.stringify(user) })
}

async function stamp(page: Page) {
  await page.evaluate(() => {
    document.getElementById('fixture-stamp')?.remove()
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = 'position:static;display:block;box-sizing:border-box;width:calc(100% - 16px);margin:8px auto 0;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);pointer-events:none;'
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

const listRows = [
  {
    id: 'live-1',
    status: 'LIVE',
    homeTeamId: home,
    awayTeamId: away,
    homeTeamName: 'Физтех с очень длинным названием факультета',
    awayTeamName: 'Экономика',
    homeScore: 2,
    awayScore: 1,
    period: 1,
    minute: 8,
    periodCount: 2,
    periodLengthSeconds: 1200,
    scheduledAt: '2026-11-08T12:00:00Z',
  },
  {
    id: 'soon-1',
    status: 'SCHEDULED',
    homeTeamId: home,
    awayTeamId: away,
    homeTeamName: 'Медики',
    awayTeamName: 'Архитекторы',
    homeScore: 0,
    awayScore: 0,
    scheduledAt: '2026-11-10T15:00:00Z',
  },
  {
    id: 'nodate',
    status: 'SCHEDULED',
    homeTeamId: home,
    awayTeamId: away,
    homeTeamName: 'Физтех',
    awayTeamName: 'Юристы',
    homeScore: 0,
    awayScore: 0,
    scheduledAt: null,
  },
  {
    id: 'done-1',
    status: 'FINISHED',
    homeTeamId: away,
    awayTeamId: home,
    homeTeamName: 'Экономика',
    awayTeamName: 'Медики',
    homeScore: 1,
    awayScore: 1,
    penaltyHome: 5,
    penaltyAway: 3,
    scheduledAt: '2026-11-01T12:00:00Z',
  },
  {
    id: 'nil',
    status: 'FINISHED',
    homeTeamId: home,
    awayTeamId: away,
    homeTeamName: 'Юристы',
    awayTeamName: 'Физтех',
    homeScore: 0,
    awayScore: 0,
    scheduledAt: '2026-11-02T12:00:00Z',
  },
]

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('referee list groups real rows and keeps empty apart from an error', async ({ page }) => {
  let mode: 'ready' | 'fail' | 'empty' = 'ready'
  await signIn(page, referee)
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/me')) return json(route, referee)
    if (path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/referee/matches')) {
      if (mode === 'fail') return json(route, { message: 'down' }, 500)
      if (mode === 'empty') return json(route, [])
      return json(route, listRows)
    }
    if (path.endsWith('/teams')) return json(route, { content: [] })
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/referee')
  await expect(page.getByRole('heading', { name: 'Пульт судьи' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'В игре' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Предстоящие' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Завершённые' })).toBeVisible()
  await expect(page.getByRole('link', { name: /Медики/ }).first()).toContainText('– : –')
  await expect(page.getByText('Дата уточняется')).toBeVisible()
  await expect(page.getByText('0 : 0')).toBeVisible()
  await expect(page.getByText('Пенальти 5 : 3')).toBeVisible()
  await expect(page.getByText('1 : 1')).toBeVisible()
  await expect(page.getByText('Макет')).toHaveCount(0)
  await noHorizontalScroll(page)
  await shot(page, 'q2-referee-list-1440')

  for (const width of [1000, 719, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await noHorizontalScroll(page)
  }
  await shot(page, 'q2-referee-list-375')

  mode = 'fail'
  await page.reload()
  await expect(page.getByText('Не удалось загрузить матчи')).toBeVisible()
  await expect(page.getByText('Нет назначенных матчей')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible()
  mode = 'ready'
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByRole('heading', { name: 'В игре' })).toBeVisible()

  mode = 'empty'
  await page.reload()
  await expect(page.getByText('Нет назначенных матчей')).toBeVisible()
  await expect(page.getByText('Новые назначения появятся здесь')).toBeVisible()
  await expect(page.getByText('Не удалось загрузить матчи')).toHaveCount(0)
})

test('referee console keeps the pad actions, traps dialogs, and reconciles an unclear save', async ({ page }) => {
  let posts = 0
  let finishes = 0
  let dropNext = false
  let finishMode: 'hold' | 'fail' | 'ok' = 'hold'
  const saved: unknown[] = []
  await signIn(page, referee)
  await page.route('**/ws/info**', (route) => json(route, { entropy: 1, origins: ['*:*'], cookie_needed: false, websocket: true }))
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    if (path.endsWith('/auth/me')) return json(route, referee)
    if (path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/teams') && method === 'GET') {
      return json(route, {
        content: [
          { id: home, name: 'Физтех' },
          { id: away, name: 'Экономика' },
        ],
      })
    }
    if (path.endsWith(`/teams/${home}/members`) || path.endsWith(`/teams/${away}/members`)) {
      const teamId = path.includes(home) ? home : away
      return json(route, [{
        playerId: teamId === home ? 'p-home' : 'p-away',
        teamId,
        firstName: teamId === home ? 'Илья' : 'Егор',
        lastName: teamId === home ? 'Орлов' : 'Морозов',
        jerseyNumber: teamId === home ? 9 : 4,
      }])
    }
    if (path.endsWith(`/matches/${matchId}`) && method === 'GET') {
      return json(route, {
        id: matchId,
        status: finishes > 0 && finishMode === 'ok' ? 'FINISHED' : 'LIVE',
        sportCode: 'FOOTBALL',
        homeTeamId: home,
        awayTeamId: away,
        homeScore: saved.length ? 1 : 0,
        awayScore: 0,
        period: 1,
        periodCount: 2,
        periodLengthSeconds: 1200,
        gameTimeSeconds: 80,
        homeGoalkeeper: null,
        awayGoalkeeper: null,
      })
    }
    if (path.endsWith(`/matches/${matchId}/events`) && method === 'GET') return json(route, saved)
    if (path.endsWith(`/matches/${matchId}/lineups`)) return json(route, { home: { confirmed: false }, away: { confirmed: false } })
    if (path.endsWith(`/matches/${matchId}/referees`)) return json(route, [{ refereeId: referee.id }])
    if (path.endsWith(`/matches/${matchId}/shootout`)) return json(route, { kicks: [], decisive: false })
    if (path.endsWith(`/matches/${matchId}/analyst-assignment`)) {
      return json(route, { matchId, mode: 'BOTH', analyst: null, homeAnalyst: null, awayAnalyst: null })
    }
    if (path.endsWith('/referee/analysts')) return json(route, [])
    if (method === 'POST' && path.endsWith(`/referee/matches/${matchId}/events`)) {
      posts += 1
      const event = {
        id: 'goal-1',
        eventType: 'GOAL',
        teamId: home,
        playerId: 'p-home',
        playerName: 'Орлов Илья',
        playerFirstName: 'Илья',
        playerLastName: 'Орлов',
        playerJersey: 9,
        voided: false,
      }
      saved.splice(0, saved.length, event)
      if (dropNext) {
        dropNext = false
        return route.abort('failed')
      }
      return json(route, event, 201)
    }
    if (method === 'POST' && path.endsWith(`/referee/matches/${matchId}/finish`)) {
      finishes += 1
      if (finishMode === 'fail') return json(route, {}, 500)
      finishMode = 'ok'
      return json(route, { id: matchId, status: 'FINISHED' })
    }
    if (method === 'PUT' && path.endsWith('/goalkeeper')) return json(route, {})
    return json(route, {})
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`/referee/matches/${matchId}`)
  await expect(page.locator('.scoreboard .score')).toHaveText('0 : 0')
  await expect(page.getByRole('button', { name: 'Пауза' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Следующий тайм' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Завершить матч' })).toBeVisible()
  await expect(page.getByText('Нет доступных аналитиков')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Назначить' })).toBeDisabled()
  const pause = await page.getByRole('button', { name: 'Пауза' }).boundingBox()
  const goal = await page.getByRole('button', { name: 'Гол', exact: true }).boundingBox()
  const keepers = await page.getByRole('heading', { name: 'Вратари' }).boundingBox()
  expect(pause && goal && keepers).toBeTruthy()
  expect(goal!.y).toBeGreaterThan(pause!.y)
  expect(keepers!.x).toBeGreaterThan(goal!.x)
  await noHorizontalScroll(page)
  await shot(page, 'q2-referee-console-1440')

  await page.setViewportSize({ width: 375, height: 900 })
  const phonePause = await page.getByRole('button', { name: 'Пауза' }).boundingBox()
  const phoneKeepers = await page.getByRole('heading', { name: 'Вратари' }).boundingBox()
  const phoneGoal = await page.getByRole('button', { name: 'Гол', exact: true }).boundingBox()
  const phoneFinish = await page.getByRole('button', { name: 'Завершить матч' }).boundingBox()
  expect(phonePause && phoneGoal && phoneKeepers && phoneFinish).toBeTruthy()
  expect(phoneGoal!.y).toBeGreaterThan(phonePause!.y)
  expect(phoneKeepers!.y).toBeGreaterThan(phoneGoal!.y)
  expect(phoneFinish!.y).toBeGreaterThan(phoneKeepers!.y)
  await noHorizontalScroll(page)
  await shot(page, 'q2-referee-console-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: 'Гол', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(page.locator('#app')).toHaveAttribute('inert', '')
  await expect(page.locator('#app')).toHaveAttribute('aria-hidden', 'true')
  await expect(dialog.getByRole('button', { name: 'Физтех' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(dialog).toContainText(await page.evaluate(() => document.activeElement?.textContent || ''))
  const stillInside = await page.evaluate(() => {
    const root = document.querySelector('[role="dialog"]')
    return !!root && root.contains(document.activeElement)
  })
  expect(stillInside).toBe(true)
  await page.keyboard.press('Shift+Tab')
  expect(await page.evaluate(() => document.querySelector('[role="dialog"]')?.contains(document.activeElement))).toBe(true)
  await noHorizontalScroll(page)
  await shot(page, 'q2-referee-event-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2-referee-event-375')
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Гол', exact: true })).toBeFocused()
  expect(posts).toBe(0)

  dropNext = true
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: 'Гол', exact: true }).click()
  await dialog.getByRole('button', { name: 'Физтех' }).click()
  await dialog.getByRole('button', { name: '#9 Орлов Илья' }).click()
  await dialog.getByRole('button', { name: 'Без передачи' }).click()
  await expect(page.locator('.proto').filter({ hasText: 'Гол' })).toBeVisible()
  await expect(page.getByText('Не удалось добавить событие')).toHaveCount(0)
  expect(posts).toBe(1)
  await expect(page.locator('.scoreboard .score')).toHaveText('1 : 0')

  await page.getByRole('button', { name: 'Завершить матч' }).click()
  const finish = page.getByRole('dialog')
  await expect(finish.getByRole('heading', { name: 'Завершить матч?' })).toBeVisible()
  await expect(finish.getByText('Проверьте счёт и протокол.')).toBeVisible()
  await expect(finish.getByText('Действие завершит матч.')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(finish).toHaveCount(0)
  expect(finishes).toBe(0)
  await expect(page.getByRole('button', { name: 'Завершить матч' })).toBeFocused()

  finishMode = 'fail'
  await page.getByRole('button', { name: 'Завершить матч' }).click()
  await shot(page, 'q2-referee-finish-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2-referee-finish-375')
  await page.setViewportSize({ width: 1440, height: 900 })
  await finish.getByRole('button', { name: 'Вернуться к протоколу' }).click()
  await expect(finish).toHaveCount(0)
  expect(finishes).toBe(0)

  await page.getByRole('button', { name: 'Завершить матч' }).click()
  await finish.getByRole('button', { name: 'Завершить матч' }).click()
  await expect(finish.getByText('Не удалось завершить матч. Повторите попытку')).toBeVisible()
  await expect(finish).toBeVisible()
  expect(finishes).toBe(1)
  finishMode = 'ok'
  await finish.getByRole('button', { name: 'Завершить матч' }).click()
  await expect(page.getByText('Матч завершён', { exact: true })).toBeVisible()
  await expect(page.getByText('Матч завершён.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Гол', exact: true })).toHaveCount(0)
  expect(finishes).toBe(2)

  await page.setViewportSize({ width: 719, height: 900 })
  await noHorizontalScroll(page)
  await page.setViewportSize({ width: 1000, height: 900 })
  await noHorizontalScroll(page)
})

test('a player does not get the referee pad, and an unassigned referee is not an empty list', async ({ page }) => {
  await signIn(page, player)
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/me')) return json(route, player)
    if (path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/referee/matches')) return json(route, listRows)
    return json(route, { content: [] })
  })
  await page.goto('/referee')
  await expect(page.getByRole('heading', { name: 'Недостаточно прав' })).toBeVisible()
  await expect(page.getByText('Нет назначенных матчей')).toHaveCount(0)

  await signIn(page, referee)
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path.endsWith('/auth/me')) return json(route, referee)
    if (path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith(`/matches/${matchId}`) && method === 'GET') {
      return json(route, {
        id: matchId,
        status: 'LIVE',
        homeTeamId: home,
        awayTeamId: away,
        homeScore: 0,
        awayScore: 0,
        periodLengthSeconds: 1200,
        period: 1,
        periodCount: 2,
      })
    }
    if (path.endsWith(`/matches/${matchId}/referees`)) return json(route, [])
    if (path.endsWith(`/matches/${matchId}/events`)) return json(route, [])
    if (path.endsWith(`/matches/${matchId}/lineups`)) return json(route, {})
    if (path.endsWith(`/matches/${matchId}/shootout`)) return json(route, { kicks: [] })
    if (path.endsWith('/teams')) return json(route, { content: [{ id: home, name: 'Физтех' }, { id: away, name: 'Экономика' }] })
    return json(route, {})
  })
  await page.goto(`/referee/matches/${matchId}`)
  await expect(page.getByText('Нет доступа к пульту')).toBeVisible()
  await expect(page.getByRole('link', { name: 'К профилю' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Гол', exact: true })).toHaveCount(0)
  await expect(page.getByText('Нет назначенных матчей')).toHaveCount(0)
})
