import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const tournamentId = 'b27691c2-9fbb-4218-bb0b-fdc1f6275b47'
const homeId = '11111111-1111-4111-8111-111111111111'
const awayId = '22222222-2222-4222-8222-222222222222'
const matchId = '33333333-3333-4333-8333-333333333333'

type Mode = 'winner' | 'long' | 'pens' | 'none' | 'error' | 'slow'

const state: { mode: Mode } = { mode: 'winner' }
const playoffWaiters: Array<() => void> = []

function releasePlayoff() {
  playoffWaiters.splice(0).forEach((resolve) => resolve())
}

function pair(id: string, round: number, slotIndex: number, home: string, away: string, status = 'SCHEDULED', scores?: { home: number; away: number; penaltyHome?: number; penaltyAway?: number }) {
  return {
    id,
    round,
    slotIndex,
    kind: 'MAIN',
    roundTitle: round === 1 ? '1/4 финала' : round === 2 ? '1/2 финала' : 'Финал',
    matchId: `match-${id}`,
    homeTeamId: `${id}-h`,
    homeTeamName: home,
    awayTeamId: `${id}-a`,
    awayTeamName: away,
    matchStatus: status,
    homeScore: scores?.home ?? (status === 'FINISHED' ? 1 : 0),
    awayScore: scores?.away ?? 0,
    penaltyHome: scores?.penaltyHome ?? null,
    penaltyAway: scores?.penaltyAway ?? null,
    scheduledAt: '2026-11-01T16:00:00Z',
  }
}

