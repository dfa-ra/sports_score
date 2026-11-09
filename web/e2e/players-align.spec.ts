import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const LONG_TEAM = 'Факультет программной инженерии и компьютерной техники'
const LOGO = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><circle cx="16" cy="16" r="16" fill="#4cb4e5"/><text x="16" y="21" text-anchor="middle" font-size="12" font-family="sans-serif" fill="#00205b">Q1</text></svg>')}`

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function person(id: string, first: string, last: string, goals: number, assists: number, teamId: string, logo = '') {
  return {
    playerId: id,
    id,
    firstName: first,
    lastName: last,
    goals,
    assists,
    appearances: 8,
    teamId,
    teamLogoUrl: logo,
    avatarUrl: null,
  }
}

const scorers = [
  person('p1', 'Илья', 'Орлов', 12, 2, 'ft'),
  person('p2', 'Артём', 'Белов', 9, 4, 'ur'),
  person('p3', 'Мария', 'Соколова', 7, 1, 'q1', LOGO),
  person('p4', 'Александр', 'Орлов-Воскресенский', 4, 1, 'long'),
]
const assists = [
  person('a1', 'Анна', 'Ким', 2, 8, 'q1', LOGO),
  person('a2', 'Олег', 'Мороз', 1, 6, 'ur'),
  person('a3', 'Илья', 'Орлов', 12, 2, 'ft'),
  person('a4', 'Мария', 'Белова-Преображенская', 1, 3, 'long'),
]

async function install(page: Page) {
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/me') || path.endsWith('/auth/refresh')) return json(route, { message: 'no' }, 401)
    if (path.endsWith('/statistics/board')) return json(route, { scorers, assists })
    if (path.endsWith('/teams')) {
      return json(route, {
        content: [
          { id: 'ft', name: 'Физтех', logoUrl: '' },
          { id: 'ur', name: 'Юристы', logoUrl: '' },
          { id: 'q1', name: 'Квант', shortName: 'Q1', logoUrl: LOGO },
          { id: 'long', name: LONG_TEAM, logoUrl: '' },
        ],
        totalPages: 1,
      })
    }
    if (path.endsWith('/tournaments')) {
      return json(route, { content: [{ id: 'liga', name: 'Лига ИТМО', status: 'ACTIVE' }], totalElements: 1, totalPages: 1 })
    }
    if (path.endsWith('/home/overview')) {
      return json(route, {
        next: null,
        live: [],
        upcoming: [],
        recent: [],
        tournaments: [],
        scorersByTournament: [{
          tournamentId: 'liga',
          tournamentName: 'Лига ИТМО',
          shortName: 'Лига',
          scorers: scorers.map((row) => ({
            ...row,
            displayName: `${row.lastName} ${row.firstName}`,
            teamName: row.teamId === 'long' ? LONG_TEAM : row.teamId === 'ft' ? 'Физтех' : row.teamId === 'ur' ? 'Юристы' : 'Квант',
          })),
        }],
        summary: { teams: 4, players: 4, matchesPlayed: 4, goals: 32, goalsPerMatch: 8 },
      })
    }
    return json(route, { content: [] })
  })
}

type Pair = {
  kind: string
  name: string
  team: string
  box: number
  ink: number | null
  nameDelta: number | null
  groupDelta: number | null
  crest: { w: number; h: number }
  ellipsis: boolean
}

async function pairs(page: Page): Promise<Pair[]> {
  return page.evaluate(() => {
    const mid = (box: DOMRect) => box.top + box.height / 2
    const read = (root: Element, kind: string): Pair | null => {
      const crest = root.querySelector('.team-crest') as HTMLElement | null
      const team = root.querySelector('.club-name') as HTMLElement | null
      if (!crest || !team) return null
      const crestBox = crest.getBoundingClientRect()
      const teamBox = team.getBoundingClientRect()
      const name = root.querySelector('.player-name, .leader-row a') as HTMLElement | null
      const row = root.closest('.cols.line') as HTMLElement | null
      const group = root.querySelector('.name-team') as HTMLElement | null
      let ink: number | null = null
      if ((crest.textContent || '').trim()) {
        const crestRange = document.createRange()
        crestRange.selectNodeContents(crest)
        const teamRange = document.createRange()
        teamRange.selectNodeContents(team)
        const crestInk = crestRange.getBoundingClientRect()
        const teamInk = teamRange.getBoundingClientRect()
        if (crestInk.height > 0 && teamInk.height > 0) ink = Math.abs(mid(crestInk) - mid(teamInk))
      }
      const teamStyle = getComputedStyle(team)
      return {
        kind,
        name: (name?.textContent || '').trim(),
        team: (team.textContent || '').trim(),
        box: Math.abs(mid(crestBox) - mid(teamBox)),
        ink,
        nameDelta: name && kind === 'list' ? Math.abs(mid(name.getBoundingClientRect()) - mid(crestBox)) : null,
        groupDelta: row && group && kind === 'list'
          ? Math.abs(mid(group.getBoundingClientRect()) - mid(row.getBoundingClientRect()))
          : null,
        crest: { w: crestBox.width, h: crestBox.height },
        ellipsis: teamStyle.whiteSpace === 'nowrap' && teamStyle.textOverflow === 'ellipsis',
      }
    }
    type Pair = {
      kind: string
      name: string
      team: string
      box: number
      ink: number | null
      nameDelta: number | null
      groupDelta: number | null
      crest: { w: number; h: number }
      ellipsis: boolean
    }
    const rows = [...document.querySelectorAll('.cols.line')].map((row) => read(row, 'list'))
    const leaders = [...document.querySelectorAll('.leader')].map((card) => read(card, 'leader'))
    return [...rows, ...leaders].filter((item): item is Pair => item != null)
  })
}

