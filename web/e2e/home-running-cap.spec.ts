import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const viewports = [375, 1280] as const
const heroName = 'QA Толян Альфа'

function finished(id: string, tournamentId: string, tournamentName: string) {
  return {
    id,
    tournamentId,
    tournamentName,
    tournamentShortName: tournamentName,
    scheduledAt: '2026-11-01T14:00:00Z',
    status: 'FINISHED',
    homeTeamId: `${id}-home`,
    homeTeamName: 'Сборная севера',
    awayTeamId: `${id}-away`,
    awayTeamName: 'Сборная юга',
    homeScore: 1,
    awayScore: 0,
    minute: null,
    stageLabel: 'Тур 1',
    round: 1,
  }
}

function scheduled(id: string, tournamentId: string, tournamentName: string, scheduledAt: string) {
  return {
    ...finished(id, tournamentId, tournamentName),
    scheduledAt,
    status: 'SCHEDULED',
    homeScore: 0,
    awayScore: 0,
  }
}

const heroNext = scheduled('next-1', 'live-cup', 'Живой', '2026-12-05T14:00:00Z')

function card(
  id: string,
  name: string,
  status: string,
  nextAt: string | null,
) {
  return {
    id,
    name,
    shortName: name,
    status,
    seasonYear: 2026,
    seasonLabel: 'Сезон 2026',
    format: 'LEAGUE',
    formatTitle: 'Лига',
    teamsCount: 8,
    competitionStage: 'GROUP',
    stageLabel: status === 'REGISTRATION' ? 'Регистрация' : 'Тур 1 из 3',
    currentRound: 1,
    roundsTotal: 3,
    progress: status === 'REGISTRATION' ? null : 0.2,
    leader: null,
    playoffRoundTitle: null,
    playoffPlayedPairs: null,
    playoffTotalPairs: null,
    lastMatch: finished(`last-${id}`, id, name),
    nextMatch: nextAt ? scheduled(`next-${id}`, id, name, nextAt) : null,
  }
}

function liveMatch(tournamentId: string, tournamentName: string) {
  return {
    ...finished('live-now', tournamentId, tournamentName),
    status: 'LIVE',
    scheduledAt: '2026-11-03T12:00:00Z',
    minute: 12,
    homeScore: 1,
    awayScore: 0,
  }
}

function overview(tournaments: ReturnType<typeof card>[], liveTournamentId?: string) {
  const liveCard = tournaments.find((item) => item.id === liveTournamentId)
  return {
    generatedAt: '2026-11-03T12:00:00Z',
    next: heroNext,
    live: liveCard ? [liveMatch(liveCard.id, liveCard.name)] : [],
    upcoming: [heroNext],
    recent: [],
    tournaments,
    scorersByTournament: [],
    summary: { matchesPlayed: 2, goals: 1, goalsPerMatch: 0.5, teams: 4, players: 16 },
  }
}

function directory(total: number) {
  const content = Array.from({ length: Math.min(total, 50) }, (_, index) => ({
    id: `dir-${index + 1}`,
    name: `Каталог ${index + 1}`,
    status: index === 0 ? 'FINISHED' : 'ACTIVE',
    startDate: '2026-09-01',
  }))
  return { content, page: 0, size: 50, totalElements: total, totalPages: 1 }
}

async function mockApi(page: Page, tournaments: ReturnType<typeof card>[], total: number, liveTournamentId?: string) {
  const body = overview(tournaments, liveTournamentId)
  const catalog = directory(total)
  await page.unroute('**/api/v1/**').catch(() => undefined)
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const payload = path.endsWith('/home/overview')
      ? body
      : path.endsWith('/tournaments')
        ? catalog
        : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
  })
}

async function openHome(page: Page, width: number, height = 900) {
  await page.setViewportSize({ width, height })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Идут сейчас' })).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
}

function runningSection(page: Page) {
  return page.locator('.block').filter({ has: page.getByRole('heading', { name: 'Идут сейчас' }) })
}

async function expectNoHorizontalScroll(page: Page) {
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBe(metrics.clientWidth)
}

function nameLines(locator: Locator) {
  return locator.evaluate((el) => {
    const style = getComputedStyle(el)
    const node = [...el.childNodes].find((child) => child.nodeType === Node.TEXT_NODE && (child.textContent || '').trim())
    const raw = node?.textContent || ''
    const lines: string[] = []
    if (node) {
      const range = document.createRange()
      let start = 0
      for (let i = 1; i <= raw.length; i += 1) {
        range.setStart(node, start)
        range.setEnd(node, i)
        if (range.getClientRects().length > 1) {
          lines.push(raw.slice(start, i - 1))
          start = i - 1
        }
      }
      lines.push(raw.slice(start))
    }
    const rendered = lines.map((line) => line.trim()).filter(Boolean)
    const text = raw.replace(/\s+/g, ' ').trim()
    return {
      fontSize: Number.parseFloat(style.fontSize),
      min: 0.95 * Number.parseFloat(getComputedStyle(document.documentElement).fontSize),
      wordBreak: style.wordBreak,
      overflowWrap: style.overflowWrap,
      rendered,
      words: text.split(' ').filter(Boolean),
      flat: rendered.flatMap((line) => line.split(/\s+/).filter(Boolean)),
    }
  })
}

