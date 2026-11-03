import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const tournamentId = '00000000-0000-4000-8000-000000000098'
const widths = [1181, 1180, 1024, 853, 731] as const
const shots: Record<number, string> = {
  1024: '/opt/cursor/artifacts/playoff-16-1024.png',
  1180: '/opt/cursor/artifacts/playoff-16-1180.png',
  1181: '/opt/cursor/artifacts/playoff-16-1181.png',
}

const roundTabs: Record<number, string[]> = {
  16: ['1/8', '1/4', '1/2', 'Финал'],
  8: ['1/4', '1/2', 'Финал'],
  4: ['1/2', 'Финал'],
}

const roundTitles: Record<number, string[]> = {
  16: ['1/8 финала', '1/4 финала', '1/2 финала', 'Финал'],
  8: ['1/4 финала', '1/2 финала', 'Финал'],
  4: ['1/2 финала', 'Финал'],
}

const state = { teams: 16 }

function bracket(teams: number) {
  const titles = roundTitles[teams]
  return titles.map((title, index) => {
    const count = 2 ** (titles.length - index - 1)
    return {
      round: index + 1,
      title,
      slots: Array.from({ length: count }, (_, slotIndex) => ({
        id: `slot-${teams}-${index}-${slotIndex}`,
        round: index + 1,
        slotIndex,
        roundTitle: title,
        homeTeamId: `home-${teams}-${index}-${slotIndex}`,
        homeTeamName: `Хозяева ${index + 1}.${slotIndex + 1}`,
        awayTeamId: `away-${teams}-${index}-${slotIndex}`,
        awayTeamName: `Гости ${index + 1}.${slotIndex + 1}`,
        matchStatus: 'SCHEDULED',
        scheduledAt: '2026-11-05T16:00:00Z',
        venue: 'Зал',
      })),
    }
  })
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const teams = state.teams
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/v1/teams') return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
      return json({
        name: `ПО-${teams}`,
        format: 'GROUPS_PLAYOFF',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: teams,
        matchesCount: teams - 1,
        seasonYear: 2026,
        playoffLive: false,
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) {
      return json({ competitionStage: 'PLAYOFF', rounds: bracket(teams) })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}`)) {
      return json({
        id: tournamentId,
        name: `ПО-${teams}`,
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

async function openBracket(page: Page, teams: number) {
  state.teams = teams
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
}

function rounds(page: Page) {
  return page.getByRole('tablist', { name: 'Раунды' })
}

async function expectRoundTabs(page: Page, teams: number) {
  const tabs = rounds(page)
  await expect(tabs).toBeVisible()
  await expect(page.locator('.po-grid')).toHaveCount(0)
  for (const label of roundTabs[teams]) {
    await expect(tabs.getByRole('tab', { name: label, exact: true })).toBeVisible()
  }
  const metrics = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(metrics.scrollWidth).toBe(metrics.clientWidth)
}

async function expectFullGrid(page: Page, teams: number) {
  await expect(rounds(page)).toHaveCount(0)
  const headers = page.locator('.po-grid h3.rh')
  const expected = [...roundTitles[teams], 'Чемпион']
  await expect(headers).toHaveCount(expected.length)
  for (let index = 0; index < expected.length; index += 1) {
    await expect(headers.nth(index)).toContainText(expected[index])
  }
  const tops = await headers.evaluateAll((nodes) => nodes.map((node) => node.getBoundingClientRect().top))
  expect(Math.max(...tops) - Math.min(...tops)).toBeLessThan(8)
}

test('16-team rounds use tabs at 1180 CSS px; 8- and 4-team grids stay until 720', async ({ page }) => {
  await mockApi(page)
  mkdirSync('/opt/cursor/artifacts', { recursive: true })

  for (const width of widths) {
    await page.setViewportSize({ width, height: 900 })

    await openBracket(page, 16)
    if (width <= 1180) await expectRoundTabs(page, 16)
    else await expectFullGrid(page, 16)
    const shot = shots[width]
    if (shot) await page.locator('.po').screenshot({ path: shot })

    await openBracket(page, 8)
    await expectFullGrid(page, 8)

    await openBracket(page, 4)
    await expectFullGrid(page, 4)
  }
})

test('a 1280px window at 125%, 150% and 175% zoom uses the visual CSS width', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await openBracket(page, 16)
  await expectFullGrid(page, 16)

  const client = await page.context().newCDPSession(page)
  for (const scale of [1.25, 1.5, 1.75]) {
    for (const teams of [16, 8, 4] as const) {
      await openBracket(page, teams)
      await client.send('Emulation.setPageScaleFactor', { pageScaleFactor: scale })
      if (teams === 16) await expectRoundTabs(page, 16)
      else await expectFullGrid(page, teams)
    }
  }

  await openBracket(page, 16)
  await client.send('Emulation.setPageScaleFactor', { pageScaleFactor: 1 })
  await expectFullGrid(page, 16)
})
