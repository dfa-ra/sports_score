import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

function match(partial: Record<string, unknown>) {
  return {
    id: 'm',
    tournamentId: 'liga',
    tournamentName: 'Лига ИТМО',
    tournamentShortName: 'Лига ИТМО',
    scheduledAt: '2026-11-06T12:00:00Z',
    status: 'LIVE',
    homeTeamId: 'h',
    homeTeamName: 'Хозяева',
    awayTeamId: 'a',
    awayTeamName: 'Гости',
    homeScore: 1,
    awayScore: 0,
    minute: 18,
    stageLabel: null,
    ...partial,
  }
}

const live = match({
  id: 'live-1',
  homeTeamName: 'Фикстура Хозяева',
  awayTeamName: 'Фикстура Гости',
  homeScore: 1,
  awayScore: 0,
  minute: 18,
  status: 'LIVE',
})
const paused = match({
  id: 'paused-1',
  homeTeamName: 'Фикстура Пауза А',
  awayTeamName: 'Фикстура Пауза Б',
  homeScore: 2,
  awayScore: 2,
  minute: 25,
  status: 'PAUSED',
})
const next = match({
  id: 'next-1',
  status: 'SCHEDULED',
  scheduledAt: '2099-06-01T16:30:00Z',
  minute: null,
  homeScore: 0,
  awayScore: 0,
  homeTeamName: 'Пумы',
  awayTeamName: 'Физтех',
})

const overview = {
  generatedAt: '2026-11-06T12:00:00Z',
  next,
  live: [paused, live],
  upcoming: [next],
  recent: [],
  tournaments: [{
    id: 'liga',
    name: 'Лига ИТМО',
    shortName: 'Лига ИТМО',
    status: 'ACTIVE',
    teamsCount: 8,
    lastMatch: null,
    nextMatch: next,
  }],
  scorersByTournament: [],
  summary: null,
}

async function open(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/home/overview') ? overview : { content: [] }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto('/')
  await expect(page.locator('.livebar')).toBeVisible()
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('a paused hero match shows Пауза after the live match and has no live pulse', async ({ page }) => {
  await open(page)
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 900 : 800 })
    const rows = page.locator('.livebar .lv')
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(0)).toContainText('Фикстура Хозяева')
    await expect(rows.nth(0)).toContainText('18′')
    await expect(rows.nth(0)).not.toContainText('Пауза')
    await expect(rows.nth(1)).toContainText('Фикстура Пауза А')
    await expect(rows.nth(1).locator('.st.paused')).toHaveText('Пауза')
    await expect(rows.nth(1)).not.toContainText('25')
    await expect(rows.nth(1).locator('.st.live')).toHaveCount(0)
    const pulse = await rows.nth(1).locator('.st.paused').evaluate((el) => getComputedStyle(el, '::before').content)
    expect(pulse).toBe('none')
    await expect(page.locator('.livebar > .st.live')).toBeVisible()
    await expect(page.getByText(/\d+\s*в игре/)).toHaveCount(0)
    await page.locator('.hero-card').screenshot({
      path: `${OUT}/home-live-paused-${width === 1440 ? 'desktop' : '375'}.png`,
    })
  }
})
