import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const tournamentId = 'liga-tabs'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function installApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/me') || path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/teams') && !path.includes('/tournaments/')) return json(route, { content: [], totalPages: 1 })
    if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
      return json(route, {
        name: 'Лига ИТМО',
        format: 'GROUPS_PLAYOFF',
        stage: 'GROUPS',
        hasPlayoff: true,
        teamsCount: 8,
        matchesCount: 24,
        seasonYear: 2026,
        playoffLive: false,
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) {
      return json(route, { competitionStage: 'GROUPS', rounds: [{ round: 1, title: 'Финал', slots: [] }] })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) {
      return json(route, [
        { teamId: 'ft', teamName: 'Физтех', status: 'APPROVED' },
        { teamId: 'ur', teamName: 'Юристы', status: 'APPROVED' },
      ])
    }
    if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return json(route, [])
    if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return json(route, { content: [], totalPages: 1 })
    if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return json(route, [])
    if (path.endsWith('/statistics/summary')) return json(route, { matchesPlayed: 4, goals: 12, goalsPerMatch: 3 })
    if (path.endsWith('/statistics/board')) return json(route, { scorers: [], assists: [] })
    if (path.endsWith(`/tournaments/${tournamentId}`)) {
      return json(route, {
        id: tournamentId,
        name: 'Лига ИТМО',
        status: 'ACTIVE',
        format: 'GROUPS_PLAYOFF',
        competitionStage: 'GROUPS',
        seasonYear: 2026,
        regulations: 'Играем два тайма.',
      })
    }
    return json(route, { content: [] })
  })
}

async function measure(page: Page) {
  return page.locator('.ttabs').evaluate((bar) => {
    const buttons = [...bar.querySelectorAll('button')].filter((node): node is HTMLElement => node instanceof HTMLElement)
    const host = bar.getBoundingClientRect()
    const active = bar.querySelector('button.on')
    const box = active instanceof HTMLElement ? active.getBoundingClientRect() : null
    const style = getComputedStyle(bar)
    const buttonStyle = buttons[0] ? getComputedStyle(buttons[0]) : null
    return {
      labels: buttons.map((button) => (button.innerText || '').replace(/\s+/g, ' ').trim()),
      tops: buttons.map((button) => Math.round(button.getBoundingClientRect().top * 100) / 100),
      height: Math.round(host.height * 100) / 100,
      scrollWidth: bar.scrollWidth,
      clientWidth: bar.clientWidth,
      scrollLeft: bar.scrollLeft,
      canScroll: bar.classList.contains('can-scroll'),
      mask: style.maskImage,
      padding: buttonStyle ? `${buttonStyle.paddingTop} ${buttonStyle.paddingRight} ${buttonStyle.paddingBottom} ${buttonStyle.paddingLeft}` : '',
      fontSize: buttonStyle?.fontSize || '',
      fontWeight: buttonStyle?.fontWeight || '',
      pageScrollWidth: document.documentElement.scrollWidth,
      pageClientWidth: document.documentElement.clientWidth,
      innerWidth: window.innerWidth,
      activeText: active instanceof HTMLElement ? (active.innerText || '').replace(/\s+/g, ' ').trim() : '',
      activeInView: !!box && box.width > 0 && box.left >= host.left - 1 && box.right <= host.right + 1,
    }
  })
}

async function openTournament(page: Page, width: number) {
  await page.setViewportSize({ width, height: 900 })
  await page.goto(`/tournaments/${tournamentId}`)
  await expect(page.locator('.ttabs button')).toHaveCount(5)
  await page.evaluate(() => document.fonts.ready)
  await expect(page.locator('.ttabs button.on')).toHaveCount(1)
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

for (const width of [320, 375] as const) {
  test(`tournament tabs stay on one 44px row at ${width}`, async ({ page }) => {
    await installApi(page)
    await openTournament(page, width)
    const resting = await measure(page)
    console.log(`tabs-${width}`, JSON.stringify(resting))
    expect(new Set(resting.tops).size, resting.labels.join(' | ')).toBe(1)
    expect(resting.height).toBe(44)
    expect(resting.scrollWidth).toBeGreaterThan(resting.clientWidth)
    expect(resting.pageScrollWidth).toBe(resting.innerWidth)
    expect(resting.canScroll).toBe(true)
    expect(resting.mask).toContain('linear-gradient')
    expect(resting.activeInView).toBe(true)
    await page.locator('.tab-card').screenshot({ path: `${OUT}/tournament-tabs-${width}.png` })

    await page.getByRole('tab', { name: 'Регламент' }).click()
    await expect(page.getByRole('tab', { name: 'Регламент' })).toHaveAttribute('aria-selected', 'true')
    await expect.poll(async () => (await measure(page)).activeInView).toBe(true)
    const active = await measure(page)
    console.log(`tabs-${width}-rules`, JSON.stringify(active))
    expect(active.activeText.toLowerCase()).toContain('регламент')
    expect(active.scrollLeft).toBeGreaterThan(0)
    expect(new Set(active.tops).size).toBe(1)
    expect(active.height).toBe(44)
    expect(active.scrollWidth).toBeGreaterThan(active.clientWidth)
    expect(active.pageScrollWidth).toBe(active.innerWidth)
  })
}

test('tournament tabs at 1440 keep the desktop row', async ({ page }) => {
  await installApi(page)
  await openTournament(page, 1440)
  const resting = await measure(page)
  console.log('tabs-1440', JSON.stringify(resting))
  expect(new Set(resting.tops).size, resting.labels.join(' | ')).toBe(1)
  expect(resting.scrollWidth).toBe(resting.clientWidth)
  expect(resting.pageScrollWidth).toBe(resting.innerWidth)
  expect(resting.canScroll).toBe(false)
  expect(resting.height).not.toBe(44)
  expect(resting.padding).toBe('12.8px 16px 12.8px 16px')
  expect(resting.fontSize).toBe('13px')
  expect(resting.fontWeight).toBe('800')
  expect(resting.activeInView).toBe(true)
  await page.locator('.tab-card').screenshot({ path: `${OUT}/tournament-tabs-1440.png` })
})
