import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const devOverview = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'fixtures/dev-home-overview.json'), 'utf8')) as {
  tournaments: unknown[]
}

const OUT = '/opt/cursor/artifacts'
const WIDTHS = [320, 375, 1440] as const

function person(id: string, first: string, last: string, goals: number, assists: number, teamId: string) {
  return { playerId: id, firstName: first, lastName: last, goals, assists, appearances: 8, teamId, teamName: teamId === 'ft' ? 'Физтех' : 'Юристы' }
}

const scorers = [0, 1, 2, 3].map((index) => person(`p${index}`, 'Илья', 'Орлов', 12 - index, index, 'ft'))
const assists = [0, 1].map((index) => person(`a${index}`, 'Анна', 'Ким', 1, 6 - index, 'ur'))

const standingRows = Array.from({ length: 5 }, (_, index) => ({
  teamId: `tm${index}`,
  teamName: `Команда ${index + 1}`,
  played: 4,
  wins: 2,
  draws: 1,
  losses: 1,
  goalsFor: 6,
  goalsAgainst: 3,
  points: 7,
}))

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function createHold() {
  let armed = true
  const pending: Array<() => void> = []
  return {
    arm() { armed = true },
    open() {
      armed = false
      pending.splice(0).forEach((done) => done())
    },
    async wait(matches: boolean) {
      if (!armed || !matches) return
      await new Promise<void>((done) => pending.push(done))
    },
  }
}

async function installApi(page: Page, parts: string[]) {
  const hold = createHold()
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    await hold.wait(parts.some((part) => path.includes(part)))
    if (path.endsWith('/auth/me') || path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/statistics/board')) {
      return json(route, { scorers, assists, goalkeepers: assists })
    }
    if (path.endsWith('/teams')) {
      return json(route, {
        content: [
          { id: 'ft', name: 'Физтех' },
          { id: 'ur', name: 'Юристы' },
        ],
        totalPages: 1,
      })
    }
    if (path.endsWith('/tournaments')) {
      return json(route, { content: [{ id: 't1', name: 'Лига', status: 'ACTIVE' }], totalPages: 1, totalElements: 1 })
    }
    if (path.endsWith('/home/overview')) return json(route, devOverview)
    if (path.endsWith('/standings')) return json(route, [{ id: 'g', name: 'Группа A', rows: standingRows }])
    if (path.endsWith('/playoff')) return json(route, { rounds: [] })
    if (/\/tournaments\/[^/]+$/.test(path)) return json(route, { id: 't1', name: 'Лига', status: 'ACTIVE' })
    return json(route, { content: [] })
  })
  return hold
}

async function height(page: Page, selector: string) {
  return page.locator(selector).first().evaluate((el) => el.getBoundingClientRect().height)
}

function expectStable(before: number, after: number, label: string) {
  expect(Math.abs(before - after), `${label}: ${before} -> ${after}`).toBeLessThanOrEqual(1)
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const mark = { value: 0 }
    ;(window as unknown as { __cls: { value: number } }).__cls = mark
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const shift = entry as PerformanceEntry & { value?: number; hadRecentInput?: boolean }
        if (!shift.hadRecentInput && shift.value) mark.value += shift.value
      }
    }).observe({ type: 'layout-shift', buffered: true })
  })
})

