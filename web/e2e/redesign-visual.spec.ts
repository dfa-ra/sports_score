import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'

function moscowToday() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

function at(day: string, hour: number, minute: number) {
  const utcHour = hour - 3
  return `${day}T${String(utcHour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00Z`
}

const today = moscowToday()
const tournaments = [
  ['liga', 'Лига ИТМО', 'Лига ИТМО'],
  ['live', 'QA Толян Live', 'QA Толян Live'],
  ['po16', 'QA Толян ПО-16', 'QA Толян ПО-16'],
  ['po8', 'QA Толян ПО-8', 'QA Толян ПО-8'],
  ['po4', 'QA Толян ПО-4', 'QA Толян ПО-4'],
  ['po2', 'QA Толян ПО-2', 'QA Толян ПО-2'],
  ['student', 'Студенческая лига', 'Студенческая'],
  ['cup', 'Кубок факультетов', 'Кубок'],
].map(([id, name, shortName]) => ({
  id,
  name,
  shortName,
  status: 'ACTIVE',
  format: 'GROUPS_PLAYOFF',
  competitionStage: 'PLAYOFF',
  teamsCount: 8,
  stageLabel: 'Плей-офф',
  progress: 0.4,
}))

function match(partial: Record<string, unknown>) {
  return {
    id: 'm',
    tournamentId: 'liga',
    tournamentName: 'Лига ИТМО',
    tournamentShortName: 'Лига ИТМО',
    scheduledAt: at(today, 19, 0),
    status: 'SCHEDULED',
    homeTeamId: 'home',
    homeTeamName: 'QA Толян Альфа',
    awayTeamId: 'away',
    awayTeamName: 'QA Толян Бета',
    homeScore: 0,
    awayScore: 0,
    minute: null,
    stageLabel: '1/2 финала',
    venue: 'QA Баннер зал',
    sportCode: 'FOOTBALL',
    ...partial,
  }
}

const live = match({
  id: 'live-1',
  tournamentId: 'live',
  tournamentName: 'QA Толян Live',
  tournamentShortName: 'QA Толян Live',
  status: 'LIVE',
  minute: 19,
  homeScore: 1,
  awayScore: 0,
  homeTeamName: 'QA 108-H1-164129',
  awayTeamName: 'QA 108-H2-164129',
})
const paused = match({
  id: 'paused-1',
  tournamentId: 'live',
  tournamentName: 'QA Толян Live',
  tournamentShortName: 'QA Толян Live',
  status: 'PAUSED',
  minute: 25,
  homeScore: 2,
  awayScore: 2,
  homeTeamName: 'Фикстура Пауза А',
  awayTeamName: 'Фикстура Пауза Б',
})
const next = match({
  id: 'next-1',
  status: 'SCHEDULED',
  scheduledAt: '2099-06-01T16:30:00Z',
  stageLabel: '1/2 финала',
})
const upcoming = tournaments.slice(0, 6).map((tour, index) => match({
  id: `up-${tour.id}`,
  tournamentId: tour.id,
  tournamentName: tour.name,
  tournamentShortName: tour.shortName,
  scheduledAt: at(today, 12 + index, 0),
  status: 'SCHEDULED',
}))
const recent = [match({
  id: 'recent-1',
  status: 'FINISHED',
  homeScore: 3,
  awayScore: 1,
  scheduledAt: at(today, 10, 0),
})]

const overview = {
  generatedAt: '2026-11-06T12:00:00Z',
  next,
  live: [live, paused],
  upcoming,
  recent,
  tournaments: tournaments.map((tour) => ({
    ...tour,
    lastMatch: recent[0],
    nextMatch: upcoming.find((row) => row.tournamentId === tour.id) ?? next,
    leader: { teamId: 'home', teamName: 'QA Толян Альфа' },
  })),
  scorersByTournament: tournaments.map((tour) => ({
    tournamentId: tour.id,
    tournamentName: tour.name,
    shortName: tour.shortName,
    scorers: [
      { playerId: 'p1', displayName: 'Толян Игроков', goals: 4, assists: 1, appearances: 3, teamName: 'QA Толян Альфа' },
      { playerId: 'p2', displayName: 'Толян Капитанов', goals: 2, assists: 0, appearances: 3, teamName: 'QA Толян Бета' },
    ],
  })),
  summary: { matchesPlayed: 46, goals: 78, goalsPerMatch: 1.7, teams: 53, players: 46 },
}

