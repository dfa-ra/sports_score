import { mkdirSync } from 'node:fs'

import { expect, test, type Page, type Route } from '@playwright/test'

const shots = '/opt/cursor/artifacts'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

type Fixture = {
  id: string
  homeTeamId: string
  awayTeamId: string
  homeName: string
  awayName: string
  homeScore: number
  awayScore: number
  periodLengthSeconds: number
  events: Record<string, unknown>[]
}

function event(matchId: string, row: Record<string, unknown>) {
  return {
    matchId,
    timestamp: '2026-08-31T14:24:00Z',
    createdAt: '2026-08-31T14:24:00Z',
    metadata: null,
    voided: false,
    playerJersey: null,
    secondaryPlayerJersey: null,
    ...row,
  }
}

const lawyers = '2b29b820-c1b6-4d92-9e8b-bb43d0e11c6e'
const architects = '99c1e1aa-2158-443c-be3c-452102324121'
const phystech = '518f5047-d6d8-4e68-8863-eb285c3c1eca'
const alpha = 'd4577c25-38ee-4e82-9a0e-ef776f2176da'
const beta = '5df623bc-24fa-411a-914d-fbc4dae1d3ac'

const cardsMatch: Fixture = {
  id: '519fd3e0-4726-4b55-82c3-eeaa4e7c6c33',
  homeTeamId: lawyers,
  awayTeamId: architects,
  homeName: 'Юристы',
  awayName: 'Архитекторы',
  homeScore: 2,
  awayScore: 0,
  periodLengthSeconds: 1200,
  events: [
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', { id: 'p-start', eventType: 'PERIOD_START', period: 1, gameTime: 0, minute: 1, createdAt: '2026-08-31T14:24:04.783788Z' }),
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', {
      id: 'g1', eventType: 'GOAL', period: 1, gameTime: 9, minute: 0, teamId: lawyers,
      playerId: 'pl-leb', playerFirstName: 'Кирилл', playerLastName: 'Лебедев', playerJersey: 9,
      secondaryPlayerId: 'pl-mor', secondaryPlayerFirstName: 'Даниил', secondaryPlayerLastName: 'Морозов', secondaryPlayerJersey: 1,
      createdAt: '2026-08-31T14:24:13.803567Z',
    }),
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', {
      id: 'yc', eventType: 'YELLOW_CARD', period: 1, gameTime: 17, minute: 0, teamId: architects,
      playerId: 'pl-mor2', playerFirstName: 'Егор', playerLastName: 'Морозов', playerJersey: 9,
      createdAt: '2026-08-31T14:24:21.849116Z',
    }),
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', {
      id: 'rc', eventType: 'RED_CARD', period: 1, gameTime: 25, minute: 0, teamId: lawyers,
      playerId: 'pl-bel', playerFirstName: 'Илья', playerLastName: 'Белов', playerJersey: 10,
      createdAt: '2026-08-31T14:24:30.551456Z',
    }),
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', {
      id: 'g2', eventType: 'GOAL', period: 1, gameTime: 35, minute: 0, teamId: lawyers,
      playerId: 'pl-kry', playerFirstName: 'Максим', playerLastName: 'Крылов', playerJersey: 8,
      createdAt: '2026-08-31T14:24:40.461521Z',
    }),
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', { id: 'p-end', eventType: 'PERIOD_END', period: 1, gameTime: 39, minute: 1 }),
    event('519fd3e0-4726-4b55-82c3-eeaa4e7c6c33', { id: 'p2', eventType: 'PERIOD_START', period: 2, gameTime: 1200, minute: 20 }),
  ],
}

const ownGoalMatch: Fixture = {
  id: 'c805bed1-39fb-4ad2-90ca-fccc22f2553e',
  homeTeamId: alpha,
  awayTeamId: beta,
  homeName: 'QA Толян Альфа',
  awayName: 'QA Толян Бета',
  homeScore: 1,
  awayScore: 1,
  periodLengthSeconds: 900,
  events: [
    event('c805bed1-39fb-4ad2-90ca-fccc22f2553e', { id: 'own-start', eventType: 'PERIOD_START', period: 1, gameTime: 0 }),
    event('c805bed1-39fb-4ad2-90ca-fccc22f2553e', {
      id: 'own-goal', eventType: 'GOAL', period: 1, gameTime: 180, teamId: alpha,
      playerId: 'pl-tol', playerFirstName: 'Толян', playerLastName: 'Игроков',
    }),
    event('c805bed1-39fb-4ad2-90ca-fccc22f2553e', {
      id: 'own-own', eventType: 'GOAL', period: 1, gameTime: 360, teamId: alpha,
      playerId: 'pl-tol', playerFirstName: 'Толян', playerLastName: 'Игроков',
      metadata: { ownGoal: true },
    }),
  ],
}

