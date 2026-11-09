import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const teamId = '00000000-0000-4000-8000-0000000000d2'
const captainId = '00000000-0000-4000-8000-0000000000c1'
const longId = '00000000-0000-4000-8000-0000000000c2'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function shot(page: Page, name: string) {
  mkdirSync(OUT, { recursive: true })
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true })
}

async function noHorizontalScroll(page: Page, selector?: string) {
  return page.evaluate((target) => {
    const root = document.documentElement
    const pageOverflow = root.scrollWidth - root.clientWidth
    const node = target ? document.querySelector(target) : null
    const box = node
      ? { scroll: node.scrollWidth - node.clientWidth, overflow: getComputedStyle(node).overflowX }
      : null
    return { pageOverflow, box }
  }, selector)
}

test('players sheet title follows the goals and passes tabs', async ({ page }) => {
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/statistics/board') {
      return json(route, {
        scorers: [
          { playerId: 'p1', firstName: 'Илья', lastName: 'Орлов', goals: 12, assists: 2, appearances: 10, teamId: 'ft' },
        ],
        assists: [
          { playerId: 'p2', firstName: 'Артём', lastName: 'Белов', goals: 5, assists: 7, appearances: 10, teamId: 'pu' },
        ],
      })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, { content: [{ id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' }], totalPages: 1 })
    }
    if (path === '/api/v1/teams') {
      return json(route, {
        content: [
          { id: 'ft', name: 'Физтех', logoUrl: '' },
          { id: 'pu', name: 'Пумы', logoUrl: '' },
        ],
        totalPages: 1,
      })
    }
    return json(route, { content: [] })
  })

  for (const width of [375, 1440]) {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
    await page.goto('/players')
    expect(await page.evaluate(() => window.devicePixelRatio)).toBe(1)
    const sheet = page.locator('.board-head h2')
    await expect(sheet).toHaveText('Бомбардиры')
    await expect(sheet).toHaveCSS('text-transform', 'uppercase')
    await expect(page.getByRole('button', { name: 'Голы' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Передачи' })).toBeVisible()
    const eyebrow = page.locator('.leader p').nth(1)
    await expect(page.locator('.leader p').nth(0)).toHaveText('Бомбардир')
    await expect(eyebrow).toHaveText('Лучший по передачам')
    await expect(eyebrow).toHaveCSS('text-transform', 'uppercase')
    const eyebrowBox = await eyebrow.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      const card = el.closest('.leader')?.getBoundingClientRect()
      return {
        clipped: el.scrollWidth > el.clientWidth + 1,
        right: rect.right,
        cardRight: card?.right ?? rect.right,
        view: window.innerWidth,
        pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      }
    })
    expect(eyebrowBox.clipped).toBe(false)
    expect(eyebrowBox.right).toBeLessThanOrEqual(eyebrowBox.cardRight + 1)
    expect(eyebrowBox.right).toBeLessThanOrEqual(eyebrowBox.view + 1)
    expect(eyebrowBox.pageOverflow).toBeLessThanOrEqual(1)

    await page.getByRole('button', { name: 'Передачи' }).click()
    await expect(sheet).toHaveText('Лидеры по передачам')
    await expect(sheet).toHaveCSS('text-transform', 'uppercase')
    await expect(page.getByRole('button', { name: 'Передачи' })).toHaveClass(/on/)
    const headingBox = await sheet.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      return {
        text: el.textContent,
        clipped: el.scrollWidth > el.clientWidth + 1,
        right: rect.right,
        view: window.innerWidth,
      }
    })
    expect(headingBox.text).toBe('Лидеры по передачам')
    expect(headingBox.clipped).toBe(false)
    expect(headingBox.right).toBeLessThanOrEqual(headingBox.view + 1)
    if (width === 1440) {
      const lines = await sheet.evaluate((el) => {
        const range = document.createRange()
        range.selectNodeContents(el)
        return range.getClientRects().length
      })
      expect(lines).toBe(1)
    }

    if (width === 375) await shot(page, 'q2-players-assists-title-375')

    await page.getByRole('button', { name: 'Голы' }).click()
    await expect(sheet).toHaveText('Бомбардиры')
  }
})