const calendarMatches = [
  ...upcoming,
  live,
  paused,
  match({
    id: 'cal-liga',
    tournamentId: 'liga',
    tournamentName: 'Лига ИТМО',
    tournamentShortName: 'Лига ИТМО',
    scheduledAt: at(today, 20, 0),
    stageLabel: '1/2 финала',
  }),
]

function pageOf(rows: unknown[]) {
  return { content: rows, totalElements: rows.length, totalPages: 1, page: 0, size: 100 }
}

function slot(partial: Record<string, unknown>) {
  return {
    id: 'slot',
    round: 1,
    slotIndex: 0,
    roundTitle: '1/8 финала',
    matchId: null,
    homeTeamId: null,
    homeTeamName: null,
    awayTeamId: null,
    awayTeamName: null,
    homeScore: null,
    awayScore: null,
    matchStatus: 'SCHEDULED',
    scheduledAt: '2026-11-21T12:00:00Z',
    venue: null,
    ...partial,
  }
}

function sixteenRounds() {
  const titles = ['1/8 финала', '1/4 финала', '1/2 финала', 'Финал']
  return titles.map((title, index) => {
    const count = 2 ** (titles.length - index - 1)
    return {
      round: index + 1,
      title,
      slots: Array.from({ length: count }, (_, slotIndex) => slot({
        id: `s16-${index}-${slotIndex}`,
        round: index + 1,
        slotIndex,
        roundTitle: title,
        matchId: index === 0 ? `m16-${slotIndex}` : `later-${index}-${slotIndex}`,
        homeTeamId: `h16-${index}-${slotIndex}`,
        awayTeamId: `a16-${index}-${slotIndex}`,
        homeTeamName: index === 0 ? `QA Толян ПО16 Т${slotIndex + 1}` : null,
        awayTeamName: index === 0 ? `QA Толян Бета ${slotIndex + 1}` : null,
        homeWaitingLabel: index === 0 ? null : `Победитель ${titles[index - 1].replace(' финала', '')} №${slotIndex * 2 + 1}`,
        awayWaitingLabel: index === 0 ? null : `Победитель ${titles[index - 1].replace(' финала', '')} №${slotIndex * 2 + 2}`,
        homeScore: index === 0 ? 1 : null,
        awayScore: index === 0 ? 0 : null,
        matchStatus: index === 0 ? 'FINISHED' : 'SCHEDULED',
        scheduledAt: `2026-11-${20 + index}T${String(12 + (slotIndex % 6)).padStart(2, '0')}:00:00Z`,
      })),
    }
  })
}

const standings = {
  tables: [
    {
      id: 'a',
      name: 'Группа A',
      sortOrder: 1,
      rows: [
        { teamId: 'home', teamName: 'QA Толян Альфа', played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, points: 0 },
        { teamId: 'away', teamName: 'QA Толян Бета', played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, points: 0 },
      ],
    },
    {
      id: 'b',
      name: 'Группа B',
      sortOrder: 2,
      rows: [
        { teamId: 'g', teamName: 'QA Толян Гамма', played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, points: 0 },
        { teamId: 'd', teamName: 'QA Толян Дельта', played: 0, wins: 0, draws: 0, losses: 0, goalsFor: 0, goalsAgainst: 0, points: 0 },
      ],
    },
  ],
}

