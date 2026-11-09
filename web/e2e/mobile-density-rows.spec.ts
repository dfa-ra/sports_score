import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const OUT = '/opt/cursor/artifacts'
const LONG_TEAM = 'QA Толян 111-164129 ПО-16'
const CHIP = 'QA Толян ПО-16'

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

async function shot(page: Page, name: string) {
  mkdirSync(OUT, { recursive: true })
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({ path: `${OUT}/${name}.png` })
}

function zeroRow(teamId: string, teamName: string) {
  return {
    teamId,
    teamName,
    played: 0,
    wins: 0,
    draws: 0,
    losses: 0,
    goalsFor: 0,
    goalsAgainst: 0,
    points: 0,
  }
}

async function install(page: Page) {
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me' || path === '/api/v1/auth/refresh') return json(route, { message: 'no' }, 401)
    if (path === '/api/v1/teams') {
      return json(route, {
        content: [
          { id: 'qa', name: CHIP, shortName: CHIP, logoUrl: '' },
          { id: 'long', name: LONG_TEAM, shortName: LONG_TEAM, logoUrl: '' },
        ],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/tournaments/current') {
      return json(route, { id: 'qa', name: CHIP, competitionStage: 'GROUPS' })
    }
    if (path === '/api/v1/tournaments') {
      return json(route, {
        content: [{ id: 'qa', name: CHIP, status: 'ACTIVE', competitionStage: 'GROUPS', seasonLabel: '2026/2027' }],
        totalPages: 1,
      })
    }
    if (path === '/api/v1/tournaments/qa') {
      return json(route, { id: 'qa', name: CHIP, competitionStage: 'GROUPS', seasonLabel: '2026/2027' })
    }
    if (path === '/api/v1/tournaments/qa/standings') {
      return json(route, {
        tables: [{
          id: 'a',
          name: 'Группа A',
          sortOrder: 1,
          rows: [zeroRow('qa', CHIP), zeroRow('long', LONG_TEAM)],
        }],
      })
    }
    if (path === '/api/v1/tournaments/qa/playoff') return json(route, { rounds: [] })
    if (path === '/api/v1/matches') return json(route, { content: [], totalPages: 1 })
    if (path === '/api/v1/statistics/board') {
      return json(route, {
        scorers: [{
          playerId: 'p-long',
          firstName: '111-164129 ПО-16',
          lastName: 'QA Толян',
          goals: 0,
          assists: 0,
          appearances: 0,
          teamId: 'qa',
        }],
        assists: [{
          playerId: 'p-ilya',
          firstName: 'Илья',
          lastName: 'Орлов',
          goals: 0,
          assists: 0,
          appearances: 0,
          teamId: 'long',
        }],
      })
    }
    if (path === '/api/v1/home/overview') {
      return json(route, {
        next: {
          id: 'next-1',
          tournamentId: 'qa',
          tournamentName: CHIP,
          tournamentShortName: CHIP,
          scheduledAt: '2099-06-01T12:00:00Z',
          status: 'SCHEDULED',
          homeTeamId: 'qa',
          homeTeamName: 'Физтех',
          awayTeamId: 'long',
          awayTeamName: 'Архитекторы',
          homeScore: 0,
          awayScore: 0,
          stageLabel: '1/2 финала',
        },
        live: [],
        upcoming: [],
        recent: [],
        tournaments: [],
        scorersByTournament: [],
        summary: { teams: 2, players: 2, matches: 4, tournaments: 1, goals: 8, goalsPerMatch: 2 },
      })
    }
    return json(route, { content: [] })
  })
}

