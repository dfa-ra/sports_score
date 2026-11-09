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
    homeTeamName: 'Сборная севера',
    awayTeamId: `${id}-away`,
    awayTeamName: 'Сборная юга',
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

function tournament(id: string, name: string, lastMatch: ReturnType<typeof match>, status = 'ACTIVE') {
  return {
    id,
    name,
    shortName: name,
    status,
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
    tournament('cup-a', 'Осенний кубок факультетов', lastA, 'ACTIVE'),
    tournament('cup-b', 'Зимний кубок факультетов', lastB, 'REGISTRATION'),
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

test('home stacks through 720 CSS px and stays a row above it', async ({ page }) => {
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
    const chips = page.locator('.filters .chip')
    await expect(chips).toHaveText(['Все турниры', 'Осенний кубок факультетов'])
    await expect(page.locator('.tcard h3', { hasText: 'Зимний кубок факультетов' })).toBeVisible()

    const vs = hero.locator('.vs')
    const homeSide = vs.locator('.side').nth(0)
    const awaySide = vs.locator('.side').nth(1)
    const vsMark = vs.locator('.x')
    const homeBox = await homeSide.boundingBox()
    const awayBox = await awaySide.boundingBox()
    const markBox = await vsMark.boundingBox()
    const crestBox = await homeSide.locator('.crest').boundingBox()
    expect(homeBox && awayBox && markBox && crestBox).toBeTruthy()
    const homeMid = homeBox!.y + homeBox!.height / 2
    const awayMid = awayBox!.y + awayBox!.height / 2
    expect(Math.abs(homeMid - awayMid)).toBeLessThan(8)
    expect(awayBox!.y).toBeLessThan(homeBox!.y + homeBox!.height)
    expect(markBox!.x).toBeGreaterThan(homeBox!.x + 20)
    expect(awayBox!.x).toBeGreaterThan(markBox!.x)
    expect(Math.round(crestBox!.width)).toBe(28)
    expect(Math.round(crestBox!.height)).toBe(28)
    const nameStyle = await homeSide.locator('.team-name').evaluate((el) => {
      const style = getComputedStyle(el)
      return { fontSize: style.fontSize, whiteSpace: style.whiteSpace, textOverflow: style.textOverflow }
    })
    const vsStyle = await vsMark.evaluate((el) => {
      const style = getComputedStyle(el)
      return { fontSize: style.fontSize, color: style.color }
    })
    expect(vsStyle.fontSize).toBe('15px')
    expect(parseFloat(nameStyle.fontSize)).toBeGreaterThanOrEqual(14)
    expect(parseFloat(nameStyle.fontSize)).toBeLessThanOrEqual(parseFloat(vsStyle.fontSize) + 0.05)
    expect(vsStyle.color).toBe('rgb(76, 180, 229)')
    expect(nameStyle.whiteSpace).not.toBe('nowrap')
    expect(nameStyle.textOverflow).not.toBe('ellipsis')
    const nameBox = await homeSide.locator('.team-name').boundingBox()
    expect(nameBox!.width).toBeLessThan(homeBox!.width)
    expect(nameBox!.height).toBeLessThanOrEqual(parseFloat(nameStyle.fontSize) * 2.4)

    await page.screenshot({ path: `/opt/cursor/artifacts/home-${width}.png`, fullPage: true })
    if (width === 375) {
      await vs.screenshot({ path: '/opt/cursor/artifacts/home-hero-teams-375.png' })
    }
  }

  await page.setViewportSize({ width: 721, height: 900 })
  await page.goto('/')
  await expect(page.locator('.eyebrow', { hasText: 'Ближайший матч' })).toBeVisible()
  expect(await columns(page, '.hero-match')).toBe(2)
  expect(await columns(page, '.tgrid')).toBe(1)
  const actsBox = await page.locator('.hero-match .acts').boundingBox()
  const liveBox = await page.locator('.hero-match .livebar').boundingBox()
  expect(liveBox!.x).toBeGreaterThan(actsBox!.x + 40)
  const desktopCrest = await page.locator('.hero-match .vs .crest').first().boundingBox()
  const desktopName = await page.locator('.hero-match .vs .team-name').first().evaluate((el) => getComputedStyle(el).fontSize)
  const desktopVs = await page.locator('.hero-match .vs .x').evaluate((el) => getComputedStyle(el).fontSize)
  expect(Math.round(desktopCrest!.width)).toBe(56)
  expect(desktopVs).toBe('25.6px')
  expect(parseFloat(desktopName)).toBeGreaterThanOrEqual(15.2)
  expect(parseFloat(desktopName)).toBeLessThanOrEqual(parseFloat(desktopVs) + 0.05)
  const desktopHome = await page.locator('.hero-match .vs .side').nth(0).boundingBox()
  const desktopAway = await page.locator('.hero-match .vs .side').nth(1).boundingBox()
  const desktopHomeMid = desktopHome!.y + desktopHome!.height / 2
  const desktopAwayMid = desktopAway!.y + desktopAway!.height / 2
  expect(Math.abs(desktopHomeMid - desktopAwayMid)).toBeLessThan(8)
  expect(desktopAway!.x).toBeGreaterThan(desktopHome!.x + desktopHome!.width * 0.4)

  writeFileSync('/opt/cursor/artifacts/home-layout-scroll.json', JSON.stringify(scroll, null, 2))
})
