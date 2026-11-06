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
const longTab = 'Студенческий кубок факультетов, общежитий и колледжей ИТМО — осенний сезон'

const tournaments = [
  ['liga', 'Лига ИТМО', 'Лига ИТМО'],
  ['live', 'QA Толян Live', 'QA Толян Live'],
  ['po16', 'QA Толян ПО-16', 'QA Толян ПО-16'],
  ['po8', 'QA Толян ПО-8', 'QA Толян ПО-8'],
  ['cup', 'Кубок факультетов', 'Кубок факультетов'],
  ['student', longTab, longTab],
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
    homeScore: null,
    awayScore: null,
    stageLabel: '1/2 финала',
    ...partial,
  }
}

const next = match({ id: 'next-1', scheduledAt: at(today, 21, 0) })
const upcoming = [
  match({ id: 'up-1', tournamentId: 'po16', tournamentName: 'QA Толян ПО-16', tournamentShortName: 'QA Толян ПО-16', scheduledAt: at(today, 18, 0) }),
  match({ id: 'up-2', tournamentId: 'cup', tournamentName: 'Кубок факультетов', tournamentShortName: 'Кубок', scheduledAt: at(today, 16, 30) }),
]
const recent = [
  match({ id: 'recent-1', status: 'FINISHED', homeScore: 3, awayScore: 1, scheduledAt: at(today, 10, 0) }),
]

const overview = {
  generatedAt: '2026-11-06T12:00:00Z',
  next,
  live: [],
  upcoming,
  recent,
  tournaments: tournaments.map((tour) => ({
    ...tour,
    lastMatch: recent[0],
    nextMatch: upcoming[0],
    leader: { teamId: 'home', teamName: 'QA Толян Альфа' },
  })),
  scorersByTournament: tournaments.map((tour) => ({
    tournamentId: tour.id,
    tournamentName: tour.name,
    shortName: tour.shortName,
    scorers: [
      { playerId: 'p1', displayName: 'Толян Игроков', goals: 4, assists: 1, appearances: 3, teamName: 'QA Толян Альфа' },
    ],
  })),
  summary: { matchesPlayed: 46, goals: 78, goalsPerMatch: 1.7, teams: 53, players: 46 },
}

const calendarMatches = tournaments.map((tour, index) => match({
  id: `cal-${tour.id}`,
  tournamentId: tour.id,
  tournamentName: tour.name,
  tournamentShortName: tour.shortName,
  scheduledAt: at(today, 12 + index, 0),
  status: 'FINISHED',
  homeScore: 1,
  awayScore: 0,
}))

function pageOf(rows: unknown[]) {
  return { content: rows, totalElements: rows.length, totalPages: 1, page: 0, size: 100 }
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
    if (path.endsWith('/teams')) return fulfill(route, { content: [] })
    if (path.endsWith('/matches')) {
      const status = url.searchParams.get('status')
      if (status === 'LIVE' || status === 'PAUSED') return fulfill(route, pageOf([]))
      return fulfill(route, pageOf(calendarMatches))
    }
    if (path.endsWith('/tournaments')) return fulfill(route, { content: tournaments, totalElements: tournaments.length })
    return fulfill(route, { content: [] })
  })
}

async function stamp(page: Page) {
  await page.evaluate(() => {
    document.getElementById('fixture-stamp')?.remove()
    const note = document.createElement('div')
    note.id = 'fixture-stamp'
    note.textContent = 'Тестовый стенд · фикстура, не прод'
    note.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:10000;background:#fff;color:#00205b;border-radius:8px;padding:4px 8px;font:700 12px/1.2 sans-serif;box-shadow:0 2px 8px rgba(0,32,91,.15);'
    document.body.appendChild(note)
  })
}

async function assertTextFits(page: Page, selector: string) {
  const report = await page.locator(selector).evaluateAll((nodes) => nodes.flatMap((node) => {
    if (!(node instanceof HTMLElement)) return []
    const style = getComputedStyle(node)
    if (style.display === 'none' || style.visibility === 'hidden' || node.getClientRects().length === 0) return []
    const full = node.dataset.full || node.getAttribute('title') || (node.textContent || '')
    return [{
      text: (node.textContent || '').replace(/\s+/g, ' ').trim(),
      full,
      title: node.getAttribute('title') || node.parentElement?.getAttribute('title') || '',
      clipped: node.scrollWidth > node.clientWidth + 1,
    }]
  }))
  expect(report.length).toBeGreaterThan(0)
  for (const item of report) {
    expect(item.clipped, item.text).toBe(false)
    expect(item.text.length).toBeGreaterThan(0)
    if (item.title) expect(item.title).toBe(item.full)
    if (item.text !== item.full) expect(item.text.endsWith('…'), item.text).toBe(true)
  }
  return report
}

test.beforeAll(() => {
  mkdirSync(OUT, { recursive: true })
})

