import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

function tournament(index: number) {
  const name = `Кубок факультета программной инженерии ${index}`
  const status = index <= 9 ? 'ACTIVE' : index <= 11 ? 'REGISTRATION' : 'FINISHED'
  return {
    id: `tour-${index}`,
    name,
    shortName: name,
    status,
    seasonYear: 2026,
    seasonLabel: 'Сезон 2026',
    format: 'CUP',
    formatTitle: 'Кубок',
    teamsCount: 8,
    competitionStage: 'PLAYOFF',
    stageLabel: 'Плей-офф · 1/4 финала',
    currentRound: 1,
    roundsTotal: 3,
    progress: 0.25,
    leader: null,
    playoffRoundTitle: '1/4 финала',
    startDate: '2026-09-01',
    endDate: '2026-12-20',
  }
}

const tournaments = Array.from({ length: 13 }, (_, index) => tournament(index + 1))
const scorers = tournaments.slice(0, 9).map((row) => ({
  tournamentId: row.id,
  tournamentName: row.name,
  shortName: row.shortName,
  scorers: [
    {
      playerId: `player-${row.id}`,
      displayName: 'Иван Иванов',
      firstName: 'Иван',
      lastName: 'Иванов',
      goals: 3,
      assists: 1,
      appearances: 2,
      teamId: 'team-north',
      teamName: 'Север',
      avatarUrl: null,
    },
  ],
}))

const overview = {
  generatedAt: '2026-11-03T12:00:00Z',
  next: null,
  live: [],
  upcoming: [],
  recent: [],
  tournaments,
  scorersByTournament: scorers,
  summary: { matchesPlayed: 12, goals: 30, goalsPerMatch: 2.5, teams: 40, players: 180 },
}

async function mockHome(page: Page) {
  await page.addInitScript(() => {
    const tasks: number[] = []
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) tasks.push(entry.duration)
    }).observe({ type: 'longtask', buffered: true })
    ;(window as unknown as { __blocking?: number[] }).__blocking = tasks
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/refresh') || path.endsWith('/auth/me')) {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
      return
    }
    const body = path.endsWith('/home/overview')
      ? overview
      : path.endsWith('/tournaments')
        ? { content: tournaments, totalElements: tournaments.length, totalPages: 1 }
        : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

for (const width of [375, 320]) {
  test(`home scorer tabs stay one row at ${width}px without a long task`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await mockHome(page)
    const started = Date.now()
    await page.goto('/', { waitUntil: 'load' })
    const tabs = page.locator('.tabs')
    await expect(tabs.locator('button')).toHaveCount(9)
    await page.evaluate(async () => {
      await document.fonts.ready
      await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve(null))))
    })
    const metrics = await page.evaluate(() => {
      const nav = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
      const tasks = (window as unknown as { __blocking?: number[] }).__blocking || []
      return {
        load: nav?.loadEventEnd ?? 0,
        blocking: tasks.reduce((sum, duration) => sum + duration, 0),
      }
    })
    expect(metrics.load).toBeLessThan(3000)
    expect(metrics.blocking).toBeLessThan(200)
    expect(Date.now() - started).toBeLessThan(3000)

    const row = await tabs.evaluate((node) => {
      const buttons = [...node.querySelectorAll('button')]
      const tops = buttons.map((button) => button.getBoundingClientRect().top)
      const labels = buttons.map((button) => button.querySelector('.tab-label')?.textContent || '')
      const active = node.querySelector('button.on')
      const host = node.getBoundingClientRect()
      const box = active?.getBoundingClientRect()
      return {
        height: node.clientHeight,
        scrolls: node.scrollWidth > node.clientWidth + 1,
        topSpread: Math.max(...tops) - Math.min(...tops),
        labels,
        activeInside: !!box && box.left >= host.left - 1 && box.right <= host.right + 1,
        fade: node.parentElement?.classList.contains('show-right') || false,
      }
    })
    expect(row.height).toBeLessThanOrEqual(48)
    expect(row.scrolls).toBe(true)
    expect(row.topSpread).toBeLessThan(2)
    expect(row.labels.some((label) => label.includes('…'))).toBe(true)
    expect(row.labels.every((label) => label.length > 0)).toBe(true)
    expect(row.activeInside).toBe(true)
    expect(row.fade).toBe(true)

    mkdirSync(OUT, { recursive: true })
    await tabs.scrollIntoViewIfNeeded()
    await page.screenshot({ path: `${OUT}/home-tabs-${width}.png` })
  })
}
