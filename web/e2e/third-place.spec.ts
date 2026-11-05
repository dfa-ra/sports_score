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
          matchId: 'match-bronze',
          homeTeamId: 'bronze-h',
          homeTeamName: 'Гамма',
          awayTeamId: 'bronze-a',
          awayTeamName: 'Эта',
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

test('third-place slot is labeled on a phone bracket', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await page.setViewportSize({ width: 375, height: 812 })
  await mockApi(page)
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await page.getByRole('tab', { name: 'Финал', exact: true }).click()
  const bronze = page.getByText('Матч за 3-е место', { exact: true })
  await expect(bronze).toBeVisible()
  await expect(page.getByText('Финал', { exact: true }).first()).toBeVisible()
  await page.locator('.po-card').screenshot({ path: '/opt/cursor/artifacts/third-place-375.png' })
})

test('third-place slot is labeled on a desktop bracket', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page)
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await expect(page.locator('.po-grid')).toBeVisible()
  await expect(page.getByText('Матч за 3-е место', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Финал', exact: true })).toBeVisible()
  await page.locator('.po-card').screenshot({ path: '/opt/cursor/artifacts/third-place-1440.png' })
})
