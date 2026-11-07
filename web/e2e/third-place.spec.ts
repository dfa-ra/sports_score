import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const tournamentId = '00000000-0000-4000-8000-000000000113'

function pair(id: string, round: number, slotIndex: number, home: string, away: string, status: string) {
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
    homeScore: status === 'FINISHED' ? 2 : 0,
    awayScore: status === 'FINISHED' ? 0 : 0,
    scheduledAt: status === 'FINISHED' ? '2026-11-01T16:00:00Z' : null,
    venue: status === 'FINISHED' ? 'Зал' : null,
  }
}

function bracket() {
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
      slots: [
        pair('final', 3, 0, 'Альфа', 'Эпсилон', 'SCHEDULED'),
        {
          id: 'bronze',
          round: 3,
          slotIndex: 1,
          kind: 'THIRD',
          roundTitle: 'Матч за 3-е место',
          matchId: null,
          homeTeamId: null,
          homeTeamName: null,
          awayTeamId: null,
          awayTeamName: null,
          matchStatus: 'SCHEDULED',
          homeScore: 0,
          awayScore: 0,
          scheduledAt: null,
          venue: null,
        },
      ],
    },
  ]
}

async function expectNavyHeader(page: Page) {
  const bronzeBg = await page.locator('.fh.bronze').first().evaluate((el) => getComputedStyle(el).backgroundColor)
  const finalBg = await page.locator('.pc.final .fh').first().evaluate((el) => getComputedStyle(el).backgroundColor)
  expect(bronzeBg).toBe(finalBg)
  expect(bronzeBg).not.toMatch(/138,\s*90,\s*43/)
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/v1/teams') return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
      return json({
        name: 'Кубок',
        format: 'GROUPS_PLAYOFF',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: 8,
        matchesCount: 6,
        seasonYear: 2026,
        playoffLive: false,
        status: 'ACTIVE',
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) {
      return json({ competitionStage: 'PLAYOFF', rounds: bracket() })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}`)) {
      return json({
        id: tournamentId,
        name: 'Кубок',
        status: 'ACTIVE',
        format: 'GROUPS_PLAYOFF',
        competitionStage: 'PLAYOFF',
        seasonYear: 2026,
        regulations: '',
      })
    }
    return json({})
  })
}

test('third-place slot is its own phone round between the semis and the final', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await page.setViewportSize({ width: 375, height: 812 })
  await mockApi(page)
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  const rounds = page.getByRole('tablist', { name: 'Раунды' })
  const labels = (await rounds.getByRole('tab').allInnerTexts()).map((text) => text.replace(/\s+/g, ' ').trim().toLocaleLowerCase('ru'))
  expect(labels).toEqual(['1/4', '1/2', 'за 3-е', 'финал'])
  await rounds.getByRole('tab', { name: /^финал$/i }).click()
  const finalPanel = page.locator('.po-panel.on')
  await expect(finalPanel.getByRole('heading', { name: 'Финал', exact: true })).toBeVisible()
  await expect(finalPanel.getByText('Чемпион', { exact: true })).toBeVisible()
  await expect(finalPanel.getByText('Матч за 3-е место', { exact: true })).toHaveCount(0)
  await rounds.getByRole('tab', { name: /за 3-е/i }).click()
  const bronzePanel = page.locator('.po-panel.on')
  await expect(bronzePanel.getByRole('heading', { name: 'Матч за 3-е место', exact: true })).toBeVisible()
  await expect(bronzePanel.getByText('Проигравший 1/2 №1', { exact: true })).toBeVisible()
  await expect(bronzePanel.getByText('Проигравший 1/2 №2', { exact: true })).toBeVisible()
  await expect(bronzePanel.getByRole('heading', { name: 'Финал', exact: true })).toHaveCount(0)
  await expect(rounds.getByRole('tab', { name: /за 3-е/i })).toHaveAttribute('aria-selected', 'true')
  await expectNavyHeader(page)
  await page.locator('.po-card').screenshot({ path: '/opt/cursor/artifacts/third-place-375.png' })
})

test('third-place card sits under the semifinals and left of the final', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page)
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '1/4 финала' }).first()).toBeVisible()
  await expect(page.locator('.bronze-board')).toBeVisible()
  const bronze = page.locator('[data-bronze]')
  const finalCard = page.locator('.pc.final')
  const lowerSemi = page.locator('[data-round="1"]:not([data-bronze])').last()
  const quarter = page.locator('[data-round="0"]').last()
  const champ = page.locator('.b-champ')
  await expect(page.getByRole('heading', { name: 'Чемпион', exact: true })).toHaveCount(0)
  await expect(bronze.getByText('Матч за 3-е место', { exact: true })).toBeVisible()
  await expect(bronze.getByText('Проигравший 1/2 №1', { exact: true })).toBeVisible()
  await expect(bronze.getByText('Проигравший 1/2 №2', { exact: true })).toBeVisible()
  await expect(bronze.getByText('Дата уточняется', { exact: true })).toBeVisible()
  await expect(bronze.locator('svg')).toHaveCount(0)
  await expectNavyHeader(page)
  const bronzeBox = await bronze.boundingBox()
  const finalBox = await finalCard.boundingBox()
  const semiBox = await lowerSemi.boundingBox()
  const quarterBox = await quarter.boundingBox()
  const champBox = await champ.boundingBox()
  expect(bronzeBox && finalBox && semiBox && quarterBox && champBox).toBeTruthy()
  expect(Math.abs(bronzeBox!.x - semiBox!.x)).toBeLessThan(8)
  expect(bronzeBox!.y).toBeGreaterThanOrEqual(semiBox!.y + semiBox!.height - 2)
  expect(bronzeBox!.x + bronzeBox!.width).toBeLessThanOrEqual(finalBox!.x + 1)
  expect(bronzeBox!.y).toBeLessThanOrEqual(quarterBox!.y + 8)
  expect(champBox!.y).toBeGreaterThanOrEqual(finalBox!.y + finalBox!.height - 4)
  expect(champBox!.x).toBeLessThan(finalBox!.x + finalBox!.width * 0.5)
  const home = bronze.getByText('Проигравший 1/2 №1', { exact: true })
  const clipped = await home.evaluate((el) => el.scrollWidth > el.clientWidth + 1)
  expect(clipped).toBe(false)
  await page.locator('.po-card').screenshot({ path: '/opt/cursor/artifacts/third-place-1440.png' })
})