test('players heading, hit target and block height stay put', async ({ page }) => {
  mkdirSync(OUT, { recursive: true })
  const hold = await installApi(page, ['/statistics/board'])
  const notes: Record<string, unknown>[] = []
  for (const width of WIDTHS) {
    hold.arm()
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/players', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.board-rows .skel').first()).toBeVisible()
    const loadingSheet = await height(page, '.players .sheet')
    const loadingHead = await height(page, '.board-head')
    hold.open()
    await expect(page.locator('.cols.line').first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const loadedSheet = await height(page, '.players .sheet')
    const loadedHead = await height(page, '.board-head')
    expectStable(loadingSheet, loadedSheet, `players sheet load ${width}`)
    expectStable(loadingHead, loadedHead, `players head load ${width}`)
    expect(loadedHead).toBeGreaterThanOrEqual(43)
    expect(loadedHead).toBeLessThanOrEqual(45)

    const goalsSheet = loadedSheet
    await page.getByRole('button', { name: 'Передачи' }).click()
    const assistsSheet = await height(page, '.players .sheet')
    expectStable(goalsSheet, assistsSheet, `players tab ${width}`)

    const heading = await page.locator('.board-head h2').evaluate((el) => {
      const style = getComputedStyle(el)
      const range = document.createRange()
      range.selectNodeContents(el)
      const text = el.textContent || ''
      const words = text.split(/\s+/).filter(Boolean)
      const linesOf = (rects: DOMRectList) => new Set([...rects].filter((rect) => rect.height > 0).map((rect) => Math.round(rect.top))).size
      const midWord = words.some((word) => {
        const start = text.indexOf(word)
        const wordRange = document.createRange()
        const node = el.firstChild
        if (!node) return false
        wordRange.setStart(node, start)
        wordRange.setEnd(node, start + word.length)
        return linesOf(wordRange.getClientRects()) > 1
      })
      return {
        lines: linesOf(range.getClientRects()),
        white: style.whiteSpace,
        overflow: style.overflow,
        ellipsis: style.textOverflow,
        minWidth: style.minWidth,
        midWord,
        text,
      }
    })
    if (width <= 720) {
      expect(heading.white).toBe('nowrap')
      expect(heading.overflow).toBe('hidden')
      expect(heading.ellipsis).toBe('ellipsis')
      expect(heading.minWidth).toBe('0px')
      expect(heading.lines).toBe(1)
    }
    expect(heading.midWord, heading.text).toBe(false)

    const goals = page.getByRole('button', { name: 'Голы' })
    await goals.scrollIntoViewIfNeeded()
    const hit = await goals.evaluate((el) => {
      const box = el.getBoundingClientRect()
      const before = getComputedStyle(el, '::before')
      const beforeH = parseFloat(before.height)
      const x = box.left + box.width / 2
      const y = box.top + box.height / 2 - beforeH / 2 + 1
      const top = document.elementFromPoint(x, y)
      return {
        visible: box.height,
        beforeH,
        hit: top === el || el.contains(top),
      }
    })
    expect(hit.visible).toBeGreaterThanOrEqual(30)
    expect(hit.visible).toBeLessThanOrEqual(34)
    expect(hit.beforeH).toBe(44)
    expect(hit.hit).toBe(true)
    notes.push({ width, loadingSheet, loadedSheet, assistsSheet, heading, hit })
    if (width === 320) await page.screenshot({ path: `${OUT}/players-heading-320.png`, fullPage: false })
    await page.getByRole('button', { name: 'Голы' }).click()
  }
  console.log('PLAYERS', JSON.stringify(notes))
})

