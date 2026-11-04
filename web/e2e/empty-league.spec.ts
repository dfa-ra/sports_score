import { expect, test, type Page, type Route } from '@playwright/test'

const emptyOverview = {
  generatedAt: '2026-11-04T12:00:00Z',
  next: null,
  live: [],
  upcoming: [],
  recent: [],
  tournaments: [],
  scorersByTournament: [],
  summary: {
    matchesPlayed: 0,
    goals: 0,
    goalsPerMatch: 0,
    teams: 0,
    players: 0,
  },
}

async function mockEmptyLeague(page: Page) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/home/overview')
      ? emptyOverview
      : path.endsWith('/tournaments')
        ? { content: [], totalElements: 0 }
        : { content: [] }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

test('empty league hides live and shows text instead of blank cards', async ({ page }) => {
  await mockEmptyLeague(page)

  for (const width of [375, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Идут сейчас' })).toBeVisible()
    await expect(page.getByText('Турниров пока нет')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Матчи' })).toBeVisible()
    await expect(page.getByText('Матчей пока нет')).toBeVisible()
    await expect(page.locator('.livebar')).toHaveCount(0)
    await expect(page.locator('.tcard')).toHaveCount(0)
    await expect(page.locator('.mrow, .mc')).toHaveCount(0)
    await expect(page.getByText('Лига в цифрах')).toBeVisible()
    await expect(page.locator('.sgrid')).toContainText('0,0')
    await expect(page.locator('.sgrid')).not.toContainText('NaN')

    const hero = page.locator('.hero-card')
    const box = await hero.boundingBox()
    expect(box).toBeTruthy()
    expect(box!.height).toBeLessThan(360)
    const liveSpace = await hero.evaluate((el) => {
      const live = el.querySelector('.livebar')
      const style = getComputedStyle(el)
      return {
        liveHeight: live ? live.getBoundingClientRect().height : 0,
        minHeight: style.minHeight,
      }
    })
    expect(liveSpace.liveHeight).toBe(0)
    if (width <= 719) expect(liveSpace.minHeight).toBe('0px')

    const scroll = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      text: document.body.innerText,
    }))
    expect(scroll.scrollWidth).toBe(scroll.clientWidth)
    expect(scroll.text).not.toContain('NaN')
  }
})
