import { expect, test } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const LONG_TEAM = 'Факультет программной инженерии и компьютерной техники'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path.endsWith('/auth/me') || path.endsWith('/auth/refresh')) return json({ message: 'no' }, 401)
    if (path.endsWith('/statistics/board')) {
      return json({
        scorers: [
          { playerId: 'p1', firstName: 'Илья', lastName: 'Орлов', goals: 12, assists: 2, appearances: 8, teamId: 'ft' },
          { playerId: 'p2', firstName: 'Мария', lastName: 'Солнцева', goals: 9, assists: 1, appearances: 8, teamId: 'ur' },
          { playerId: 'p3', firstName: 'Пётр', lastName: 'Квантов', goals: 7, assists: 1, appearances: 8, teamId: 'kv' },
          { playerId: 'p4', firstName: 'Олег', lastName: 'Длинный', goals: 4, assists: 0, appearances: 8, teamId: 'long' },
        ],
        assists: [
          { playerId: 'a1', firstName: 'Анна', lastName: 'Ким', goals: 2, assists: 8, appearances: 8, teamId: 'ur' },
          { playerId: 'a2', firstName: 'Сергей', lastName: 'Нов', goals: 1, assists: 6, appearances: 8, teamId: 'ft' },
          { playerId: 'a3', firstName: 'Ева', lastName: 'Лис', goals: 1, assists: 4, appearances: 8, teamId: 'kv' },
        ],
      })
    }
    if (path.endsWith('/teams')) {
      return json({
        content: [
          { id: 'ft', name: 'Физтех' },
          { id: 'ur', name: 'Юристы' },
          { id: 'kv', name: 'Квант', logoUrl: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><rect width="16" height="16" fill="#123"/></svg>') },
          { id: 'long', name: LONG_TEAM },
        ],
        totalPages: 1,
      })
    }
    if (path.endsWith('/tournaments')) return json({ content: [], totalPages: 1 })
    if (path.endsWith('/home/overview')) {
      return json({
        liveMatches: [],
        upcomingMatches: [],
        recentResults: [],
        topScorers: [],
        scorersByTournament: [
          { tournamentId: 't1', tournamentName: 'Кубок', playerId: 'p1', playerName: 'Илья Орлов', teamName: 'Физтех', goals: 12 },
        ],
      })
    }
    return json({ content: [] })
  })
})

function measure() {
  const textBox = (el: Element) => {
    const node = [...el.childNodes].find((child) => child.nodeType === Node.TEXT_NODE && (child.textContent ?? '').trim())
    const range = document.createRange()
    if (node) range.selectNodeContents(node)
    else range.selectNodeContents(el)
    return range.getBoundingClientRect()
  }
  const alphabetic = (el: Element) => {
    const probe = document.createElement('span')
    probe.style.cssText = 'display:inline-block;width:0;height:0;overflow:hidden;vertical-align:baseline;padding:0;margin:0;border:0'
    el.insertBefore(probe, el.firstChild)
    const y = probe.getBoundingClientRect().bottom
    probe.remove()
    return y
  }
  const mid = (box: DOMRect) => box.top + box.height / 2
  const rows = [...document.querySelectorAll('.cols.line, .leader, .who.name-team')].map((row) => {
    const crest = row.querySelector('.team-crest')
    const team = row.querySelector('.club-name')
    const name = row.querySelector('.player-name') ?? row.querySelector('a')
    const nameTeam = row.querySelector('.name-team') ?? (row.classList.contains('name-team') ? row : null)
    if (!crest || !team || !name || !nameTeam) return null
    const box = crest.getBoundingClientRect()
    const teamText = textBox(team)
    const nameText = textBox(name)
    const fallback = crest.classList.contains('team-crest--fallback')
    let letterX: number | null = null
    let letterY: number | null = null
    let letterH: number | null = null
    let lettersVsClub: number | null = null
    if (fallback) {
      const range = document.createRange()
      range.selectNodeContents(crest)
      const letters = range.getBoundingClientRect()
      if (letters.width > 0 && letters.height > 0) {
        letterX = Math.abs((letters.left + letters.width / 2) - (box.left + box.width / 2))
        letterY = Math.abs(mid(letters) - mid(box))
        letterH = letters.height
        lettersVsClub = Math.abs(mid(letters) - mid(teamText))
      }
    }
    const stacked = getComputedStyle(nameTeam).flexDirection.startsWith('column')
    return {
      kind: row.classList.contains('leader') ? 'leader' : 'row',
      team: (team.textContent ?? '').trim(),
      fallback,
      crest: box.width,
      crestH: box.height,
      letterX,
      letterY,
      letterH,
      lettersVsClub,
      baselineDelta: stacked ? null : Math.abs(nameText.bottom - teamText.bottom),
      alphabeticDelta: stacked ? null : Math.abs(alphabetic(name) - alphabetic(team)),
      ellipsis: getComputedStyle(team).textOverflow,
      nowrap: getComputedStyle(team).whiteSpace,
    }
  }).filter(Boolean)
  const home = [...document.querySelectorAll('.team-crest--fallback')].map((crest) => {
    const host = crest.parentElement
    const name = host?.querySelector('.club-name, .srow-name, .player-name')
    const box = crest.getBoundingClientRect()
    const range = document.createRange()
    range.selectNodeContents(crest)
    const letters = range.getBoundingClientRect()
    return {
      letterDelta: letters.height > 0 ? Math.abs(mid(letters) - mid(box)) : null,
      letterH: letters.height,
      crestH: box.height,
      besideName: Boolean(name),
    }
  })
  return { rows, home }
}