test('chip and tab labels are not clipped at 375, 719, and 1440', async ({ page }) => {
  await install(page)
  for (const width of [375, 719, 1440]) {
    await page.setViewportSize({ width, height: width === 375 ? 1500 : 1100 })
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Матчи' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Бомбардиры по турнирам' })).toBeVisible()
    const labels = await assertTextFits(page, '.chips .chip-label, .tabs .tab-label')
    const long = labels.find((item) => item.full === longTab)
    expect(long, 'long scorer tab').toBeTruthy()
    if (width <= 719) expect(long?.text.endsWith('…')).toBe(true)
    await expect(page.locator('.tabs button.on .tab-label')).toHaveAttribute('title', /.+/)
    if (width === 375) {
      await expect(page.locator('.filters .scroll-fade-edge.right')).toBeVisible()
      await expect(page.locator('.scroll-fade:has(.feed-cards) .scroll-fade-edge.right')).toBeVisible()
    }
    if (width === 719 || width === 1440) {
      const tabs = await page.locator('.scroll-fade:has(.tabs)').evaluate((el) => {
        const row = el.firstElementChild
        if (!(row instanceof HTMLElement)) return { overflow: false, whole: false }
        const host = row.getBoundingClientRect()
        const buttons = [...row.querySelectorAll('button')]
        return {
          overflow: row.scrollWidth > row.clientWidth + 2,
          whole: buttons.length > 0 && buttons.every((button) => {
            const box = button.getBoundingClientRect()
            return box.width > 0 && box.left >= host.left - 1 && box.right <= host.right + 1
          }),
        }
      })
      expect(tabs.whole || tabs.overflow).toBe(true)
      if (tabs.overflow) await expect(page.locator('.scroll-fade:has(.tabs) .scroll-fade-edge.right')).toBeVisible()
      await stamp(page)
      await page.locator('.card', { has: page.locator('.tabs') }).screenshot({
        path: `${OUT}/qa3-scorer-tabs-${width}.png`,
      })
    }
    if (width === 375) {
      await stamp(page)
      await page.locator('.card', { has: page.getByRole('heading', { name: 'Матчи' }) }).screenshot({
        path: `${OUT}/qa3-home-matches-375.png`,
      })
      await page.locator('.card', { has: page.locator('.tabs') }).screenshot({
        path: `${OUT}/qa3-scorer-tabs-375.png`,
      })
    }
    const last = page.locator('.tabs button').last()
    await last.evaluate((button) => (button as HTMLButtonElement).click())
    await expect(last).toHaveClass(/on/)
    const activeInside = await last.evaluate((button) => {
      const bar = button.closest('.tabs')
      if (!(bar instanceof HTMLElement)) return { ok: false }
      const box = button.getBoundingClientRect()
      const host = bar.getBoundingClientRect()
      const label = button.querySelector('.tab-label')
      return {
        ok: box.width > 0 && box.left >= host.left - 1 && box.right <= host.right - 24,
        box: { left: box.left, right: box.right, width: box.width },
        host: { left: host.left, right: host.right, width: host.width },
        scroll: { left: bar.scrollLeft, width: bar.scrollWidth, client: bar.clientWidth },
        text: label?.textContent,
        labelWidth: label instanceof HTMLElement ? { sw: label.scrollWidth, cw: label.clientWidth } : null,
      }
    })
    expect(activeInside.ok, JSON.stringify(activeInside)).toBe(true)
    await assertTextFits(page, '.chips .chip-label, .tabs .tab-label')
    if (width === 375) {
      const peek = await page.locator('.feed-cards').evaluate((bar) => {
        const cards = [...bar.querySelectorAll('.mc')] as HTMLElement[]
        const host = bar.getBoundingClientRect()
        const second = cards[1]?.getBoundingClientRect()
        if (!second) return 0
        return Math.min(second.right, host.right) - Math.max(second.left, host.left)
      })
      expect(peek).toBeGreaterThanOrEqual(12)
      expect(peek).toBeLessThanOrEqual(72)
    }
  }

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/calendar')
  await expect(page.getByRole('button', { name: 'Лига ИТМО' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Сетка' }).first()).toBeVisible()
  await assertTextFits(page, '.filters .chip-label')
  const liga = page.locator('.filters .chip-label').filter({ hasText: 'Лига ИТМО' })
  await expect(liga).toHaveAttribute('title', 'Лига ИТМО')
  const whole = await liga.evaluate((node) => {
    const bar = node.closest('.filters')
    if (!(bar instanceof HTMLElement) || !(node instanceof HTMLElement)) return false
    node.scrollIntoView({ inline: 'nearest', block: 'nearest' })
    const box = node.getBoundingClientRect()
    const host = bar.getBoundingClientRect()
    return node.scrollWidth <= node.clientWidth + 1
      && box.left >= host.left - 1
      && box.right <= host.right + 1
  })
  expect(whole).toBe(true)
  await expect(page.locator('.filters-fade .scroll-fade-edge.right')).toBeVisible()
  await stamp(page)
  await page.locator('.filters-fade').screenshot({ path: `${OUT}/qa3-calendar-chip-375.png` })
})
