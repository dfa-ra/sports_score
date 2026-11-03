import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const viewports = [375, 1280] as const
const counts = [3, 4, 5, 7] as const

function match(id: string, tournamentId: string, tournamentName: string) {
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

const next = {
  id: 'next-1',
  tournamentId: 'cup-1',
  tournamentName: 'Турнир 1',
  tournamentShortName: 'Турнир 1',
  scheduledAt: '2026-12-05T14:00:00Z',
  status: 'SCHEDULED',
  homeTeamId: 'next-home',
  homeTeamName: 'Сборная севера',
  awayTeamId: 'next-away',
  awayTeamName: 'Сборная юга',
  homeScore: 0,
  awayScore: 0,
  minute: null,
  stageLabel: 'Тур 2',
  round: 2,
}

function tournament(index: number) {
  const name = `Турнир ${index}`
  const id = `cup-${index}`
  return {
    id,
    name,
    shortName: name,
    status: index % 2 === 0 ? 'REGISTRATION' : 'ACTIVE',
    seasonYear: 2026,
    seasonLabel: 'Сезон 2026',
    format: 'LEAGUE',
    formatTitle: 'Лига',
    teamsCount: 8,
    competitionStage: 'GROUP',
    stageLabel: 'Тур 1 из 3',
    currentRound: 1,
    roundsTotal: 3,
    progress: 0.2,
    leader: null,
    playoffRoundTitle: null,
    playoffPlayedPairs: null,
    playoffTotalPairs: null,
    lastMatch: match(`last-${index}`, id, name),
    nextMatch: index === 1 ? next : null,
  }
}

function overview(count: number) {
  return {
    generatedAt: '2026-11-03T12:00:00Z',
    next,
    live: [],
    upcoming: [next],
    recent: [],
    tournaments: Array.from({ length: count }, (_, index) => tournament(index + 1)),
    scorersByTournament: [],
    summary: { matchesPlayed: count, goals: count, goalsPerMatch: 1, teams: count * 2, players: count * 8 },
  }
}

async function mockApi(page: Page, count: number) {
  const body = overview(count)
  await page.unroute('**/api/v1/**').catch(() => undefined)
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const payload = path.endsWith('/home/overview') ? body : path.endsWith('/tournaments') ? { content: [] } : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
  })
}

async function openHome(page: Page, width: number) {
  await page.setViewportSize({ width, height: 900 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Идут сейчас' })).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
}

test('«Идут сейчас» shows at most four tournaments and a total link only past the cap', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })

  for (const count of counts) {
    await mockApi(page, count)
    for (const width of viewports) {
      await openHome(page, width)
      const section = page.locator('.block').filter({ has: page.getByRole('heading', { name: 'Идут сейчас' }) })
      const cards = section.locator('.tcard')
      const shown = Math.min(count, 4)
      await expect(cards).toHaveCount(shown)
      await expect(cards.locator('h3')).toHaveText(Array.from({ length: shown }, (_, index) => `Турнир ${index + 1}`))
      for (let index = shown + 1; index <= count; index += 1) {
        await expect(section.getByRole('heading', { name: `Турнир ${index}`, exact: true })).toHaveCount(0)
      }

      const header = section.locator('.section-title .more-link')
      await expect(header).toHaveText('Все турниры')
      await expect(header).toHaveAttribute('href', '/tournaments')

      const more = section.getByRole('link', { name: `Все турниры (${count}) →` })
      if (count <= 4) {
        await expect(section.getByRole('link', { name: /Все турниры \(\d+\)/ })).toHaveCount(0)
      } else {
        await expect(more).toBeVisible()
        await expect(more).toHaveClass(/hv-btn/)
        await expect(more).toHaveClass(/\bdark\b/)
        await expect(more).toHaveAttribute('href', '/tournaments')
        const cardBox = await cards.last().boundingBox()
        const buttonBox = await more.boundingBox()
        expect(cardBox && buttonBox).toBeTruthy()
        expect(buttonBox!.y).toBeGreaterThan(cardBox!.y + cardBox!.height - 1)
        expect(buttonBox!.x).toBeGreaterThanOrEqual(0)
        expect(buttonBox!.x + buttonBox!.width).toBeLessThanOrEqual(width + 1)
      }

      const metrics = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }))
      expect(metrics.scrollWidth).toBe(metrics.clientWidth)

      if (count === 5) {
        await section.screenshot({ path: `/opt/cursor/artifacts/home-running-5-${width}.png` })
      }
    }
  }

  await page.getByRole('link', { name: 'Все турниры (7) →' }).click()
  await expect(page).toHaveURL(/\/tournaments$/)
  await expect(page.getByRole('heading', { name: 'Турниры' })).toBeVisible()
})