test('crest letters and name baselines at 320, 375 and 1440', async ({ page }) => {
  test.setTimeout(90_000)
  mkdirSync(OUT, { recursive: true })
  const shots: Array<[number, string]> = [
    [375, `${OUT}/players-goals-375.png`],
    [320, `${OUT}/players-320.png`],
    [1440, `${OUT}/players-1440.png`],
  ]
  const report: Record<string, unknown> = {}

  for (const [width, file] of shots) {
    await page.setViewportSize({ width, height: width >= 1000 ? 900 : 812 })
    await page.goto('/players')
    await expect(page.getByRole('heading', { name: 'Бомбардиры' })).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const goals = await page.evaluate(measure)
    report[`goals-${width}`] = goals
    assertRows(goals.rows, width)
    await page.screenshot({ path: file })
    if (width === 375) {
      const clip = await page.evaluate(() => {
        const leaders = document.querySelector('.leaders')!.getBoundingClientRect()
        const rows = [...document.querySelectorAll('.cols.line')].slice(0, 4)
        const last = rows[rows.length - 1].getBoundingClientRect()
        const top = Math.max(0, leaders.top)
        return {
          x: 0,
          y: top,
          width: window.innerWidth,
          height: Math.min(window.innerHeight - top, last.bottom - top + 12),
        }
      })
      await page.screenshot({ path: `${OUT}/crest-letters-375.png`, clip })
    }

    await page.getByRole('button', { name: 'Передачи' }).click()
    await expect(page.getByRole('heading', { name: 'Лидеры по передачам' })).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    const assists = await page.evaluate(measure)
    report[`assists-${width}`] = assists
    assertRows(assists.rows, width)
    if (width === 375) await page.screenshot({ path: `${OUT}/players-assists-375.png` })
  }

  await page.setViewportSize({ width: 375, height: 900 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Бомбардиры по турнирам' })).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
  const home = await page.evaluate(measure)
  report.home = home
  for (const crest of home.home) {
    if (crest.letterDelta != null) expect(crest.letterDelta).toBeLessThanOrEqual(0.5)
    if (crest.letterH > 0) expect(crest.letterH).toBeLessThanOrEqual(crest.crestH)
  }
  const block = page.locator('.block').filter({ has: page.getByRole('heading', { name: 'Бомбардиры по турнирам' }) })
  await block.scrollIntoViewIfNeeded()
  await block.screenshot({ path: `${OUT}/home-scorers-375.png` })

  writeFileSync(`${OUT}/players-align.json`, JSON.stringify(report, null, 2))
})

function assertRows(
  rows: Array<{
    kind: string
    fallback: boolean
    crest: number
    crestH: number
    letterX: number | null
    letterY: number | null
    letterH: number | null
    lettersVsClub: number | null
    baselineDelta: number | null
    alphabeticDelta: number | null
    ellipsis: string
    nowrap: string
    team: string
  } | null>,
  width: number,
) {
  const list = rows.filter((row): row is NonNullable<typeof row> => row != null)
  expect(list.length).toBeGreaterThan(0)
  for (const row of list) {
    expect(Math.abs(row.crest - row.crestH)).toBeLessThanOrEqual(0.5)
    if (row.fallback) {
      expect(row.letterX ?? 99).toBeLessThanOrEqual(0.5)
      expect(row.letterY ?? 99).toBeLessThanOrEqual(0.5)
      expect(row.letterH ?? 99).toBeLessThanOrEqual(row.crestH)
      expect(row.lettersVsClub ?? 99).toBeLessThanOrEqual(0.5)
    }
    if (row.baselineDelta != null) expect(row.baselineDelta).toBeLessThanOrEqual(0.5)
    if (row.alphabeticDelta != null) expect(row.alphabeticDelta).toBeLessThanOrEqual(0.5)
    const crest = width <= 720 ? 11 : 16
    expect(row.crest).toBeCloseTo(crest, 0)
    if (row.team === LONG_TEAM) {
      expect(row.ellipsis).toBe('ellipsis')
      expect(row.nowrap).toBe('nowrap')
    }
  }
}