test('mobile group rows stay one team wide and long names ellipsize', async ({ page }) => {
  test.setTimeout(60_000)
  await install(page)

  for (const width of [375, 320]) {
    await page.setViewportSize({ width, height: 812 })
    await page.goto('/table?tournament=qa')
    await expect(page.getByRole('heading', { level: 2, name: 'Группа A' })).toBeVisible()
    const layout = await page.locator('.sheet').first().evaluate((sheet) => {
      const title = sheet.querySelector('.group-title') as HTMLElement
      const head = sheet.querySelector('.line.head') as HTMLElement
      const rows = [...sheet.querySelectorAll('.line:not(.head)')] as HTMLElement[]
      const titleBox = title.getBoundingClientRect()
      const headBox = head.getBoundingClientRect()
      const sheetBox = sheet.getBoundingClientRect()
      const goal = [...head.querySelectorAll('span')].find((node) => (node.textContent || '').trim() === 'И') as HTMLElement
      return {
        columns: getComputedStyle(sheet).gridTemplateColumns,
        titleTop: titleBox.top,
        headTop: headBox.top,
        goalLeft: goal.getBoundingClientRect().left,
        titleLeft: titleBox.left,
        sheetWidth: sheetBox.width,
        rows: rows.map((row) => {
          const box = row.getBoundingClientRect()
          const name = row.querySelector('.club b') as HTMLElement
          const style = getComputedStyle(name)
          return {
            text: (name.textContent || '').trim(),
            top: box.top,
            bottom: box.bottom,
            left: box.left,
            width: box.width,
            white: style.whiteSpace,
            overflow: style.textOverflow,
            scroll: name.scrollWidth,
            client: name.clientWidth,
          }
        }),
      }
    })
    expect(layout.columns.split(' ').length, `sheet columns at ${width}`).toBe(1)
    expect(Math.abs(layout.titleTop - layout.headTop), `header row at ${width}`).toBeLessThanOrEqual(2)
    expect(layout.goalLeft).toBeGreaterThan(layout.titleLeft + 40)
    expect(layout.rows, `row count at ${width}`).toHaveLength(2)
    expect(layout.rows[0].text).toBe(CHIP)
    expect(layout.rows[1].text).toBe(LONG_TEAM)
    expect(layout.rows[0].bottom).toBeLessThanOrEqual(layout.rows[1].top + 1)
    for (const row of layout.rows) {
      expect(row.width, row.text).toBeGreaterThan(layout.sheetWidth - 4)
      expect(row.left).toBeLessThan(layout.sheetWidth)
      expect(row.white).toBe('nowrap')
      expect(row.overflow).toBe('ellipsis')
      if (row.text === LONG_TEAM) expect(row.scroll, row.text).toBeGreaterThan(row.client + 1)
    }
    const pageScroll = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(pageScroll, `page scroll at ${width}`).toBeLessThanOrEqual(1)
    await shot(page, `table-${width}`)
  }

  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/players')
  await expect(page.getByText(LONG_TEAM).first()).toBeVisible()
  const leaders = await page.locator('.leader').evaluateAll((cards) => cards.map((card) => {
    const name = card.querySelector('.leader-row a') as HTMLElement
    const team = card.querySelector('.club-name') as HTMLElement
    const nameStyle = getComputedStyle(name)
    const nameBox = name.getBoundingClientRect()
    const teamBox = team.getBoundingClientRect()
    return {
      name: (name.textContent || '').trim(),
      team: (team.textContent || '').trim(),
      white: nameStyle.whiteSpace,
      overflow: nameStyle.textOverflow,
      line: parseFloat(nameStyle.lineHeight),
      font: parseFloat(nameStyle.fontSize),
      height: nameBox.height,
      scrollHeight: name.scrollHeight,
      clientHeight: name.clientHeight,
      scrollWidth: name.scrollWidth,
      clientWidth: name.clientWidth,
      gap: teamBox.top - nameBox.bottom,
      nameBottom: nameBox.bottom,
      teamTop: teamBox.top,
    }
  }))
  expect(leaders).toHaveLength(2)
  const longLeader = leaders.find((row) => row.name.includes('111-164129'))
  const shortLeader = leaders.find((row) => row.name.includes('Орлов'))
  expect(longLeader).toBeTruthy()
  expect(shortLeader).toBeTruthy()
  for (const row of leaders) {
    expect(row.white).toBe('nowrap')
    expect(row.overflow).toBe('ellipsis')
    expect(row.height).toBeLessThanOrEqual(row.line + 1)
    expect(row.height).toBeGreaterThanOrEqual(row.font)
    expect(row.scrollHeight).toBeLessThanOrEqual(row.clientHeight + 1)
    expect(row.gap).toBeGreaterThanOrEqual(2)
    expect(row.gap).toBeLessThanOrEqual(4)
    expect(row.nameBottom).toBeLessThanOrEqual(row.teamTop)
  }
  expect(longLeader!.scrollWidth).toBeGreaterThan(longLeader!.clientWidth + 1)
  await shot(page, 'players-375')

  for (const width of [320, 375]) {
    await page.setViewportSize({ width, height: 812 })
    await page.goto('/')
    await expect(page.locator('.hero-match .meta .tb')).toHaveText(CHIP)
    const chip = await page.locator('.hero-match .meta .tb').evaluate((node) => {
      const style = getComputedStyle(node)
      return {
        white: style.whiteSpace,
        overflow: style.textOverflow,
        height: node.getBoundingClientRect().height,
        line: parseFloat(style.lineHeight),
        scroll: node.scrollWidth,
        client: node.clientWidth,
        maxWidth: style.maxWidth,
      }
    })
    expect(chip.white, `chip at ${width}`).toBe('nowrap')
    expect(chip.overflow, `chip at ${width}`).toBe('ellipsis')
    expect(chip.maxWidth, `chip at ${width}`).not.toBe('none')
    expect(chip.height, `chip at ${width}`).toBeLessThanOrEqual(chip.line + 2)
    expect(chip.height, `chip at ${width}`).toBeGreaterThanOrEqual(chip.line - 2)
    if (width === 320) await shot(page, 'home-320')
  }

  await page.setViewportSize({ width: 1920, height: 1080 })
  await page.goto('/table?tournament=qa')
  await expect(page.getByRole('heading', { level: 2, name: 'Группа A' })).toBeVisible()
  const desktopTable = await page.locator('.sheet .line:not(.head)').first().evaluate((row) => {
    const columns = getComputedStyle(row).gridTemplateColumns.split(' ').length
    const rows = [...row.parentElement!.querySelectorAll('.line:not(.head)')] as HTMLElement[]
    const boxes = rows.map((item) => item.getBoundingClientRect())
    return { columns, stacked: boxes[0].bottom <= boxes[1].top + 1, width: boxes[0].width }
  })
  expect(desktopTable.columns).toBeGreaterThanOrEqual(7)
  expect(desktopTable.stacked).toBe(true)
  await shot(page, 'table-1920')

  await page.goto('/players')
  await expect(page.locator('.leader').first().locator('a')).toBeVisible()
  const desktopName = await page.locator('.leader').first().locator('a').evaluate((node) => getComputedStyle(node).fontSize)
  expect(desktopName).toBe('16px')
  await shot(page, 'players-1920')

  await page.goto('/')
  const desktopMeta = await page.locator('.hero-match .meta').evaluate((node) => getComputedStyle(node).display)
  expect(desktopMeta).toBe('grid')
  await shot(page, 'home-1920')
})
