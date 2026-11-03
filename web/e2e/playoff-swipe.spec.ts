import { expect, test, type Page, type Route } from '@playwright/test'

const tournamentId = '00000000-0000-4000-8000-000000000094'

function slot(n: number, round: number, index: number) {
  return {
    id: `slot-${round}-${index}`,
    round,
    slotIndex: index,
    roundTitle: round === 0 ? '1/2 финала' : 'Финал',
    homeTeamId: `home-${n}`,
    homeTeamName: `Хозяева ${n}`,
    awayTeamId: `away-${n}`,
    awayTeamName: `Гости ${n}`,
    matchStatus: 'SCHEDULED',
    scheduledAt: '2026-11-05T16:00:00Z',
    venue: 'Зал',
  }
}

const playoff = {
  competitionStage: 'PLAYOFF',
  rounds: [
    { round: 0, title: '1/2 финала', slots: [slot(1, 0, 0), slot(2, 0, 1)] },
    { round: 1, title: 'Финал', slots: [slot(3, 1, 0)] },
  ],
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/v1/teams') return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
      return json({
        name: 'Кубок теста',
        format: 'CUP',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: 4,
        matchesCount: 3,
        seasonYear: 2026,
        playoffLive: false,
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) return json(playoff)
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}`)) {
      return json({
        id: tournamentId,
        name: 'Кубок теста',
        status: 'ACTIVE',
        format: 'CUP',
        competitionStage: 'PLAYOFF',
        seasonYear: 2026,
        regulations: '',
      })
    }
    return json({})
  })
}

test('swiping from the semifinal to the final switches the round', async ({ page }) => {
  await mockApi(page)
  await page.goto(`/tournaments/${tournamentId}`)
  const semi = page.getByRole('tab', { name: '1/2', exact: true })
  const finalTab = page.getByRole('tab', { name: 'Финал', exact: true })
  await expect(semi).toHaveAttribute('aria-selected', 'true')
  const scroller = page.locator('.po-swipe')
  await expect(scroller).toBeVisible()
  const box = await scroller.boundingBox()
  if (!box) throw new Error('bracket scroller has no box')
  const client = await page.context().newCDPSession(page)
  const y = box.y + Math.min(80, box.height / 2)
  const startX = box.x + box.width * 0.84
  const endX = box.x + box.width * 0.08
  await client.send('Input.dispatchTouchEvent', {
    type: 'touchStart',
    touchPoints: [{ x: Math.round(startX), y: Math.round(y) }],
  })
  for (let step = 1; step <= 12; step += 1) {
    const x = startX + ((endX - startX) * step) / 12
    await client.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: Math.round(x), y: Math.round(y) }],
    })
  }
  await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  await expect(finalTab).toHaveAttribute('aria-selected', 'true')
  await expect(semi).toHaveAttribute('aria-selected', 'false')
})