function expectAligned(found: Pair[], width: number, tab: string) {
  expect(found.length, `${tab} pairs at ${width}`).toBeGreaterThanOrEqual(4)
  for (const pair of found) {
    expect(pair.box, `${tab} ${pair.kind} ${pair.team} box at ${width}`).toBeLessThanOrEqual(1)
    if (pair.ink != null) expect(pair.ink, `${tab} ${pair.kind} ${pair.team} ink at ${width}`).toBeLessThanOrEqual(1)
    if (pair.nameDelta != null) expect(pair.nameDelta, `${tab} name ${pair.name} at ${width}`).toBeLessThanOrEqual(1)
    if (pair.groupDelta != null) expect(pair.groupDelta, `${tab} group ${pair.name} at ${width}`).toBeLessThanOrEqual(1)
    const size = width <= 720 ? 11 : 16
    expect(pair.crest.w, `${tab} crest width at ${width}`).toBe(size)
    expect(pair.crest.h, `${tab} crest height at ${width}`).toBe(size)
  }
  if (width <= 720) {
    const long = found.find((pair) => pair.kind === 'list' && pair.team === LONG_TEAM)
    expect(long?.ellipsis, `ellipsis at ${width}`).toBe(true)
  }
}

test('team icon and team name share a vertical center', async ({ page }) => {
  test.setTimeout(90_000)
  mkdirSync(OUT, { recursive: true })
  await install(page)
  const report: Record<string, unknown> = {}

  for (const width of [320, 375, 1440]) {
    await page.setViewportSize({ width, height: width === 1440 ? 900 : 812 })
    await page.goto('/players')
    await expect(page.getByRole('heading', { name: 'Бомбардиры' })).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const goals = await pairs(page)
    expectAligned(goals, width, 'goals')
    report[`goals-${width}`] = goals
    if (width === 375) await page.screenshot({ path: `${OUT}/players-goals-375.png` })
    if (width === 320) await page.screenshot({ path: `${OUT}/players-320.png` })
    if (width === 1440) await page.screenshot({ path: `${OUT}/players-1440.png` })

    await page.getByRole('button', { name: 'Передачи' }).click()
    await expect(page.getByRole('heading', { name: 'Лидеры по передачам' })).toBeVisible()
    const assistRows = await pairs(page)
    expectAligned(assistRows, width, 'assists')
    report[`assists-${width}`] = assistRows
    if (width === 375) await page.screenshot({ path: `${OUT}/players-assists-375.png` })
  }

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Бомбардиры по турнирам' })).toBeVisible()
  const block = page.locator('.block').filter({ has: page.getByRole('heading', { name: 'Бомбардиры по турнирам' }) })
  await block.scrollIntoViewIfNeeded()
  const home = await block.evaluate((root) => {
    const mid = (box: DOMRect) => box.top + box.height / 2
    return [...root.querySelectorAll('.srow, tr')].map((row) => {
      const crest = row.querySelector('.team-crest, .crest') as HTMLElement | null
      const team = row.querySelector('.tm, .club-name') as HTMLElement | null
      if (!crest || !team) return null
      const crestBox = crest.getBoundingClientRect()
      const teamBox = team.getBoundingClientRect()
      return {
        team: (team.textContent || '').trim(),
        box: Math.abs(mid(crestBox) - mid(teamBox)),
      }
    }).filter((item) => item != null)
  })
  for (const pair of home) expect(pair.box, `home ${pair.team}`).toBeLessThanOrEqual(1)
  report.home = home as Pair[]
  await block.screenshot({ path: `${OUT}/home-scorers-375.png` })
  writeFileSync(`${OUT}/players-align.json`, JSON.stringify(report, null, 2))
})