test('home skeletons match the loaded blocks', async ({ page }) => {
  mkdirSync(OUT, { recursive: true })
  const hold = await installApi(page, ['/home/overview'])
  const notes: Record<string, unknown>[] = []
  for (const width of WIDTHS) {
    hold.arm()
    await page.setViewportSize({ width, height: 1400 })
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.matches-card .sk-mrow', { state: 'attached' })
    const loading = await page.evaluate(() => {
      const h = (sel: string) => document.querySelector(sel)?.getBoundingClientRect().height ?? null
      const cards = [...document.querySelectorAll('.sk-tcard')].map((el) => el.getBoundingClientRect().height)
      return {
        matches: h('.matches-card'),
        matchSk: h('.sk-matches'),
        scorers: h('.sk-scorers'),
        srow: [...document.querySelectorAll('.sk-scorers .srow, .sk-scorers tbody tr')].map((el) => el.getBoundingClientRect().height).find((value) => value > 0) ?? null,
        cards,
        grid: h('.tgrid'),
        cols: getComputedStyle(document.querySelector('.tgrid')!).gridTemplateColumns.split(' ').filter(Boolean).length,
      }
    })
    hold.open()
    await expect(page.locator('.tcard:not(.sk-tcard)').first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const loaded = await page.evaluate(() => {
      const h = (sel: string) => document.querySelector(sel)?.getBoundingClientRect().height ?? null
      const cards = [...document.querySelectorAll('.tcard')].map((el) => el.getBoundingClientRect().height)
      const row = document.querySelector('.srow, .stbl tbody tr')
      return {
        matches: h('.matches-card'),
        mc: h('.mc'),
        mrow: h('.mrow'),
        scorers: document.querySelector('.sk-scorers') ? null : h('.container.block:last-child .card'),
        row: [...document.querySelectorAll('.srow, .stbl tbody tr')].map((el) => el.getBoundingClientRect().height).find((value) => value > 0) ?? null,
        cards,
        grid: h('.tgrid'),
      }
    })
    if (width <= 720) {
      expect(loading.matchSk).toBeGreaterThanOrEqual(117)
      expect(loading.matchSk).toBeLessThanOrEqual(119)
      expect(loaded.mc).toBeGreaterThanOrEqual(117)
      expect(loaded.mc).toBeLessThanOrEqual(119)
    } else {
      expectStable(loading.matches, loaded.matches, `home matches rows ${width}`)
      expect(loaded.mrow).toBeGreaterThan(80)
    }
    expect(loading.cards).toHaveLength(4)
    if (width <= 960) expect(loading.cols).toBe(1)
    if (width >= 1440) expect(loading.cols).toBe(2)
    expect(loading.srow).toBeGreaterThanOrEqual(55)
    expect(loading.srow).toBeLessThanOrEqual(57)
    expect(loaded.row).toBeGreaterThanOrEqual(55)
    expect(loaded.row).toBeLessThanOrEqual(57)
    expectStable(loading.matches, loaded.matches, `home matches ${width}`)
    expectStable(loading.scorers, loaded.scorers ?? 0, `home scorers ${width}`)
    expect(loaded.cards).toHaveLength(loading.cards.length)
    loading.cards.forEach((card, index) => {
      expectStable(card, loaded.cards[index], `home tcard ${index} ${width}`)
    })
    expectStable(loading.grid ?? 0, loaded.grid ?? 0, `home tgrid ${width}`)
    const cardSpread = Math.max(...loaded.cards) - Math.min(...loaded.cards)
    expect(cardSpread, `home cards differ ${width}: ${loaded.cards.join(', ')}`).toBeLessThanOrEqual(1)
    const cls = await page.evaluate(() => (window as unknown as { __cls: { value: number } }).__cls.value)
    expect(cls, `home CLS ${width}`).toBeLessThanOrEqual(0.03)
    const beforeTab = loaded.scorers ?? 0
    await page.locator('.tabs button').nth(1).click()
    const afterTab = await height(page, '.container.block:last-child .card')
    expectStable(beforeTab, afterTab, `home scorer tab ${width}`)
    notes.push({ width, loading, loaded, afterTab, cls })
    if (width === 375) await page.screenshot({ path: `${OUT}/home-blocks-375.png`, fullPage: true })
  }
  console.log('HOME', JSON.stringify(notes))
})

test('table tab skeleton keeps the header and 56px rows', async ({ page }) => {
  const hold = await installApi(page, ['/tournaments', '/standings', '/statistics/board', '/playoff'])
  const notes: Record<string, unknown>[] = []
  for (const width of WIDTHS) {
    hold.arm()
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/table?tournament=t1', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.tab-skel .sk-row').first()).toBeVisible()
    const loading = await page.evaluate(() => {
      const row = document.querySelector('.tab-skel .sk-row')!
      const head = document.querySelector('.tab-skel .line.head')!
      return {
        panel: document.querySelector('.tab-panel')!.getBoundingClientRect().height,
        row: row.getBoundingClientRect().height,
        head: head.getBoundingClientRect().height,
      }
    })
    expect(loading.row).toBeGreaterThanOrEqual(55)
    expect(loading.row).toBeLessThanOrEqual(57)
    hold.open()
    await expect(page.locator('.sheet a.line').first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const loaded = await page.evaluate(() => {
      const row = document.querySelector('.sheet a.line')!
      const head = document.querySelector('.sheet .line.head')!
      return {
        panel: document.querySelector('.tab-panel')!.getBoundingClientRect().height,
        row: row.getBoundingClientRect().height,
        head: head.getBoundingClientRect().height,
      }
    })
    expect(loaded.row).toBeGreaterThanOrEqual(55)
    expect(loaded.row).toBeLessThanOrEqual(57)
    expectStable(loading.head, loaded.head, `table head ${width}`)
    expectStable(loading.panel, loaded.panel, `table panel ${width}`)
    notes.push({ width, loading, loaded })
  }
  console.log('TABLE', JSON.stringify(notes))
})
