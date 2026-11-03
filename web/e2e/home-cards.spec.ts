import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const widths = [375, 640, 768, 960, 961, 1100] as const
const fitName = 'QA Толян Альфа'
const twoLineName = 'QA Толян Альфа Северный'
const overflowName = 'QA Толян Альфа Очень Длинное Название Команды Которое Специально Не Помещается В Две Строки Карточки Турнира И Ещё Дальше'

function match(
  id: string,
  tournamentId: string,
  tournamentName: string,
  status: string,
  scheduledAt: string,
  homeTeamName = fitName,
  awayTeamName = twoLineName,
) {
  return {
    id,
    tournamentId,
    tournamentName,
    tournamentShortName: tournamentName,
    scheduledAt,
    status,
    homeTeamId: `${id}-home`,
    homeTeamName,
    awayTeamId: `${id}-away`,
    awayTeamName,
    homeScore: 1,
    awayScore: 0,
    minute: status === 'LIVE' ? 12 : null,
    stageLabel: '1/2 финала',
    round: 1,
  }
}

const next = match('next-1', 'cup-a', 'Осенний кубок', 'SCHEDULED', '2026-12-05T14:00:00Z', fitName, twoLineName)
const live = match('live-1', 'cup-b', 'Зимний кубок', 'LIVE', '2026-11-03T12:00:00Z', fitName, twoLineName)
const lastA = match('last-a', 'cup-a', 'Осенний кубок', 'FINISHED', '2026-11-01T14:00:00Z', twoLineName, overflowName)
const lastB = match('last-b', 'cup-b', 'Зимний кубок', 'FINISHED', '2026-11-02T14:00:00Z', twoLineName, overflowName)

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

function overview(withLive: boolean) {
  return {
    generatedAt: '2026-11-03T12:00:00Z',
    next,
    live: withLive ? [live] : [],
    upcoming: [next],
    recent: [lastA, lastB],
    tournaments: [
      tournament('cup-a', 'Осенний кубок', lastA, 'ACTIVE'),
      tournament('cup-b', 'Зимний кубок', lastB, 'ACTIVE'),
    ],
    scorersByTournament: [],
    summary: { matchesPlayed: 2, goals: 1, goalsPerMatch: 0.5, teams: 4, players: 16 },
  }
}

async function mockApi(page: Page, withLive: boolean) {
  const body = overview(withLive)
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const payload = path.endsWith('/home/overview') ? body : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
  })
}

async function openHome(page: Page, width: number) {
  await page.setViewportSize({ width, height: 2400 })
  await page.goto('/')
  await expect(page.locator('.eyebrow', { hasText: 'Ближайший матч' })).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
}

function exactName(page: Page, name: string) {
  return page.locator('.tcard .irow .n').filter({ hasText: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) })
}

async function columns(locator: Locator) {
  return locator.evaluate((el) => getComputedStyle(el).gridTemplateColumns.trim().split(/\s+/).filter(Boolean).length)
}

async function nameLayout(locator: Locator) {
  return locator.evaluate((el) => {
    const style = getComputedStyle(el)
    const box = el.getBoundingClientRect()
    const probe = el.cloneNode(true) as HTMLElement
    probe.style.position = 'fixed'
    probe.style.left = '0'
    probe.style.top = '0'
    probe.style.visibility = 'hidden'
    probe.style.pointerEvents = 'none'
    probe.style.boxSizing = 'border-box'
    probe.style.width = `${box.width}px`
    probe.style.maxWidth = `${box.width}px`
    probe.style.height = 'auto'
    probe.style.maxHeight = 'none'
    probe.style.margin = '0'
    probe.style.font = style.font
    probe.style.lineHeight = style.lineHeight
    probe.style.letterSpacing = style.letterSpacing
    probe.style.wordBreak = style.wordBreak
    probe.style.overflowWrap = style.overflowWrap
    probe.style.whiteSpace = 'normal'
    probe.style.display = 'block'
    probe.style.overflow = 'visible'
    probe.style.webkitLineClamp = 'unset'
    probe.style.lineClamp = 'none'
    probe.style.webkitBoxOrient = 'unset'
    document.body.appendChild(probe)
    const fullHeight = probe.getBoundingClientRect().height
    probe.remove()
    const textNode = el.firstChild
    const range = document.createRange()
    if (textNode) range.selectNodeContents(textNode)
    else range.selectNodeContents(el)
      const lines = [...range.getClientRects()]
      .filter((rect) => {
        const mid = (rect.top + rect.bottom) / 2
        return mid >= box.top && mid < box.bottom
      })
      .map((rect) => ({ width: rect.width, top: rect.top }))
    return {
      text: (el.textContent || '').replace(/\s+/g, ' ').trim(),
      webkitLineClamp: style.webkitLineClamp,
      lineClamp: style.lineClamp,
      whiteSpace: style.whiteSpace,
      textOverflow: style.textOverflow,
      display: style.display,
      shown: box.height,
      fullHeight,
      lineHeight: Number.parseFloat(style.lineHeight),
      boxWidth: box.width,
      lines,
    }
  })
}

