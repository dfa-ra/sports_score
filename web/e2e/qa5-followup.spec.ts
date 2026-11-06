import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const tournamentId = '00000000-0000-4000-8000-0000000000a5'
const matchId = 'f0238fa1-0000-4000-8000-000000000001'
const teamId = '00000000-0000-4000-8000-0000000000d5'
const otherTeamId = '00000000-0000-4000-8000-0000000000e5'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function rotate<T>(rows: T[], turn: number) {
  if (!rows.length) return rows
  const shift = turn % rows.length
  return rows.slice(shift).concat(rows.slice(0, shift))
}

const match = {
  id: matchId,
  tournamentId,
  tournamentName: 'QA Толян Кубок 2',
  homeTeamId: teamId,
  awayTeamId: otherTeamId,
  homeTeamName: 'QA Толян Альфа',
  awayTeamName: 'QA Толян Бета',
  status: 'LIVE',
  homeScore: 1,
  awayScore: 0,
  scheduledAt: '2026-11-06T16:00:00Z',
  startedAt: '2026-11-06T16:00:00Z',
}

const homeRows = [
  { id: 'h-new', status: 'FINISHED', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 2, awayScore: 0, startedAt: '2026-11-03T12:00:00Z' },
  { id: 'h-mid', status: 'FINISHED', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 1, awayScore: 1, startedAt: '2026-10-02T12:00:00Z' },
  { id: 'h-old', status: 'FINISHED', homeTeamId: otherTeamId, awayTeamId: teamId, homeScore: 3, awayScore: 0, startedAt: '2026-09-01T12:00:00Z' },
  { id: matchId, status: 'FINISHED', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 0, awayScore: 0, startedAt: '2026-11-06T12:00:00Z' },
  { id: 'h-live', status: 'LIVE', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 0, awayScore: 0, startedAt: '2026-11-05T12:00:00Z' },
  { id: 'h-soon', status: 'SCHEDULED', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 0, awayScore: 0, scheduledAt: '2026-12-01T12:00:00Z' },
]

const awayRows = [
  { id: 'a-new', status: 'FINISHED', homeTeamId: otherTeamId, awayTeamId: teamId, homeScore: 0, awayScore: 2, startedAt: '2026-11-04T12:00:00Z' },
  { id: 'a-old', status: 'FINISHED', homeTeamId: otherTeamId, awayTeamId: '00000000-0000-4000-8000-0000000000f5', homeScore: 1, awayScore: 0, startedAt: '2026-08-01T12:00:00Z' },
  { id: matchId, status: 'LIVE', homeTeamId: teamId, awayTeamId: otherTeamId, homeScore: 0, awayScore: 0, startedAt: '2026-11-06T16:00:00Z' },
  { id: 'a-soon', status: 'SCHEDULED', homeTeamId: otherTeamId, awayTeamId: teamId, homeScore: 0, awayScore: 0, scheduledAt: '2026-12-02T12:00:00Z' },
]

async function stamp(page: Page) {
  await page.evaluate(() => {
    document.getElementById('fixture-stamp')?.remove()
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:10000;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);'
    document.body.appendChild(note)
  })
}

async function stubMatch(page: Page) {
  let turn = 0
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') {
      return json(route, { content: [
        { id: teamId, name: 'QA Толян Альфа', shortName: 'Альфа' },
        { id: otherTeamId, name: 'QA Толян Бета', shortName: 'Бета' },
      ] })
    }
    if (path === `/api/v1/matches/${matchId}`) return json(route, match)
    if (path === `/api/v1/matches/${matchId}/events`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/referees`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/lineups`) return json(route, {})
    if (path === `/api/v1/tournaments/${tournamentId}`) {
      return json(route, { id: tournamentId, name: 'QA Толян Кубок 2', status: 'ACTIVE' })
    }
    if (path.endsWith('/form')) {
      const delay = 400 + (turn % 3) * 150
      const payload = path.includes(teamId) && !path.includes(otherTeamId)
        ? rotate(homeRows, turn)
        : rotate(awayRows, turn + 1)
      turn += 1
      await new Promise((resolve) => setTimeout(resolve, delay))
      return json(route, payload)
    }
    return json(route, {})
  })
}

test('the same match shows the same form letters on every load', async ({ page }) => {
  test.setTimeout(60_000)
  mkdirSync(OUT, { recursive: true })
  await stubMatch(page)
  const home = page.locator('.form-row').nth(0).locator('.marks i')
  const away = page.locator('.form-row').nth(1).locator('.marks i')
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
    const seen: string[] = []
    for (let load = 0; load < 3; load += 1) {
      await page.goto(`/matches/${matchId}`)
      await expect(page.locator('.pitch')).toBeVisible()
      await expect(page.getByText('нет матчей')).toHaveCount(0)
      await expect(home).toHaveText(['В', 'Н', 'П'])
      await expect(away).toHaveText(['П', 'В'])
      seen.push(`${(await home.allTextContents()).join('')}|${(await away.allTextContents()).join('')}`)
    }
    expect(new Set(seen).size).toBe(1)
    await stamp(page)
    const card = page.locator('.rail .card', { has: page.getByText('последние 5') })
    await card.scrollIntoViewIfNeeded()
    await page.screenshot({ path: `${OUT}/qa5-form-${width}.png` })
  }
})