const goalsMatch: Fixture = {
  id: 'bbaa7821-bad6-46ca-a126-86ca3c18078f',
  homeTeamId: phystech,
  awayTeamId: lawyers,
  homeName: 'Физтех',
  awayName: 'Юристы',
  homeScore: 3,
  awayScore: 1,
  periodLengthSeconds: 1200,
  events: [
    event('bbaa7821-bad6-46ca-a126-86ca3c18078f', {
      id: 'b1', eventType: 'GOAL', period: 1, gameTime: 540, teamId: phystech,
      playerId: 'pl-vol', playerFirstName: 'Максим', playerLastName: 'Волков', playerJersey: 9,
      secondaryPlayerId: 'pl-orl', secondaryPlayerFirstName: 'Илья', secondaryPlayerLastName: 'Орлов', secondaryPlayerJersey: 8,
    }),
    event('bbaa7821-bad6-46ca-a126-86ca3c18078f', {
      id: 'b2', eventType: 'GOAL', period: 1, gameTime: 840, teamId: lawyers,
      playerId: 'pl-leb', playerFirstName: 'Кирилл', playerLastName: 'Лебедев', playerJersey: 9,
      secondaryPlayerId: 'pl-kry', secondaryPlayerFirstName: 'Максим', secondaryPlayerLastName: 'Крылов', secondaryPlayerJersey: 8,
    }),
    event('bbaa7821-bad6-46ca-a126-86ca3c18078f', {
      id: 'b3', eventType: 'GOAL', period: 1, gameTime: 1020, teamId: phystech,
      playerId: 'pl-orl', playerFirstName: 'Илья', playerLastName: 'Орлов', playerJersey: 8,
      secondaryPlayerId: 'pl-sok', secondaryPlayerFirstName: 'Артём', secondaryPlayerLastName: 'Соколов', secondaryPlayerJersey: 10,
    }),
    event('bbaa7821-bad6-46ca-a126-86ca3c18078f', {
      id: 'b4', eventType: 'GOAL', period: 1, gameTime: 1500, teamId: phystech,
      playerId: 'pl-vol', playerFirstName: 'Максим', playerLastName: 'Волков', playerJersey: 9,
      secondaryPlayerId: 'pl-orl', secondaryPlayerFirstName: 'Илья', secondaryPlayerLastName: 'Орлов', secondaryPlayerJersey: 8,
    }),
  ],
}

async function install(page: Page, fixture: Fixture, eventsMode: 'ok' | 'error' | 'empty' | 'slow' = 'ok') {
  let releaseEvents: () => void = () => {}
  const eventsGate = eventsMode === 'slow'
    ? new Promise<void>((resolve) => { releaseEvents = resolve })
    : Promise.resolve()
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path.endsWith('/teams') && route.request().method() === 'GET') {
      return json(route, {
        content: [
          { id: fixture.homeTeamId, name: fixture.homeName, shortName: fixture.homeName },
          { id: fixture.awayTeamId, name: fixture.awayName, shortName: fixture.awayName },
        ],
      })
    }
    if (path.endsWith(`/matches/${fixture.id}`) && route.request().method() === 'GET') {
      return json(route, {
        id: fixture.id,
        tournamentId: 'b16257ae-e20e-49ac-bb3c-c96a2173d2ee',
        status: 'FINISHED',
        sportCode: 'FOOTBALL',
        homeTeamId: fixture.homeTeamId,
        awayTeamId: fixture.awayTeamId,
        homeTeamName: fixture.homeName,
        awayTeamName: fixture.awayName,
        homeScore: fixture.homeScore,
        awayScore: fixture.awayScore,
        period: 2,
        periodCount: 2,
        periodLengthSeconds: fixture.periodLengthSeconds,
        scheduledAt: '2026-09-08T11:12:46Z',
      })
    }
    if (path.endsWith(`/matches/${fixture.id}/events`)) {
      await eventsGate
      if (eventsMode === 'error') return json(route, { message: 'no' }, 500)
      if (eventsMode === 'empty') return json(route, [])
      return json(route, fixture.events)
    }
    if (path.endsWith(`/matches/${fixture.id}/lineups`)) {
      return json(route, { home: { confirmed: false, starters: [], bench: [] }, away: { confirmed: false, starters: [], bench: [] } })
    }
    if (path.endsWith(`/matches/${fixture.id}/referees`)) return json(route, [])
    if (path.endsWith(`/matches/${fixture.id}/shootout`)) return json(route, { kicks: [], decisive: false })
    if (path.includes('/form')) return json(route, [])
    if (path.includes('/tournaments/')) return json(route, { id: 'b16257ae-e20e-49ac-bb3c-c96a2173d2ee', name: 'Лига' })
    return json(route, {})
  })
  await page.routeWebSocket(/\/ws/, (ws) => { ws.onMessage(() => {}) })
  return releaseEvents
}

