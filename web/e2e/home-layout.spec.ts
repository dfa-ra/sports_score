import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'

const widths = [375, 512, 719] as const

function match(id: string, tournamentId: string, tournamentName: string, status: string, scheduledAt: string) {
  return {
    id,
    tournamentId,
    tournamentName,
    tournamentShortName: tournamentName,
    scheduledAt,
    status,
    homeTeamId: `${id}-home`,
    homeTeamName: 'Северная сборная факультета',
    awayTeamId: `${id}-away`,
    awayTeamName: 'Южная сборная факультета',
    homeScore: 0,
    awayScore: 0,
    minute: status === 'LIVE' ? 12 : null,
    stageLabel: '1/2 финала',
    round: 1,
  }
}

const next = match('next-1', 'cup-a', 'Осенний кубок факультетов', 'SCHEDULED', '2026-12-05T14:00:00Z')
const live = match('live-1', 'cup-b', 'Зимний кубок факультетов', 'LIVE', '2026-11-03T12:00:00Z')
const lastA = match('last-a', 'cup-a', 'Осенний кубок факультетов', 'FINISHED', '2026-11-01T14:00:00Z')
const lastB = match('last-b', 'cup-b', 'Зимний кубок факультетов', 'FINISHED', '2026-11-02T14:00:00Z')

function tournament(id: string, name: string, lastMatch: ReturnType<typeof match>) {
  return {
    id,
    name,
    shortName: name,
    status: 'ACTIVE',
    seasonYear: 2026,
    seasonLabel: 'Сезон 2026',
    format: 'CUP',
    formatTitle: 'Кубок',
    teamsCount: 8,
    competitionStage: 'PLAYOFF',
    stageLabel: 'Плей-офф · 1/2 финала',
    currentRound: 1,
    roundsTotal: 3,
    progress: 0.25,
    leader: null,
    playoffRoundTitle: '1/2 финала',
    playoffPlayedPairs: 0,
    playoffTotalPairs: 4,
    lastMatch,
    nextMatch: id === 'cup-a' ? next : null,
  }
}

const overview = {
  generatedAt: '2026-11-03T12:00:00Z',
  next,
  live: [live],
  upcoming: [next],
  recent: [lastA, lastB],
  tournaments: [
    tournament('cup-a', 'Осенний кубок факультетов', lastA),
    tournament('cup-b', 'Зимний кубок факультетов', lastB),
  ],
  scorersByTournament: [],
  summary: { matchesPlayed: 2, goals: 0, goalsPerMatch: 0, teams: 4, players: 16 },
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/home/overview') ? overview : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

async function columns(page: Page, selector: string) {
  return page.locator(selector).evaluate((el) =>
    getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/).filter(Boolean).length,
  )
}

test('home stacks at 719 CSS px and stays a row at 720', async ({ page }) => {
  await mockApi(page)
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  const scroll: Record<string, { scrollWidth: number; clientWidth: number; heroColumns: number; cardColumns: number }> = {}

  for (const width of widths) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await expect(page.locator('.eyebrow', { hasText: 'Ближайший матч' })).toBeVisible()
    const hero = page.locator('.hero-match')
    const acts = hero.locator('.acts')
    const livebar = hero.locator('.livebar')
    await expect(hero).toBeVisible()
    await expect(livebar).toBeVisible()
    expect(await columns(page, '.hero-match')).toBe(1)
    expect(await columns(page, '.tgrid')).toBe(1)

    const actsBox = await acts.boundingBox()
    const liveBox = await livebar.boundingBox()
    expect(actsBox).toBeTruthy()
    expect(liveBox).toBeTruthy()
    expect(liveBox!.y).toBeGreaterThanOrEqual(actsBox!.y + actsBox!.height - 1)
    expect(liveBox!.x).toBeLessThan(actsBox!.x + actsBox!.width)

    const line = page.locator('.tcard .irow .sc .line').first()
    await expect(line).toBeVisible()
    const parts = await line.locator('span').evaluateAll((nodes) =>
      nodes.map((node) => ({
        text: node.textContent || '',
        top: node.getBoundingClientRect().top,
        left: node.getBoundingClientRect().left,
      })),
    )
    expect(parts.map((part) => part.text).join(' ')).toBe('0 : 0')
    expect(Math.abs(parts[0].top - parts[1].top)).toBeLessThan(2)
    expect(Math.abs(parts[1].top - parts[2].top)).toBeLessThan(2)
    expect(parts[1].left).toBeGreaterThan(parts[0].left)
    expect(parts[2].left).toBeGreaterThan(parts[1].left)

    const metrics = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }))
    expect(metrics.scrollWidth).toBe(metrics.clientWidth)
    scroll[String(width)] = {
      ...metrics,
      heroColumns: await columns(page, '.hero-match'),
      cardColumns: await columns(page, '.tgrid'),
    }
    await page.screenshot({ path: `/opt/cursor/artifacts/home-${width}.png`, fullPage: true })
  }

  await page.setViewportSize({ width: 720, height: 900 })
  await page.goto('/')
  await expect(page.locator('.eyebrow', { hasText: 'Ближайший матч' })).toBeVisible()
  expect(await columns(page, '.hero-match')).toBe(2)
  expect(await columns(page, '.tgrid')).toBe(2)
  const actsBox = await page.locator('.hero-match .acts').boundingBox()
  const liveBox = await page.locator('.hero-match .livebar').boundingBox()
  expect(liveBox!.x).toBeGreaterThan(actsBox!.x + 40)

  writeFileSync('/opt/cursor/artifacts/home-layout-scroll.json', JSON.stringify(scroll, null, 2))
})