test('a successful match retry clears the update banner', async ({ page }) => {
  test.setTimeout(45_000)
  mkdirSync(OUT, { recursive: true })
  let release = false
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') return json(route, { content: [] })
    if (path === `/api/v1/matches/${matchId}`) {
      if (!release) return
      return json(route, match)
    }
    if (path === `/api/v1/matches/${matchId}/events`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/referees`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/lineups`) return json(route, {})
    if (path.endsWith('/form')) {
      return json(route, [{
        id: 'h-new',
        status: 'FINISHED',
        homeTeamId: teamId,
        awayTeamId: otherTeamId,
        homeScore: 2,
        awayScore: 0,
        startedAt: '2026-11-03T12:00:00Z',
      }])
    }
    return json(route, {})
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/matches/${matchId}`)
  const retry = page.getByRole('button', { name: 'Повторить' })
  await expect(retry).toBeVisible({ timeout: 20_000 })
  release = true
  await retry.click()
  await expect(page.getByRole('link', { name: 'QA Толян Альфа' })).toBeVisible()
  await expect(page.getByText('Матч не обновился.')).toHaveCount(0)
  await expect(page.getByText('Матч не загрузился')).toHaveCount(0)
  await expect(page.locator('.form-error')).toHaveCount(0)
  await page.waitForTimeout(4_500)
  await expect(page.getByText('Матч не обновился.')).toHaveCount(0)
  await expect(page.locator('.form-error')).toHaveCount(0)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/qa5-match-retry-cleared.png` })
})

const retryFormRow = {
  id: 'h-new',
  status: 'FINISHED',
  homeTeamId: teamId,
  awayTeamId: otherTeamId,
  homeScore: 2,
  awayScore: 0,
  startedAt: '2026-11-03T12:00:00Z',
}

test('a successful match retry does not leave the form loading', async ({ page }) => {
  test.setTimeout(20_000)
  let matchGets = 0
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') return json(route, { content: [] })
    if (path === `/api/v1/matches/${matchId}`) {
      matchGets += 1
      if (matchGets === 1) return json(route, { message: 'down' }, 500)
      return json(route, match)
    }
    if (path === `/api/v1/matches/${matchId}/events`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/referees`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/lineups`) return json(route, {})
    if (path.endsWith('/form')) return json(route, [retryFormRow])
    return json(route, {})
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/matches/${matchId}`)
  await expect(page.getByText('Матч не загрузился')).toBeVisible()
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByRole('link', { name: 'QA Толян Альфа' })).toBeVisible()
  const card = page.locator('.rail .card', { hasText: 'последние 5' })
  await expect(card.locator('.marks i')).toHaveText(['В', 'П'])
  await expect(card.getByText('Загрузка…')).toHaveCount(0)
})

test('the form loads when a failed retry is overtaken by the live match', async ({ page }) => {
  test.setTimeout(20_000)
  let matchGets = 0
  let releaseRetry = () => {}
  const retryGate = new Promise<void>((resolve) => { releaseRetry = resolve })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/teams') return json(route, { content: [] })
    if (path === `/api/v1/matches/${matchId}`) {
      matchGets += 1
      if (matchGets === 1) return json(route, { message: 'down' }, 500)
      if (matchGets === 2) {
        await retryGate
        return json(route, { message: 'down' }, 500)
      }
      return json(route, match)
    }
    if (path === `/api/v1/matches/${matchId}/events`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/referees`) return json(route, [])
    if (path === `/api/v1/matches/${matchId}/lineups`) return json(route, {})
    if (path.endsWith('/form')) return json(route, [retryFormRow])
    return json(route, {})
  })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/matches/${matchId}`)
  const retry = page.getByRole('button', { name: 'Повторить' })
  await expect(page.getByText('Матч не загрузился')).toBeVisible()
  await expect(retry).toBeVisible()
  await retry.click()
  await expect.poll(() => matchGets, { timeout: 8_000 }).toBeGreaterThanOrEqual(3)
  releaseRetry()
  await expect(page.getByRole('link', { name: 'QA Толян Альфа' })).toBeVisible()
  const card = page.locator('.rail .card', { hasText: 'последние 5' })
  await expect(card.locator('.marks i')).toHaveText(['В', 'П'])
  await expect(card.getByText('Загрузка…')).toHaveCount(0)
})