test('team player table fits the numeric headers at 375 and keeps the desktop columns', async ({ page }) => {
  const longName = 'Александровна Преображенская-Воскресенская'
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === `/api/v1/teams/${teamId}`) {
      return json(route, {
        id: teamId,
        name: 'Физтех',
        shortName: 'Физтех',
        captainId,
      })
    }
    if (path === `/api/v1/teams/${teamId}/members`) {
      return json(route, [
        { id: 'm1', playerId: captainId, firstName: 'Илья', lastName: 'Орлов' },
        { id: 'm2', playerId: longId, firstName: longName, lastName: 'Белова' },
      ])
    }
    if (path === `/api/v1/teams/${teamId}/player-stats`) {
      return json(route, [
        { playerId: captainId, firstName: 'Илья', lastName: 'Орлов', goals: 4, assists: 2, appearances: 8, yellowCards: 1 },
        { playerId: longId, firstName: longName, lastName: 'Белова', goals: 1, assists: 3, appearances: 7, yellowCards: 0 },
      ])
    }
    if (path === '/api/v1/teams') {
      return json(route, { content: [{ id: teamId, name: 'Физтех', shortName: 'Физтех' }], totalPages: 1 })
    }
    if (path === '/api/v1/matches') return json(route, { content: [] })
    return json(route, { content: [] })
  })

  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`/teams/${teamId}?tab=stats`)
  expect(await page.evaluate(() => window.devicePixelRatio)).toBe(1)
  const table = page.locator('.stat-table')
  await expect(table).toBeVisible()
  await expect(page.locator('.assists-short')).toHaveText('Пер.')
  await expect(page.locator('.assists-wide')).toBeHidden()

  const phone = await table.evaluate((node) => {
    const headers = [...node.querySelectorAll('th')].map((th) => {
      const rect = th.getBoundingClientRect()
      return {
        text: th.innerText.replace(/\s+/g, ' ').trim(),
        clipped: th.scrollWidth > th.clientWidth + 1,
        left: rect.left,
        right: rect.right,
      }
    })
    const names = [...node.querySelectorAll('a')].map((link) => {
      const rect = link.getBoundingClientRect()
      const cell = link.closest('td')?.getBoundingClientRect()
      const range = document.createRange()
      const textNode = link.firstChild
      const full = textNode?.textContent || ''
      const pieces: string[] = []
      if (textNode && full) {
        let from = 0
        for (let i = 1; i <= full.length; i++) {
          range.setStart(textNode, from)
          range.setEnd(textNode, i)
          const box = range.getClientRects()
          const wrapped = box.length > 1
          if (wrapped && i > from + 1) {
            pieces.push(full.slice(from, i - 1))
            from = i - 1
          }
        }
        pieces.push(full.slice(from))
      }
      range.selectNodeContents(link)
      const lines = [...range.getClientRects()].map((line) => Math.round(line.width))
      return {
        text: link.textContent?.trim() || '',
        width: rect.width,
        left: rect.left,
        right: rect.right,
        cellRight: cell?.right ?? rect.right,
        lines,
        pieces,
      }
    })
    return {
      headers,
      names,
      layout: getComputedStyle(node).tableLayout,
      align: getComputedStyle(node.querySelector('td.num') as Element).textAlign,
    }
  })
  expect(phone.headers.map((header) => header.text)).toEqual(['Фамилия Имя', 'Голы', 'Пер.', 'Игры', 'ЖК'])
  for (const header of phone.headers) {
    expect(header.clipped, header.text).toBe(false)
    expect(header.left).toBeGreaterThanOrEqual(-1)
    expect(header.right).toBeLessThanOrEqual(376)
  }
  const shortName = phone.names.find((row) => row.text.includes('Орлов'))
  const longNameCell = phone.names.find((row) => row.text.includes('Белова'))
  expect(shortName?.pieces).toEqual(['Орлов Илья'])
  expect(longNameCell?.pieces.map((part) => part.trim())).toEqual([
    'Белова',
    'Александровна',
    'Преображенская-',
    'Воскресенская',
  ])
  expect(longNameCell && longNameCell.width).toBeGreaterThan(70)
  expect(longNameCell && longNameCell.right).toBeLessThanOrEqual((longNameCell.cellRight) + 1)
  expect(longNameCell && longNameCell.right).toBeLessThanOrEqual(376)
  const badge = await page.locator('.stat-table .captain-badge').evaluate((el) => {
    const badgeBox = el.getBoundingClientRect()
    const cell = el.closest('td')?.getBoundingClientRect()
    return { width: badgeBox.width, cell: cell?.width ?? badgeBox.width }
  })
  expect(badge.width).toBeGreaterThan(40)
  expect(badge.width).toBeLessThan(badge.cell - 24)
  expect(phone.layout).toBe('fixed')
  expect(phone.align).toBe('right')
  const phoneScroll = await noHorizontalScroll(page, '.stats-card .table-wrap')
  expect(phoneScroll.pageOverflow).toBeLessThanOrEqual(1)
  expect(phoneScroll.box?.scroll ?? 0).toBeLessThanOrEqual(1)
  await shot(page, 'q2-team-columns-375')

  await page.setViewportSize({ width: 1440, height: 900 })
  await expect(page.locator('.assists-wide')).toHaveText('Передачи')
  await expect(page.locator('.assists-short')).toBeHidden()
  const desktop = await table.evaluate((node) => {
    const num = node.querySelector('td.num') as HTMLElement
    const link = node.querySelector('a') as HTMLElement
    const head = node.querySelector('th.who-h') as HTMLElement
    const wrap = node.closest('.table-wrap') as HTMLElement
    return {
      layout: getComputedStyle(node).tableLayout,
      numWidth: getComputedStyle(num).width,
      align: getComputedStyle(num).textAlign,
      headAlign: getComputedStyle(head).textAlign,
      nameWrap: getComputedStyle(link).whiteSpace,
      whoDisplay: getComputedStyle(node.querySelector('.who') as Element).display,
      overflow: getComputedStyle(wrap).overflowX,
    }
  })
  expect(desktop.layout).toBe('auto')
  expect(desktop.align).toBe('right')
  expect(desktop.headAlign).toBe('left')
  expect(desktop.nameWrap).toBe('nowrap')
  expect(desktop.whoDisplay).toBe('flex')
  expect(desktop.overflow).toBe('auto')
  expect(Number.parseFloat(desktop.numWidth)).toBeGreaterThan(48)
})

