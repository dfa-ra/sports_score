import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const PAGE_SIZE = 20

type Team = { id: string; name: string; shortName: string }

const catalog: Team[] = [
  ...Array.from({ length: 24 }, (_, index) => ({
    id: `club-${index}`,
    name: `Клуб ${String(index + 1).padStart(2, '0')}`,
    shortName: `К${index + 1}`,
  })),
  { id: 'yak', name: 'Якоря Юга', shortName: 'ЯЮ' },
]

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function normalize(raw: string) {
  return raw.replace(/\s+/g, ' ').trim().toLocaleLowerCase('ru')
}

function matches(team: Team, q: string) {
  const needle = normalize(q)
  if (!needle) return true
  return team.name.toLocaleLowerCase('ru').includes(needle)
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

test('team search finds a name past page 1, pages at a fixed size, and clears back', async ({ page }) => {
  const calls: { page: number; size: number; q: string }[] = []
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path !== '/api/v1/teams') return json(route, { content: [] })
    const requested = Number(url.searchParams.get('size') || '20')
    const size = Math.min(500, requested)
    const pageNumber = Number(url.searchParams.get('page') || '0')
    const q = url.searchParams.get('q') || ''
    calls.push({ page: pageNumber, size: requested, q })
    const filtered = catalog
      .filter((team) => matches(team, q))
      .sort((a, b) => a.name.localeCompare(b.name, 'ru'))
    const start = pageNumber * size
    return json(route, {
      content: filtered.slice(start, start + size),
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      page: pageNumber,
      size,
    })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/teams')
  await expect(page.getByRole('heading', { name: 'Команды', exact: true })).toBeVisible()
  await expect(page.locator('.card-link')).toHaveCount(PAGE_SIZE)
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Показать ещё' })).toBeVisible()
  expect(calls[0]).toEqual({ page: 0, size: PAGE_SIZE, q: '' })

  await page.getByLabel('Поиск команды').fill('  ЯКОРЯ   юга ')
  await expect(page.getByText('Найдено · 1')).toBeVisible()
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toBeVisible()
  await expect(page.getByText('Команд с таким названием нет')).toHaveCount(0)
  await expect(page.getByText('Команды не загрузились')).toHaveCount(0)
  const searchCall = calls.find((call) => call.q === 'ЯКОРЯ юга')
  expect(searchCall).toEqual({ page: 0, size: PAGE_SIZE, q: 'ЯКОРЯ юга' })
  await noHorizontalScroll(page)
  await shot(page, 'q2-teams-search-1440')

  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2-teams-search-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByLabel('Поиск команды').fill('клуб')
  await expect(page.getByText('Найдено · 24')).toBeVisible()
  await expect(page.locator('.card-link')).toHaveCount(PAGE_SIZE)
  await expect(page.getByRole('link', { name: /Клуб 21/ })).toHaveCount(0)
  await page.getByRole('button', { name: 'Показать ещё' }).click()
  await expect(page.locator('.card-link')).toHaveCount(24)
  await expect(page.getByRole('link', { name: /Клуб 21/ })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Показать ещё' })).toHaveCount(0)
  const clubCalls = calls.filter((call) => call.q === 'клуб')
  expect(clubCalls.map((call) => `${call.page}:${call.size}`)).toEqual(['0:20', '1:20'])
  expect(clubCalls.every((call) => call.size === PAGE_SIZE)).toBe(true)

  await page.getByRole('button', { name: 'Очистить' }).click()
  await expect(page.getByText('Найдено · 24')).toHaveCount(0)
  await expect(page.locator('.card-link')).toHaveCount(PAGE_SIZE)
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toHaveCount(0)
  await expect(page.getByRole('link', { name: /Клуб 01/ })).toBeVisible()
  const restored = calls.filter((call) => call.q === '')
  expect(restored.at(-1)).toEqual({ page: 0, size: PAGE_SIZE, q: '' })

  await page.getByRole('button', { name: 'Показать ещё' }).click()
  await expect(page.locator('.card-link')).toHaveCount(25)
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toBeVisible()
  const unfiltered = calls.filter((call) => call.q === '')
  expect(unfiltered.map((call) => `${call.page}:${call.size}`)).toEqual(['0:20', '0:20', '1:20'])
})

test('empty team search is not an error, a failed request can be retried, and a stale page is ignored', async ({ page }) => {
  let mode: 'ready' | 'fail' = 'ready'
  let releaseSlow: () => void = () => {}
  const slow = new Promise<void>((resolve) => {
    releaseSlow = resolve
  })
  const calls: string[] = []
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path !== '/api/v1/teams') return json(route, { content: [] })
    const q = url.searchParams.get('q') || ''
    calls.push(q)
    if (q === 'Альфа') await slow
    if (mode === 'fail' && q === 'Якоря') return json(route, { message: 'down' }, 500)
    const filtered = catalog.filter((team) => matches(team, q))
    const pageNumber = Number(url.searchParams.get('page') || '0')
    const size = Math.min(500, Number(url.searchParams.get('size') || '20'))
    const start = pageNumber * size
    return json(route, {
      content: filtered.slice(start, start + size),
      totalElements: filtered.length,
      page: pageNumber,
      size,
    })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/teams')
  await expect(page.locator('.card-link').first()).toBeVisible()

  await page.getByLabel('Поиск команды').fill('Неттакой')
  await expect(page.getByText('Команд с таким названием нет')).toBeVisible()
  await expect(page.getByText('Команды не загрузились')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Повторить' })).toHaveCount(0)
  await shot(page, 'q2-teams-search-empty-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await noHorizontalScroll(page)
  await shot(page, 'q2-teams-search-empty-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  mode = 'fail'
  await page.getByLabel('Поиск команды').fill('Якоря')
  await expect(page.getByText('Команды не загрузились')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible()
  await expect(page.getByText('Команд с таким названием нет')).toHaveCount(0)
  mode = 'ready'
  await page.getByRole('button', { name: 'Повторить' }).click()
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toBeVisible()
  await expect(page.getByText('Команды не загрузились')).toHaveCount(0)

  await page.getByLabel('Поиск команды').fill('Альфа')
  await expect.poll(() => calls.includes('Альфа')).toBe(true)
  await page.getByLabel('Поиск команды').fill('Якоря Юга')
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toBeVisible()
  releaseSlow()
  await page.waitForTimeout(300)
  await expect(page.getByRole('link', { name: /Якоря Юга/ })).toBeVisible()
  await expect(page.getByRole('link', { name: /Клуб 01/ })).toHaveCount(0)
  await expect(page.getByText('Найдено · 1')).toBeVisible()
})