async function shoot(page: Page, fixture: Fixture, slug: string) {
  const card = page.locator('.card.events')
  for (const width of [320, 375, 1440]) {
    await page.setViewportSize({ width, height: width >= 720 ? 1100 : 900 })
    await card.scrollIntoViewIfNeeded()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow, `${slug} scroll at ${width}`).toBeLessThanOrEqual(1)
    await card.screenshot({ path: `${shots}/match-feed-${slug}-${width}.png` })
  }
}

test('dev-like feeds mirror goals, cards, and an own goal', async ({ page }) => {
  mkdirSync(shots, { recursive: true })
  await install(page, cardsMatch)
  await page.goto(`/matches/${cardsMatch.id}`)
  const card = page.locator('.card.events')
  await expect(card.getByText('Лебедев Кирилл')).toBeVisible()
  await expect(card.getByText('Начало тайма')).toHaveCount(0)
  await expect(card.getByText('2-Й ТАЙМ')).toHaveCount(0)
  await expect(card.locator('.half')).toContainText('2 : 0')
  await expect(card.locator('.total b')).toHaveText('2 : 0')
  await expect(card.locator('.ev').nth(0).locator('.min')).toHaveText("0'")
  await expect(card.locator('.ev').nth(0).locator('.run')).toHaveText('1 : 0')
  await expect(card.locator('.ev').nth(1)).toContainText('Жёлтая')
  await expect(card.locator('.ev').nth(2)).toContainText('Красная')
  await expect(card.locator('.ev').nth(1)).not.toContainText('(')
  await shoot(page, cardsMatch, '519-cards')

  await install(page, ownGoalMatch)
  await page.goto(`/matches/${ownGoalMatch.id}`)
  const own = page.locator('.card.events')
  await expect(own.locator('.ev').nth(0)).toHaveClass(/home/)
  await expect(own.locator('.ev').nth(1)).toHaveClass(/away/)
  await expect(own.locator('.ev').nth(1)).toContainText('автогол')
  await expect(own.locator('.ev').nth(1).locator('.run')).toHaveText('1 : 1')
  await expect(own.locator('.half')).toContainText('1 : 1')
  await expect(own.locator('.total b')).toHaveText('1 : 1')
  await shoot(page, ownGoalMatch, 'c805-own-goal')

  await install(page, goalsMatch)
  await page.goto(`/matches/${goalsMatch.id}`)
  const goals = page.locator('.card.events')
  await expect(goals.locator('.ev')).toHaveCount(4)
  await expect(goals.locator('.ev').nth(0).locator('.min')).toHaveText("9'")
  await expect(goals.locator('.ev').nth(1)).toHaveClass(/away/)
  await expect(goals.locator('.ev').nth(3).locator('.run')).toHaveText('3 : 1')
  await expect(goals.locator('.half')).toContainText('3 : 1')
  await expect(goals.getByText('передача · #8 Орлов Илья').first()).toBeVisible()
  await shoot(page, goalsMatch, 'bbaa-goals')
})

test('event feed shows loading, empty, and a retry that is at least 44px', async ({ page }) => {
  mkdirSync(shots, { recursive: true })
  await page.setViewportSize({ width: 375, height: 900 })
  const release = await install(page, cardsMatch, 'slow')
  await page.goto(`/matches/${cardsMatch.id}`)
  const card = page.locator('.card.events')
  await expect(card.getByText('Загрузка событий…')).toBeVisible()
  await expect(card.locator('.events-skel')).toHaveCount(5)
  await card.screenshot({ path: `${shots}/match-feed-loading-375.png` })
  release()
  await expect(card.getByText('Лебедев Кирилл')).toBeVisible()

  await install(page, cardsMatch, 'empty')
  await page.goto(`/matches/${cardsMatch.id}`)
  await expect(card.getByText('Событий пока нет')).toBeVisible()
  await expect(card.getByText('Появятся после первого события матча')).toBeVisible()
  await card.screenshot({ path: `${shots}/match-feed-empty-375.png` })

  await install(page, cardsMatch, 'error')
  await page.goto(`/matches/${cardsMatch.id}`)
  await expect(card.getByText('Не удалось загрузить события')).toBeVisible()
  const retry = card.getByRole('button', { name: 'Повторить' })
  const box = await retry.boundingBox()
  expect(box?.width ?? 0).toBeGreaterThanOrEqual(44)
  expect(box?.height ?? 0).toBeGreaterThanOrEqual(44)
  await card.screenshot({ path: `${shots}/match-feed-error-375.png` })
  await install(page, cardsMatch, 'ok')
  await retry.click()
  await expect(card.getByText('Лебедев Кирилл')).toBeVisible()
})
