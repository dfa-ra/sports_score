import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

const words: Record<number, { goals: string; assists: string }> = {
  1: { goals: 'гол', assists: 'передача' },
  2: { goals: 'гола', assists: 'передачи' },
  5: { goals: 'голов', assists: 'передач' },
  11: { goals: 'голов', assists: 'передач' },
  12: { goals: 'голов', assists: 'передач' },
  21: { goals: 'гол', assists: 'передача' },
  100: { goals: 'голов', assists: 'передач' },
}

type BoardMode = 'ready' | 'hold' | 'empty' | 'fail'

function player(
  id: string,
  first: string,
  last: string,
  goals: number,
  assists: number,
  teamId: string,
  avatarUrl: string | null = null,
) {
  return {
    playerId: id,
    id,
    firstName: first,
    lastName: last,
    goals,
    assists,
    appearances: 10,
    teamId,
    teamLogoUrl: '',
    avatarUrl,
  }
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function stamp(page: Page, label = 'Тестовый стенд · фикстура, не прод') {
  await page.evaluate((text) => {
    document.getElementById('fixture-stamp')?.remove()
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = text
    note.style.cssText = 'margin:12px 8px 4px;display:inline-block;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);'
    document.body.appendChild(note)
  }, label)
}

async function shot(page: Page, name: string, label?: string) {
  mkdirSync(OUT, { recursive: true })
  await page.evaluate(() => document.fonts.ready)
  await stamp(page, label)
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
}

async function metricFacts(page: Page) {
  return page.locator('.leader').evaluateAll((cards) => cards.map((card) => {
    const metric = card.querySelector('.leader-metric') as HTMLElement | null
    const value = metric?.querySelector('b') as HTMLElement | null
    const label = metric?.querySelector('small') as HTMLElement | null
    const name = card.querySelector('.leader-row a') as HTMLElement | null
    const team = card.querySelector('.club-name') as HTMLElement | null
    const row = card.querySelector('.leader-row') as HTMLElement | null
    const title = card.querySelector('p') as HTMLElement | null
    if (!metric || !value || !label || !name || !row || !title) return null
    const metricBox = metric.getBoundingClientRect()
    const valueBox = value.getBoundingClientRect()
    const labelBox = label.getBoundingClientRect()
    const nameBox = name.getBoundingClientRect()
    const teamBox = team?.getBoundingClientRect()
    const rowBox = row.getBoundingClientRect()
    const cardBox = card.getBoundingClientRect()
    const cardStyle = getComputedStyle(card)
    const nameRange = document.createRange()
    nameRange.selectNodeContents(name)
    const teamRange = document.createRange()
    if (team) teamRange.selectNodeContents(team)
    return {
      title: (title.textContent || '').trim(),
      value: (value.textContent || '').trim(),
      label: (label.textContent || '').trim(),
      gap: labelBox.top - valueBox.bottom,
      gapCss: getComputedStyle(metric).gap,
      valueSize: getComputedStyle(value).fontSize,
      labelSize: getComputedStyle(label).fontSize,
      nameSize: getComputedStyle(name).fontSize,
      teamSize: team ? getComputedStyle(team).fontSize : '',
      categorySize: getComputedStyle(title).fontSize,
      paddingTop: cardStyle.paddingTop,
      paddingRight: cardStyle.paddingRight,
      radius: cardStyle.borderTopLeftRadius,
      minWidth: Number.parseFloat(getComputedStyle(metric).minWidth),
      metricWidth: metricBox.width,
      valueScroll: value.scrollWidth,
      valueClient: value.clientWidth,
      centerDelta: Math.abs((metricBox.top + metricBox.height / 2) - (rowBox.top + rowBox.height / 2)),
      metricHeight: metricBox.height,
      cardHeight: cardBox.height,
      nameRight: nameBox.right,
      metricLeft: metricBox.left,
      metricRight: metricBox.right,
      cardRight: cardBox.right,
      nameLines: nameRange.getClientRects().length,
      teamLines: team ? teamRange.getClientRects().length : 0,
      teamTop: teamBox?.top ?? 0,
      nameBottom: nameBox.bottom,
      clipped: value.scrollWidth > value.clientWidth + 1 || label.scrollWidth > label.clientWidth + 1,
    }
  }))
}

test('players leader metric stack keeps word forms at 1, 2, 5, 11, 21 and 100', async ({ page }) => {
  test.setTimeout(90_000)
  let mode: BoardMode = 'ready'
  let goals = 12
  let assists = 5
  let long = false
  let releaseBoard: () => void = () => {}
  let hold = new Promise<void>((resolve) => {
    releaseBoard = resolve
  })

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/statistics/board') {
      if (mode === 'hold') await hold
      if (mode === 'fail') return json(route, { message: 'down' }, 500)
      if (mode === 'empty') return json(route, { scorers: [], assists: [] })
      const scorer = long
        ? player('p1', 'Александр Константинович', 'Орлов-Воскресенский', goals, 1, 'ft')
        : player('p1', 'Илья', 'Орлов', goals, 2, 'ft')
      const helper = long
        ? player('p2', 'Мария Александровна', 'Белова-Преображенская', 1, assists, 'pu')
        : player('p2', 'Артём', 'Белов', 5, assists, 'pu')
      return json(route, { scorers: [scorer, helper], assists: [helper, scorer] })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, {
        content: [
          { id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' },
          { id: 'cup', name: 'Очень длинный студенческий кубок факультетов', status: 'ACTIVE' },
        ],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/teams') {
      return json(route, {
        content: [
          { id: 'ft', name: long ? 'Факультет программной инженерии и компьютерной техники' : 'Физтех', logoUrl: '' },
          { id: 'pu', name: long ? 'Сборная института международного развития и экономики' : 'Пумы', logoUrl: '' },
        ],
        totalPages: 1,
      })
    }
    return json(route, { content: [] })
  })

  mode = 'hold'
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/players')
  expect(await page.evaluate(() => window.devicePixelRatio)).toBe(1)
  await expect(page.getByRole('heading', { name: 'Игроки' })).toBeVisible()
  await expect(page.getByLabel('Поиск игрока')).toBeVisible()
  await expect(page.locator('.leaders .card')).toHaveCount(2)
  await expect(page.locator('.leader-metric')).toHaveCount(0)
  mode = 'ready'
  releaseBoard()

  async function expectWords(goalCount: number, assistCount: number) {
    goals = goalCount
    assists = assistCount
    await page.goto('/players')
    await expect(page.locator('.leader').nth(0).locator('.leader-metric small')).toHaveText(words[goalCount].goals)
    await expect(page.locator('.leader').nth(1).locator('.leader-metric small')).toHaveText(words[assistCount].assists)
    await expect(page.locator('.leader').nth(0).locator('.leader-metric b')).toHaveText(String(goalCount))
    await expect(page.locator('.leader').nth(1).locator('.leader-metric b')).toHaveText(String(assistCount))
  }

  for (const count of [1, 2, 5, 11, 21, 100]) await expectWords(count, count)

  goals = 12
  assists = 5
  long = false
  await page.goto('/players')
  await expect(page.getByRole('heading', { name: 'Бомбардиры' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Голы' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Передачи' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Все турниры' })).toBeVisible()
  await expect(page.locator('.leader-metric')).toHaveCount(2)
  await expect(page.getByText('Физтех').first()).toBeVisible()
  await expect(page.getByText('Пумы').first()).toBeVisible()

  for (const width of [375, 719, 1000, 1440]) {
    await page.setViewportSize({ width, height: width < 800 ? 812 : 900 })
    if (width === 1440) {
      expect(await page.evaluate(() => window.innerWidth)).toBe(1440)
      expect(await page.evaluate(() => window.devicePixelRatio)).toBe(1)
    }
    const facts = await metricFacts(page)
    expect(facts, `viewport ${width}`).toHaveLength(2)
    for (const fact of facts) {
      expect(fact, `viewport ${width}`).not.toBeNull()
      if (!fact) continue
      expect(fact.gapCss, `${fact.title} gap css at ${width}`).toBe('4px')
      expect(Math.abs(fact.gap - 4), `${fact.title} gap at ${width}`).toBeLessThan(0.6)
      expect(fact.valueSize).toBe('38px')
      expect(fact.labelSize).toBe('12px')
      expect(fact.nameSize).toBe('16px')
      expect(fact.teamSize).toBe('12px')
      expect(fact.categorySize).toBe('12px')
      expect(fact.paddingTop).toBe('20px')
      expect(fact.paddingRight).toBe('20px')
      expect(fact.radius).toBe('18px')
      expect(fact.minWidth).toBeGreaterThanOrEqual(72)
      expect(fact.metricWidth).toBeGreaterThanOrEqual(72)
      expect(fact.clipped).toBe(false)
      expect(fact.centerDelta).toBeLessThan(1.5)
      expect(fact.metricHeight).toBeLessThan(70)
      expect(fact.metricHeight).toBeLessThan(fact.cardHeight)
      expect(fact.nameRight).toBeLessThanOrEqual(fact.metricLeft + 1)
      expect(fact.metricRight).toBeLessThanOrEqual(fact.cardRight + 1)
      expect(fact.teamTop).toBeGreaterThanOrEqual(fact.nameBottom - 1)
    }
    const scroll = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }))
    expect(scroll.scrollWidth, `horizontal scroll at ${width}`).toBe(scroll.clientWidth)
  }

  await page.setViewportSize({ width: 1440, height: 900 })
  await shot(page, 'q2-players-metric-1440')
  await page.setViewportSize({ width: 375, height: 812 })
  await shot(page, 'q2-players-metric-375')

  long = true
  goals = 100
  assists = 2
  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/players')
  await expect(page.getByText('Факультет программной инженерии').first()).toBeVisible()
  await expect(page.locator('.leader').nth(0).locator('.leader-metric b')).toHaveText('100')
  await expect(page.locator('.leader').nth(0).locator('.leader-metric small')).toHaveText('голов')
  await expect(page.locator('.leader').nth(1).locator('.leader-metric small')).toHaveText('передачи')
  const longFacts = await metricFacts(page)
  expect(longFacts).toHaveLength(2)
  for (const fact of longFacts) {
    expect(fact).not.toBeNull()
    if (!fact) continue
    expect(fact.nameLines).toBeGreaterThan(1)
    expect(fact.teamLines).toBeGreaterThan(1)
    expect(fact.clipped).toBe(false)
    expect(fact.value).toMatch(/^(100|2)$/)
    expect(fact.nameRight).toBeLessThanOrEqual(fact.metricLeft + 1)
    expect(fact.cardHeight).toBeGreaterThan(116)
  }
  const longScroll = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(longScroll.scrollWidth).toBe(longScroll.clientWidth)
  await shot(page, 'q2-players-metric-long-375')

  mode = 'empty'
  long = false
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/players')
  await expect(page.getByText('Бомбардир', { exact: true })).toBeVisible()
  await expect(page.getByText('Ассистент', { exact: true })).toBeVisible()
  await expect(page.locator('.leader-metric')).toHaveCount(0)
  await expect(page.getByText('Игроков пока нет')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Игроки' })).toBeVisible()
  await expect(page.locator('.leader').nth(0)).not.toContainText('0')
  await expect(page.locator('.leader').nth(1)).not.toContainText('0')

  mode = 'fail'
  await page.goto('/players')
  await expect(page.getByText('Игроки не загрузились')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Повторить' })).toBeVisible()
  await expect(page.getByLabel('Поиск игрока')).toBeVisible()
  await expect(page.locator('.leader-metric')).toHaveCount(0)
  await expect(page.getByText('Бомбардир')).toHaveCount(0)
})

test('leader card at 1440 keeps a 4px gap, a wrapped name, and the given photo', async ({ page }) => {
  const photo = 'https://fixture.invalid/orlov.jpg'
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NMQ0AAAgDsElDKlKngoOkSf8ms7cEAoFAIBAIBALBl6DKSoAut3OBigAAAABJRU5ErkJggg==', 'base64')
  await page.route('**/orlov.jpg', (route) => route.fulfill({ status: 200, contentType: 'image/png', body: png }))
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/statistics/board') {
      return json(route, {
        scorers: [player('p1', 'Александр Константинович Владимирович', 'Орлов-Воскресенский-Преображенский', 12, 1, 'ft', photo)],
        assists: [player('p2', 'Мария Александровна', 'Белова-Преображенская', 1, 5, 'pu', photo)],
      })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, { content: [{ id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' }], totalPages: 1 })
    }
    if (path === '/api/v1/teams') {
      return json(route, {
        content: [
          { id: 'ft', name: 'Факультет программной инженерии и компьютерной техники', logoUrl: '' },
          { id: 'pu', name: 'Сборная института международного развития и экономики', logoUrl: '' },
        ],
        totalPages: 1,
      })
    }
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/players')
  expect(await page.evaluate(() => window.innerWidth)).toBe(1440)
  expect(await page.evaluate(() => window.devicePixelRatio)).toBe(1)
  const photoImg = page.locator('.leader').nth(0).locator('img')
  await expect(photoImg).toHaveAttribute('src', photo)
  const facts = await metricFacts(page)
  expect(facts).toHaveLength(2)
  for (const fact of facts) {
    expect(fact).not.toBeNull()
    if (!fact) continue
    expect(fact.gapCss).toBe('4px')
    expect(Math.abs(fact.gap - 4)).toBeLessThan(0.6)
    expect(fact.nameLines).toBeGreaterThan(1)
    expect(fact.clipped).toBe(false)
  }
  await shot(page, 'q2-players-metric-photo-1440', 'Тестовый стенд · фикстура, не live')
})