test('«Идут сейчас» keeps four cards, the new order, and the directory total', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })

  const three = [
    card('reg-1', 'Набор', 'REGISTRATION', null),
    card('active-late', 'Дальний', 'ACTIVE', '2026-12-20T14:00:00Z'),
    card('active-soon', 'Ближний', 'ACTIVE', '2026-11-05T14:00:00Z'),
  ]
  for (const width of viewports) {
    await mockApi(page, three, 10)
    await openHome(page, width)
    const section = runningSection(page)
    await expect(section.locator('.tcard h3')).toHaveText(['Ближний', 'Дальний', 'Набор'])
    await expect(section.getByRole('link', { name: /Все турниры \(\d+\)/ })).toHaveCount(0)
    await expect(section.locator('.section-title .more-link')).toHaveText('Все турниры')
    await expectNoHorizontalScroll(page)
  }

  const four = [
    ...three,
    card('live-cup', 'Живой', 'ACTIVE', '2026-11-08T14:00:00Z'),
  ]
  for (const width of viewports) {
    await mockApi(page, four, 12, 'live-cup')
    await openHome(page, width)
    const section = runningSection(page)
    await expect(section.locator('.tcard h3')).toHaveText(['Живой', 'Ближний', 'Дальний', 'Набор'])
    await expect(section.getByRole('link', { name: /Все турниры \(\d+\)/ })).toHaveCount(0)
    await expectNoHorizontalScroll(page)
  }

  const five = [
    card('reg-late', 'Набор поздний', 'REGISTRATION', null),
    card('active-late', 'Дальний', 'ACTIVE', '2026-12-20T14:00:00Z'),
    card('active-soon', 'Ближний', 'ACTIVE', '2026-11-05T14:00:00Z'),
    card('live-cup', 'Живой', 'ACTIVE', '2026-11-08T14:00:00Z'),
    card('reg-early', 'Набор ранний', 'REGISTRATION', null),
  ]
  for (const width of viewports) {
    await mockApi(page, five, 9, 'live-cup')
    await openHome(page, width)
    const section = runningSection(page)
    const cards = section.locator('.tcard')
    await expect(cards).toHaveCount(4)
    await expect(cards.locator('h3')).toHaveText(['Живой', 'Ближний', 'Дальний', 'Набор поздний'])
    await expect(section.getByRole('heading', { name: 'Набор ранний' })).toHaveCount(0)
    const more = section.getByRole('link', { name: 'Все турниры (9) →' })
    await expect(more).toBeVisible()
    await expect(more).toHaveClass(/hv-btn/)
    await expect(more).toHaveClass(/\bdark\b/)
    await expect(more).toHaveAttribute('href', '/tournaments')
    await expect(section.locator('.section-title .more-link')).toHaveText('Все турниры')
    const cardBox = await cards.last().boundingBox()
    const buttonBox = await more.boundingBox()
    expect(cardBox && buttonBox).toBeTruthy()
    expect(buttonBox!.y).toBeGreaterThan(cardBox!.y + cardBox!.height - 1)
    expect(buttonBox!.x).toBeGreaterThanOrEqual(0)
    expect(buttonBox!.x + buttonBox!.width).toBeLessThanOrEqual(width + 1)
    await expectNoHorizontalScroll(page)
    if (width === 375 || width === 1280) {
      await section.screenshot({ path: `/opt/cursor/artifacts/home-running-5-${width}.png` })
    }
  }

  const tied = [
    card('active-b', 'Ровесник Б', 'ACTIVE', '2026-11-15T14:00:00Z'),
    card('active-a', 'Ровесник А', 'ACTIVE', '2026-11-15T14:00:00Z'),
    card('active-open', 'Без даты', 'ACTIVE', null),
    card('live-cup', 'Живой', 'ACTIVE', '2026-11-08T14:00:00Z'),
    card('reg-1', 'Набор', 'REGISTRATION', null),
  ]
  await mockApi(page, tied, 9, 'live-cup')
  await openHome(page, 1280)
  await expect(runningSection(page).locator('.tcard h3')).toHaveText(['Живой', 'Ровесник Б', 'Ровесник А', 'Без даты'])

  await page.getByRole('link', { name: 'Все турниры (9) →' }).click()
  await expect(page).toHaveURL(/\/tournaments$/)
  await expect(page.getByRole('heading', { name: 'Турниры' })).toBeVisible()
})

test('hero team name stays on two lines at 768 and 375', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  const tournaments = [card('live-cup', 'Живой', 'ACTIVE', '2026-11-08T14:00:00Z')]
  const body = overview(tournaments, 'live-cup')
  body.next = {
    ...heroNext,
    homeTeamName: heroName,
    awayTeamName: 'Сборная юга',
  }
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const payload = path.endsWith('/home/overview') ? body : path.endsWith('/tournaments') ? directory(3) : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
  })

  for (const width of [768, 375] as const) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await expect(page.locator('.hero-match .team-name').first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const name = page.locator('.hero-match .team-name', { hasText: heroName }).first()
    await expect(name).toBeVisible()
    const info = await nameLines(name)
    expect(info.wordBreak).toBe('normal')
    expect(info.overflowWrap).toBe('break-word')
    expect(info.fontSize).toBeGreaterThanOrEqual(info.min - 0.2)
    expect(info.flat).toEqual(info.words)
    expect(info.rendered.length).toBeGreaterThan(0)
    expect(info.rendered.length).toBeLessThanOrEqual(2)
    for (const line of info.rendered) expect(line).not.toMatch(/^\p{L}$/u)
    await expectNoHorizontalScroll(page)
    if (width === 768) {
      await page.locator('.hero-match').screenshot({ path: '/opt/cursor/artifacts/home-hero-names-768.png' })
    }
  }
})