test('live cards are one column through 960px and clamp team names at two lines', async ({ page }) => {
  await mockApi(page, true)
  mkdirSync('/opt/cursor/artifacts', { recursive: true })

  for (const width of widths) {
    await openHome(page, width)
    await expect(page.locator('.livebar')).toBeVisible()
    await expect(page.locator('.livebar .st', { hasText: 'Live сейчас' })).toBeVisible()

    const grid = page.locator('.tgrid')
    const cards = page.locator('.tcard')
    await expect(cards).toHaveCount(2)
    const narrow = width <= 960
    expect(await page.evaluate(() => window.matchMedia('(max-width: 960px)').matches)).toBe(narrow)
    expect(await columns(grid)).toBe(narrow ? 1 : 2)

    const first = await cards.nth(0).boundingBox()
    const second = await cards.nth(1).boundingBox()
    expect(first && second).toBeTruthy()
    if (narrow) {
      expect(second!.y).toBeGreaterThan(first!.y + first!.height - 2)
      expect(Math.abs(first!.x - second!.x)).toBeLessThan(2)
    } else {
      expect(Math.abs(first!.y - second!.y)).toBeLessThan(2)
      expect(second!.x).toBeGreaterThan(first!.x + 40)
    }

    if (width === 375 || width === 1100) {
      const fit = exactName(page, fitName).first()
      const wrapped = exactName(page, twoLineName).first()
      const overflow = exactName(page, overflowName).first()
      await expect(fit).toBeVisible()
      await expect(wrapped).toBeVisible()
      await expect(overflow).toBeVisible()

      const declaredClamp = await page.evaluate(() =>
        [...document.querySelectorAll('style')].some((node) => {
          const text = node.textContent || ''
          return /(^|[^-])line-clamp:\s*2/.test(text) && /-webkit-line-clamp:\s*2/.test(text)
        }),
      )
      expect(declaredClamp).toBe(true)
      for (const name of [fit, wrapped, overflow]) {
        const layout = await nameLayout(name)
        expect(layout.webkitLineClamp).toBe('2')
        expect(layout.whiteSpace).not.toBe('nowrap')
        expect(layout.textOverflow).toBe('ellipsis')
      }

      const fitLayout = await nameLayout(fit)
      const wrappedLayout = await nameLayout(wrapped)
      const overflowLayout = await nameLayout(overflow)
      const lineTops = (lines: { top: number }[]) => {
        const tops: number[] = []
        for (const line of lines) {
          if (!tops.some((top) => Math.abs(top - line.top) < 2)) tops.push(line.top)
        }
        return tops.sort((a, b) => a - b)
      }

      expect(fitLayout.text).toBe(fitName)
      expect(fitLayout.fullHeight).toBeLessThanOrEqual(fitLayout.shown + 1)
      expect(lineTops(fitLayout.lines).length).toBeGreaterThan(0)
      expect(lineTops(fitLayout.lines).length).toBeLessThanOrEqual(2)

      expect(wrappedLayout.text).toBe(twoLineName)
      expect(wrappedLayout.fullHeight).toBeLessThanOrEqual(wrappedLayout.shown + 1)
      expect(lineTops(wrappedLayout.lines)).toHaveLength(2)

      const overflowTops = lineTops(overflowLayout.lines)
      expect(overflowLayout.text).toBe(overflowName)
      expect(overflowTops).toHaveLength(2)
      expect(overflowLayout.shown).toBeGreaterThan(overflowLayout.lineHeight * 1.4)
      expect(overflowLayout.shown).toBeLessThanOrEqual(overflowLayout.lineHeight * 2 + 2)
      expect(overflowLayout.fullHeight).toBeGreaterThan(overflowLayout.shown + 1)
      expect(overflowTops[1]).toBeGreaterThan(overflowTops[0] + 2)
    }

    if (width === 768 || width === 1100) {
      const section = page.locator('.block').filter({ has: page.getByRole('heading', { name: 'Идут сейчас' }) })
      await section.screenshot({ path: `/opt/cursor/artifacts/home-cards-${width}.png` })
    }
  }
})

test('nearest match fills the hero when nothing is live', async ({ page }) => {
  await mockApi(page, false)
  mkdirSync('/opt/cursor/artifacts', { recursive: true })

  for (const width of widths) {
    await openHome(page, width)
    await expect(page.locator('.livebar')).toHaveCount(0)
    await expect(page.getByText('Live сейчас')).toHaveCount(0)

    const hero = page.locator('.hero-match')
    await expect(hero).toBeVisible()
    expect(await columns(hero)).toBe(1)
    const content = hero.locator(':scope > div').first()
    const eyebrow = hero.locator('.eyebrow', { hasText: 'Ближайший матч' })
    const acts = hero.locator('.acts')
    const heroBox = await hero.boundingBox()
    const contentBox = await content.boundingBox()
    const eyeBox = await eyebrow.boundingBox()
    const actsBox = await acts.boundingBox()
    expect(heroBox && contentBox && eyeBox && actsBox).toBeTruthy()

    const pad = await hero.evaluate((el) => {
      const style = getComputedStyle(el)
      return {
        top: Number.parseFloat(style.paddingTop),
        right: Number.parseFloat(style.paddingRight),
        bottom: Number.parseFloat(style.paddingBottom),
        left: Number.parseFloat(style.paddingLeft),
      }
    })
    expect(eyeBox!.y - heroBox!.y).toBeLessThan(pad.top + 8)
    expect(heroBox!.y + heroBox!.height - (actsBox!.y + actsBox!.height)).toBeLessThan(pad.bottom + 12)
    expect(Math.abs(contentBox!.width - (heroBox!.width - pad.left - pad.right))).toBeLessThan(2)
    expect(contentBox!.width).toBeGreaterThan(heroBox!.width * 0.8)

    const metrics = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }))
    expect(metrics.scrollWidth).toBe(metrics.clientWidth)

    if (width === 640) {
      await page.locator('.band').screenshot({ path: '/opt/cursor/artifacts/home-no-live-640.png' })
    }
  }
})