async function fulfill(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function install(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const url = new URL(route.request().url())
    if (path.endsWith('/home/overview')) return fulfill(route, overview)
    if (path.endsWith('/auth/me')) return fulfill(route, { message: 'no' }, 401)
    if (path.endsWith('/matches') && !path.includes('/matches/')) {
      const status = url.searchParams.get('status')
      const rows = status === 'LIVE'
        ? calendarMatches.filter((row) => row.status === 'LIVE')
        : status === 'PAUSED'
          ? calendarMatches.filter((row) => row.status === 'PAUSED')
          : calendarMatches
      return fulfill(route, pageOf(rows))
    }
    if (path.endsWith('/matches/next-1')) return fulfill(route, next)
    if (path.endsWith('/matches/next-1/events')) return fulfill(route, [])
    if (path.endsWith('/matches/next-1/referees')) return fulfill(route, [])
    if (path.endsWith('/matches/next-1/lineups')) return fulfill(route, { home: null, away: null })
    if (path.includes('/teams/') && path.endsWith('/form')) {
      return fulfill(route, [match({
        id: 'form-1',
        status: 'FINISHED',
        homeScore: 2,
        awayScore: 0,
        homeTeamId: 'home',
        awayTeamId: 'away',
      })])
    }
    if (path.includes('/teams/') && path.endsWith('/members')) return fulfill(route, [])
    if (path.endsWith('/tournaments/liga/standings')) return fulfill(route, standings)
    if (path.endsWith('/tournaments/liga/playoff')) return fulfill(route, { rounds: sixteenRounds() })
    if (path.endsWith('/tournaments/liga/overview')) {
      return fulfill(route, {
        name: 'Лига ИТМО',
        format: 'GROUPS_PLAYOFF',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: 16,
      })
    }
    if (path.endsWith('/tournaments/liga')) {
      return fulfill(route, {
        id: 'liga',
        name: 'Лига ИТМО',
        status: 'ACTIVE',
        format: 'GROUPS_PLAYOFF',
        competitionStage: 'PLAYOFF',
        seasonYear: 2026,
        seasonLabel: 'Сезон 2026',
        startDate: '2026-09-01',
        endDate: '2026-12-20',
      })
    }
    if (path.endsWith('/tournaments/liga/teams') || path.endsWith('/tournaments/liga/tables')) return fulfill(route, [])
    if (path.endsWith('/tournaments/liga/matches')) return fulfill(route, pageOf([]))
    if (path.endsWith('/statistics/summary')) return fulfill(route, { matchesPlayed: 0, goals: 0, goalsPerMatch: 0 })
    if (path.endsWith('/tournaments/current')) return fulfill(route, { id: 'liga', name: 'Лига ИТМО' })
    if (path.endsWith('/tournaments')) return fulfill(route, { content: tournaments, totalElements: tournaments.length })
    if (path.endsWith('/statistics/board')) return fulfill(route, { scorers: [], assists: [], goalkeepers: [] })
    return fulfill(route, { content: [] })
  })
}

async function stamp(page: Page) {
  await page.evaluate(() => {
    const note = document.createElement('div')
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:10000;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);'
    document.body.appendChild(note)
  })
}

async function noPageScroll(page: Page) {
  const box = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(box.scrollWidth).toBeLessThanOrEqual(box.clientWidth + 1)
}

