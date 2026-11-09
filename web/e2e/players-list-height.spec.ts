import { expect, test, type Page, type Route } from '@playwright/test'

const WIDTHS = [320, 375, 1440] as const

function person(id: string, goals: number, assists: number) {
  return {
    playerId: id,
    firstName: 'Илья',
    lastName: 'Орлов',
    goals,
    assists,
    appearances: 8,
    teamId: 'ft',
    teamName: 'Физтех',
  }
}

const scorers = Array.from({ length: 5 }, (_, index) => person(`p${index}`, 12 - index, index))
const assists = [person('a0', 1, 6)]

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

async function installApi(page: Page) {
  const hold = createHold()
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    await hold.wait(path.endsWith('/statistics/board'))
    if (path.endsWith('/auth/me') || path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/statistics/board')) {
      const empty = url.searchParams.get('tournamentId') === 'empty'
      return json(route, empty ? { scorers: [], assists: [], goalkeepers: [] } : { scorers, assists, goalkeepers: [] })
    }
    if (path.endsWith('/teams')) return json(route, { content: [{ id: 'ft', name: 'Физтех' }], totalPages: 1 })
    if (path.endsWith('/tournaments')) {
      return json(route, {
        content: [
          { id: 't1', name: 'Лига', status: 'ACTIVE' },
          { id: 'empty', name: 'Пустой', status: 'ACTIVE' },
        ],
        totalPages: 1,
        totalElements: 2,
      })
    }
    return json(route, { content: [] })
  })
  return hold
}

async function block(page: Page) {
  return page.locator('.board-list').evaluate((el) => {
    const list = el.getBoundingClientRect().height
    const style = getComputedStyle(el)
    const head = el.querySelector('.cols.head')?.getBoundingClientRect().height ?? 0
    const empty = el.querySelector('.board-empty')
    const emptyBox = empty?.getBoundingClientRect()
    let centerDelta: number | null = null
    if (empty) {
      const range = document.createRange()
      range.selectNodeContents(empty)
      const text = range.getBoundingClientRect()
      centerDelta = Math.abs((text.top + text.height / 2) - (emptyBox!.top + emptyBox!.height / 2))
    }
    return {
      list,
      minHeight: style.minHeight,
      head,
      rows: el.querySelector('.board-rows')?.getBoundingClientRect().height ?? null,
      empty: emptyBox?.height ?? null,
      centerDelta,
    }
  })
}

function close(before: number, after: number, label: string) {
  expect(Math.abs(before - after), `${label}: ${before} -> ${after}`).toBeLessThanOrEqual(1)
}

test('players list keeps five rows plus the table header', async ({ page }) => {
  const hold = await installApi(page)
  const notes: Record<string, unknown>[] = []
  for (const width of WIDTHS) {
    hold.arm()
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/players', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.board-rows .skel').first()).toBeVisible()
    const skeleton = await block(page)
    hold.open()
    await expect(page.locator('.cols.line').first()).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const data = await block(page)
    await page.getByRole('button', { name: 'Передачи' }).click()
    await expect(page.locator('.cols.line')).toHaveCount(1)
    const assistsTab = await block(page)
    await page.getByRole('button', { name: 'Пустой' }).click()
    await expect(page.locator('.board-empty')).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const empty = await block(page)

    const header = width <= 720 ? 24 : 32
    expect(Math.abs(data.head - header)).toBeLessThanOrEqual(0.05)
    expect(Math.abs(skeleton.head - header)).toBeLessThanOrEqual(0.05)
    expect(data.minHeight).toBe(`${5 * 56 + header}px`)
    expect(empty.minHeight).toBe(`${5 * 56 + header}px`)
    expect(skeleton.minHeight).toBe(`${5 * 56 + header}px`)
    close(data.list, empty.list, `empty ${width}`)
    close(data.list, assistsTab.list, `tab ${width}`)
    close(data.list, skeleton.list, `skeleton ${width}`)
    close(empty.empty ?? 0, 5 * 56 + header, `empty box ${width}`)
    expect(empty.centerDelta ?? 99).toBeLessThanOrEqual(1)
    notes.push({ width, skeleton, data, assistsTab, empty })
  }
  console.log('PLAYERS_LIST', JSON.stringify(notes))
})