function rounds() {
  const finalName = state.mode === 'long'
    ? 'Факультет журналистики и международной коммуникации'
    : 'Физтех'
  const finalStatus = state.mode === 'none' ? 'SCHEDULED' : 'FINISHED'
  const scores = state.mode === 'pens'
    ? { home: 0, away: 0, penaltyHome: 5, penaltyAway: 3 }
    : state.mode === 'none'
      ? { home: 0, away: 0 }
      : { home: 2, away: 1 }
  return [
    {
      round: 1,
      title: '1/4 финала',
      slots: [
        pair('q1', 1, 0, 'Альфа', 'Бета', 'FINISHED'),
        pair('q2', 1, 1, 'Гамма', 'Дельта', 'FINISHED'),
        pair('q3', 1, 2, 'Эпсилон', 'Зета', 'FINISHED'),
        pair('q4', 1, 3, 'Эта', 'Тета', 'FINISHED'),
      ],
    },
    {
      round: 2,
      title: '1/2 финала',
      slots: [
        pair('s1', 2, 0, 'Альфа', 'Гамма', 'FINISHED'),
        pair('s2', 2, 1, 'Эпсилон', 'Эта', 'FINISHED'),
      ],
    },
    {
      round: 3,
      title: 'Финал',
      slots: [{
        ...pair('final', 3, 0, finalName, 'Экономика', finalStatus, scores),
        matchId,
        homeTeamId: homeId,
        awayTeamId: awayId,
      }],
    },
  ]
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') return json(route, { content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) {
      if (state.mode === 'slow') await new Promise<void>((resolve) => { playoffWaiters.push(resolve) })
      if (state.mode === 'error') return json(route, { message: 'down' }, 500)
      return json(route, { competitionStage: 'PLAYOFF', rounds: rounds() })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
      return json(route, {
        name: 'Кубок',
        format: 'GROUPS_PLAYOFF',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: 8,
        matchesCount: 7,
        seasonYear: 2026,
        playoffLive: false,
        status: 'FINISHED',
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}`)) {
      return json(route, {
        id: tournamentId,
        name: 'Кубок',
        status: 'FINISHED',
        format: 'GROUPS_PLAYOFF',
        competitionStage: 'PLAYOFF',
        seasonYear: 2026,
        regulations: '',
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) return json(route, [])
    if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return json(route, [])
    if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return json(route, { content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return json(route, [])
    return json(route, {})
  })
}

async function noHorizontalScroll(page: Page) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBe(metrics.clientWidth)
}

async function snapSwipe(page: Page) {
  await page.evaluate(() => {
    const el = document.querySelector('.po-swipe')
    const active = el?.querySelector('.po-panel.on') as HTMLElement | null
    if (el && active) el.scrollLeft = active.offsetLeft
  })
}

async function shot(page: Page, name: string) {
  mkdirSync(OUT, { recursive: true })
  await snapSwipe(page)
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('champion card uses the decided final and keeps every round', async ({ page }) => {
  state.mode = 'slow'
  await mockApi(page)

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('status', { name: 'Сетка загружается' })).toBeVisible()
  await expect(page.locator('[data-champion]')).toHaveCount(0)
  state.mode = 'winner'
  releasePlayoff()
  await expect(page.locator('[data-champion]')).toBeVisible()
  await expect(page.getByText('Макет')).toHaveCount(0)

  const card = page.locator('[data-champion]')
  await expect(card.locator('.champion-name')).toHaveText('Физтех')
  await expect(card.locator('.team-crest--fallback')).toHaveText('Ф')
  await expect(card.locator('.champion-score')).toHaveText('2 : 1')
  await expect(card.locator('.champion-pens')).toHaveCount(0)
  await expect(card.locator('.champion-meta')).toContainText('Кубок')
  await expect(card.locator('.champion-meta')).toContainText('Сезон 2026')
  const team = card.getByRole('link', { name: /Физтех/ })
  const result = card.getByRole('link', { name: '2 : 1' })
  await expect(team).toHaveAttribute('href', `/teams/${homeId}`)
  await expect(result).toHaveAttribute('href', `/matches/${matchId}`)
  expect(await team.evaluate((el) => el.querySelector('a'))).toBeNull()
  expect(await result.evaluate((el) => el.closest('.champion-team'))).toBeNull()

  await expect(page.locator('.po-grid h3.rh')).toHaveCount(3)
  await expect(page.locator('.po-grid h3.rh').nth(0)).toContainText('1/4 финала')
  await expect(page.locator('.po-grid h3.rh').nth(1)).toContainText('1/2 финала')
  await expect(page.locator('.po-grid h3.rh').nth(2)).toContainText('Финал')
  await expect(page.locator('.po-grid .pc')).toHaveCount(7)
  await expect(page.locator('.champ-cell, .b-champ, .champ-block')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Чемпион', exact: true })).toHaveCount(0)
  await noHorizontalScroll(page)
  await shot(page, 'q2-champion-1440')

  for (const width of [1000, 719, 375]) {
    await page.setViewportSize({ width, height: 900 })
    await noHorizontalScroll(page)
  }

  await page.setViewportSize({ width: 375, height: 812 })
  const tabs = page.getByRole('tablist', { name: 'Раунды' })
  await expect(tabs).toBeVisible()
  const cardBox = await card.boundingBox()
  const tabBox = await tabs.boundingBox()
  expect(cardBox && tabBox).toBeTruthy()
  expect(cardBox!.y + cardBox!.height).toBeLessThanOrEqual(tabBox!.y + 1)
  expect(await card.evaluate((el) => !el.closest('.po-swipe'))).toBe(true)
  await tabs.getByRole('tab', { name: '1/4', exact: true }).click()
  await expect(page.locator('.po-panel.on > .pair-stack > .pair-block')).toHaveCount(4)
  await tabs.getByRole('tab', { name: '1/2', exact: true }).click()
  await expect(page.locator('.po-panel.on > .pair-stack > .pair-block')).toHaveCount(2)
  await tabs.getByRole('tab', { name: 'Финал', exact: true }).click()
  await expect(page.locator('.po-panel.on > .pair-stack > .pair-block')).toHaveCount(1)
  await shot(page, 'q2-champion-375')

  state.mode = 'long'
  await page.setViewportSize({ width: 375, height: 900 })
  await page.reload()
  const longName = page.locator('.champion-name')
  await expect(longName).toHaveText('Факультет журналистики и международной коммуникации')
  await expect(page.locator('[data-champion] .team-crest--fallback')).toHaveText('ФЖ')
  const wrap = await longName.evaluate((el) => {
    const line = parseFloat(getComputedStyle(el).lineHeight) || 1
    const score = el.closest('[data-champion]')?.querySelector('.champion-score')
    const nameBox = el.getBoundingClientRect()
    const scoreBox = score?.getBoundingClientRect()
    const overlaps = !!scoreBox && nameBox.bottom > scoreBox.top && nameBox.left < scoreBox.right && nameBox.right > scoreBox.left
    return {
      lines: el.scrollHeight / line,
      clipped: el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1,
      overlaps,
    }
  })
  expect(wrap.lines).toBeGreaterThan(1.6)
  expect(wrap.clipped).toBe(false)
  expect(wrap.overlaps).toBe(false)
  await noHorizontalScroll(page)
  await shot(page, 'q2-champion-long-375')

  state.mode = 'pens'
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.reload()
  await expect(page.locator('.champion-score')).toHaveText('0 : 0')
  await expect(page.locator('.champion-pens')).toHaveText('пен. 5 : 3')
  await expect(page.locator('.po-grid .pc')).toHaveCount(7)
  await shot(page, 'q2-champion-pens-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2-champion-pens-375')

  state.mode = 'none'
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.reload()
  await expect(page.locator('[data-champion]')).toHaveCount(0)
  await expect(page.getByText('Не удалось загрузить сетку')).toHaveCount(0)
  await expect(page.locator('.po-grid .pc')).toHaveCount(7)
  await expect(page.locator('.po-grid h3.rh')).toHaveCount(3)
  await shot(page, 'q2-champion-none-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await expect(page.locator('[data-champion]')).toHaveCount(0)
  await expect(page.getByRole('tab', { name: 'Финал', exact: true })).toBeVisible()
  await noHorizontalScroll(page)
  await shot(page, 'q2-champion-none-375')

  state.mode = 'error'
  await page.reload()
  await expect(page.getByText('Не удалось загрузить сетку')).toBeVisible()
  await expect(page.locator('[data-champion]')).toHaveCount(0)
  await expect(page.getByText('Сетка ещё не сформирована')).toHaveCount(0)
})