async function labelsFitScroller(page: Page, scroller: string) {
  const report = await page.locator(scroller).evaluate((bar) => {
    const nodes = [...bar.querySelectorAll('button')].filter((node) => node instanceof HTMLElement) as HTMLElement[]
    return nodes.map((button) => {
      const range = document.createRange()
      range.selectNodeContents(button)
      const text = range.getBoundingClientRect()
      const before = button.getBoundingClientRect()
      const host = bar.getBoundingClientRect()
      const contentRight = before.right - host.left + bar.scrollLeft
      const contentLeft = before.left - host.left + bar.scrollLeft
      button.scrollIntoView({ inline: 'nearest', block: 'nearest' })
      const after = range.getBoundingClientRect()
      const scrolled = bar.getBoundingClientRect()
      return {
        text: (button.innerText || '').replace(/\s+/g, ' ').trim(),
        selfClipped: button.scrollWidth > button.clientWidth + 1,
        inScrollRange: contentLeft >= -1 && contentRight <= bar.scrollWidth + 1,
        visible: after.width > 0
          && after.left >= scrolled.left - 1
          && after.right <= scrolled.right + 1
          && after.top >= scrolled.top - 2
          && after.bottom <= scrolled.bottom + 2,
      }
    })
  })
  expect(report.length).toBeGreaterThan(1)
  for (const item of report) {
    expect(item.selfClipped, item.text).toBe(false)
    expect(item.inScrollRange, item.text).toBe(true)
    expect(item.visible, item.text).toBe(true)
    expect(item.text.length).toBeGreaterThan(1)
  }
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('home chips, tabs, pause chip and header gap stay intact', async ({ page }) => {
  await install(page)
  for (const width of [1440, 720, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 1400 : 1200 })
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Матчи' })).toBeVisible()
    await expect(page.locator('.livebar .lv').nth(1).locator('.badge.paused')).toHaveText('Пауза')
    await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
    await labelsFitScroller(page, '.filters .chips')
    await labelsFitScroller(page, '.tabs')
    const underline = await page.locator('.tabs button.on').evaluate((button) => {
      const range = document.createRange()
      range.selectNodeContents(button)
      const text = range.getBoundingClientRect()
      const box = button.getBoundingClientRect()
      const shadow = getComputedStyle(button).boxShadow
      return {
        centered: Math.abs((text.left + text.right) / 2 - (box.left + box.right) / 2) <= 2,
        shadow: shadow.includes('rgb'),
        textInside: text.left >= box.left - 1 && text.right <= box.right + 1,
      }
    })
    expect(underline.centered).toBe(true)
    expect(underline.shadow).toBe(true)
    expect(underline.textInside).toBe(true)
    const columns = await page.locator('.sgrid .stat').evaluateAll((nodes) => {
      const widths = nodes.map((node) => node.getBoundingClientRect().width)
      return { count: widths.length, spread: Math.max(...widths) - Math.min(...widths) }
    })
    expect(columns.count).toBe(5)
    expect(columns.spread).toBeLessThanOrEqual(2)
    await noPageScroll(page)
    if (width === 375) {
      const gap = await page.evaluate(() => {
        const login = document.querySelector('.login-pill')!.getBoundingClientRect()
        const burger = document.querySelector('.menu-button')!.getBoundingClientRect()
        return burger.left - login.right
      })
      expect(gap).toBeGreaterThanOrEqual(18)
      const peek = await page.locator('.feed-cards').evaluate((bar) => {
        const cards = [...bar.querySelectorAll('.mc')] as HTMLElement[]
        if (cards.length < 2) return { cards: cards.length, peek: 0, gap: 0, firstInside: false }
        const host = bar.getBoundingClientRect()
        const first = cards[0].getBoundingClientRect()
        const second = cards[1].getBoundingClientRect()
        const visibleRight = Math.min(second.right, host.right)
        return {
          cards: cards.length,
          peek: visibleRight - Math.max(second.left, host.left),
          gap: second.left - first.right,
          firstInside: first.left >= host.left - 1 && first.right <= host.right + 1,
        }
      })
      expect(peek.cards).toBeGreaterThan(1)
      expect(peek.firstInside).toBe(true)
      expect(peek.gap).toBeGreaterThanOrEqual(8)
      expect(peek.peek).toBeGreaterThanOrEqual(12)
      expect(peek.peek).toBeLessThanOrEqual(72)
    }
  }
  await page.setViewportSize({ width: 1440, height: 1400 })
  await page.goto('/')
  await expect(page.getByText('Лига в цифрах')).toBeVisible()
  await stamp(page)
  await page.screenshot({ path: `${OUT}/redesign-home-1440.png`, fullPage: true })
  await page.setViewportSize({ width: 375, height: 1400 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Матчи' })).toBeVisible()
  await stamp(page)
  await page.screenshot({ path: `${OUT}/redesign-home-375.png`, fullPage: true })
})

test('match stars, empty events and header gap', async ({ page }) => {
  await install(page)
  for (const width of [1440, 720, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 900 : 1000 })
    await page.goto('/matches/next-1')
    await expect(page.getByText('Событий пока нет')).toBeVisible()
    await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
    await expect(page.locator('body')).not.toContainText('Очные')
    await expect(page.locator('body')).not.toContainText('Секретар')
    const stars = await page.locator('.pitch-side').evaluateAll((nodes) => nodes.map((node) => {
      const star = node.querySelector('.fav-star')!.getBoundingClientRect()
      const crest = node.querySelector('.team-crest, .pitch-who')!.getBoundingClientRect()
      return { starTop: star.top, crestTop: crest.top }
    }))
    expect(stars).toHaveLength(2)
    if (width > 720) {
      expect(stars[0].starTop).toBeLessThan(stars[0].crestTop)
      expect(stars[1].starTop).toBeLessThan(stars[1].crestTop)
    } else {
      const beside = await page.locator('.pitch-side').evaluateAll((nodes) => nodes.map((node) => {
        const star = node.querySelector('.fav-star')!.getBoundingClientRect()
        const crest = node.querySelector('.team-crest')!.getBoundingClientRect()
        const starMid = star.top + star.height / 2
        const crestMid = crest.top + crest.height / 2
        return { starMid, crestMid, starLeft: star.left, crestLeft: crest.left }
      }))
      expect(Math.abs(beside[0].starMid - beside[0].crestMid)).toBeLessThanOrEqual(8)
      expect(Math.abs(beside[1].starMid - beside[1].crestMid)).toBeLessThanOrEqual(8)
      expect(beside[0].starLeft).toBeLessThan(beside[0].crestLeft)
      expect(beside[1].starLeft).toBeGreaterThan(beside[1].crestLeft)
    }
    expect(Math.abs(stars[0].starTop - stars[1].starTop)).toBeLessThanOrEqual(2)
    const underline = await page.locator('.match-tabs button.on .tab-label').evaluate((label) => {
      const text = label.getBoundingClientRect()
      const button = label.parentElement!.getBoundingClientRect()
      const border = getComputedStyle(label).borderBottomColor
      return {
        centered: Math.abs((text.left + text.right) / 2 - (button.left + button.right) / 2) <= 2,
        ink: border,
      }
    })
    expect(underline.centered).toBe(true)
    expect(underline.ink).not.toBe('rgba(0, 0, 0, 0)')
    const gap = await page.locator('.card.events').evaluate((card) => {
      const title = card.querySelector('header')!.getBoundingClientRect()
      const empty = card.querySelector('.empty-events')!.getBoundingClientRect()
      return { between: empty.top - title.bottom, card: card.getBoundingClientRect().height }
    })
    expect(gap.between).toBeLessThan(28)
    expect(gap.card).toBeLessThan(120)
    await noPageScroll(page)
    if (width === 375) {
      const headerGap = await page.evaluate(() => {
        const login = document.querySelector('.login-pill')!.getBoundingClientRect()
        const burger = document.querySelector('.menu-button')!.getBoundingClientRect()
        return burger.left - login.right
      })
      expect(headerGap).toBeGreaterThanOrEqual(18)
    }
    if (width === 1440 || width === 375) {
      await stamp(page)
      await page.screenshot({ path: `${OUT}/redesign-match-${width}.png`, fullPage: true })
    }
  }
})

test('calendar chips stay inside the scroller', async ({ page }) => {
  await install(page)
  for (const width of [1440, 720, 375]) {
    await page.setViewportSize({ width, height: width === 375 ? 1000 : 900 })
    await page.goto('/calendar')
    await expect(page.getByRole('button', { name: 'Лига ИТМО' })).toBeVisible()
    await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
    await labelsFitScroller(page, '.filters')
    const align = await page.evaluate(() => {
      const filtersEl = document.querySelector('.filters') as HTMLElement
      filtersEl.scrollLeft = 0
      const hero = document.querySelector('.hero')!.getBoundingClientRect()
      const filters = filtersEl.getBoundingClientRect()
      const first = filtersEl.querySelector('button')!.getBoundingClientRect()
      return {
        filtersLeft: filters.left,
        heroLeft: hero.left,
        firstLeft: first.left,
      }
    })
    expect(align.filtersLeft).toBeGreaterThanOrEqual(align.heroLeft - 1)
    expect(align.firstLeft).toBeGreaterThanOrEqual(align.heroLeft - 1)
    await noPageScroll(page)
    if (width === 1440 || width === 375) {
      await stamp(page)
      await page.screenshot({ path: `${OUT}/redesign-calendar-${width}.png`, fullPage: true })
    }
  }
})

test('playoff connectors meet card centers and the phone header stays static', async ({ page }) => {
  await install(page)
  await page.setViewportSize({ width: 1440, height: 1100 })
  await page.goto('/tournaments/liga?tab=playoff')
  await expect(page.getByText('1/8 финала').first()).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
  await expect(page.locator('.b-bronze, .pc.bronze')).toHaveCount(0)
  await page.waitForTimeout(200)
  const links = await page.locator('.po-grid').evaluate((grid) => {
    const cells = [...grid.querySelectorAll<HTMLElement>('.cellc[data-from-round]')]
    return cells.map((cell) => {
      const from = Number(cell.dataset.fromRound)
      const index = Number(cell.dataset.slotIndex)
      const box = cell.getBoundingClientRect()
      const targets = [...grid.querySelectorAll<HTMLElement>(`.cell[data-round="${from + 1}"] .pc`)]
      const target = targets[index]
      const exit = cell.querySelectorAll('path')[2]?.getAttribute('d') || ''
      const match = exit.match(/M50 ([0-9.]+)/)
      const y = match ? Number(match[1]) : -1
      const center = target
        ? ((target.getBoundingClientRect().top + target.getBoundingClientRect().height / 2 - box.top) / box.height) * 100
        : -1
      return { y, center, delta: Math.abs(y - center) }
    })
  })
  expect(links.length).toBeGreaterThan(0)
  for (const link of links) expect(link.delta).toBeLessThanOrEqual(4)
  await noPageScroll(page)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/redesign-playoff-1440.png`, fullPage: true })

  await page.setViewportSize({ width: 720, height: 900 })
  await page.goto('/tournaments/liga?tab=playoff')
  await expect(page.getByText('1/8 финала').first()).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
  await noPageScroll(page)

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/tournaments/liga?tab=playoff')
  await expect(page.getByText('1/8 финала').first()).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'static')
  const footers = await page.locator('.po-panel.on .pf').evaluateAll((nodes) => nodes.map((node) => {
    const box = node.getBoundingClientRect()
    const bits = [...node.children].map((child) => child.getBoundingClientRect())
    const tops = bits.map((bit) => bit.top)
    return { height: box.height, spread: tops.length ? Math.max(...tops) - Math.min(...tops) : 0 }
  }))
  expect(footers.length).toBeGreaterThan(1)
  const heights = footers.map((row) => row.height)
  expect(Math.max(...heights) - Math.min(...heights)).toBeLessThanOrEqual(2)
  for (const row of footers) expect(row.spread).toBeLessThanOrEqual(2)
  await noPageScroll(page)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/redesign-playoff-375.png`, fullPage: true })
})