test('table passes tab keeps the designer copy and the existing column width', async ({ page }) => {
  let assists: unknown[] = [
    { playerId: 'p1', firstName: 'Артём', lastName: 'Белов', assists: 4, appearances: 6, teamId: 'ft' },
  ]
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/tournaments') {
      return json(route, { content: [{ id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' }], totalPages: 1 })
    }
    if (path === '/api/v1/tournaments/current') return json(route, { id: 'liga', name: 'Лига ИТМО' })
    if (path === '/api/v1/tournaments/liga') return json(route, { id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' })
    if (path === '/api/v1/tournaments/liga/standings') return json(route, [])
    if (path === '/api/v1/tournaments/liga/playoff') return json(route, { rounds: [] })
    if (path === '/api/v1/statistics/board') return json(route, { scorers: [], assists, goalkeepers: [] })
    if (path === '/api/v1/teams') return json(route, { content: [{ id: 'ft', name: 'Физтех' }], totalPages: 1 })
    if (path === '/api/v1/matches') return json(route, { content: [] })
    return json(route, { content: [] })
  })

  for (const width of [375, 1440]) {
    await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
    await page.goto('/table?tournament=liga&tab=assists')
    expect(await page.evaluate(() => window.devicePixelRatio)).toBe(1)
    await expect(page.getByRole('button', { name: 'Лидеры по передачам' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Лидеры по передачам' })).toBeVisible()
    const head = page.locator('.pass-col')
    const facts = await head.evaluate((el) => {
      const num = el.closest('table')?.querySelector('td.num') as HTMLElement | null
      return {
        text: el.innerText.replace(/\s+/g, ' ').trim(),
        clipped: el.scrollWidth > el.clientWidth + 1,
        width: num ? getComputedStyle(num).width : '',
        transform: getComputedStyle(el).textTransform,
      }
    })
    expect(facts.text).toBe(width === 375 ? 'Пер.' : 'Передачи')
    expect(facts.clipped).toBe(false)
    expect(facts.transform).toBe('none')
    const px = Number.parseFloat(facts.width)
    expect(Math.abs(px - (width === 375 ? 66.4 : 76.8))).toBeLessThan(0.2)
  }

  assists = []
  await page.goto('/table?tournament=liga&tab=assists')
  await expect(page.getByText('Передач ещё нет')).toBeVisible()
})