test('table tabs, footnote and place numbers', async ({ page }) => {
  await install(page)
  await page.setViewportSize({ width: 375, height: 1000 })
  await page.goto('/table?tournament=liga')
  await expect(page.getByRole('button', { name: 'Игроки' })).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
  await labelsFitScroller(page, '.league-tabs')
  const rows = await page.locator('.league-tabs').evaluate((bar) => {
    const buttons = [...bar.querySelectorAll('button')] as HTMLElement[]
    const tops = buttons.map((button) => button.getBoundingClientRect().top)
    return { lines: new Set(tops.map((top) => Math.round(top))).size }
  })
  expect(rows.lines).toBeLessThanOrEqual(2)
  const rank = await page.locator('.rank').first().evaluate((node) => {
    const box = node.getBoundingClientRect()
    const range = document.createRange()
    range.selectNodeContents(node)
    const text = range.getBoundingClientRect()
    return {
      dx: Math.abs((text.left + text.right) / 2 - (box.left + box.right) / 2),
      dy: Math.abs((text.top + text.bottom) / 2 - (box.top + box.bottom) / 2),
    }
  })
  expect(rank.dx).toBeLessThanOrEqual(1.5)
  expect(rank.dy).toBeLessThanOrEqual(1.5)
  await noPageScroll(page)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/redesign-table-375.png`, fullPage: true })

  await page.setViewportSize({ width: 720, height: 1000 })
  await page.goto('/table?tournament=liga')
  await expect(page.getByRole('button', { name: 'Игроки' })).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
  await labelsFitScroller(page, '.league-tabs')
  await noPageScroll(page)

  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/table?tournament=liga')
  await expect(page.getByText('Группа A')).toBeVisible()
  await expect(page.locator('.chrome')).toHaveCSS('position', 'sticky')
  await noPageScroll(page)
  await stamp(page)
  await page.screenshot({ path: `${OUT}/redesign-table-1440.png`, fullPage: true })
})
