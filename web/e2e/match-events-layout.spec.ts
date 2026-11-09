import { mkdirSync } from 'node:fs'

import { expect, test, type Page, type Route } from '@playwright/test'

const matchId = '11111111-2222-4333-8444-555555555555'
const homeTeamId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const awayTeamId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
const tournamentId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'

const homeScorer = 'Константинопольский-Константинопольский Иван-Сергей'
const homeAssist = 'Передающий-Оченьдлинноеимя Пётр'
const awayScorer = 'Фамилиякоторуюнельзясократить-Александрович Сидор'
const awayAssist = 'Безномера Алексей'
const cardPlayer = 'Жёлтокарточный-Длиннаяфамилия Олег'
const subOut = 'Уходящий-Составной Игрок'
const subIn = 'Выходящий-Запасной Игрок'

test.use({ deviceScaleFactor: 1, viewport: { width: 1280, height: 900 } })

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function event(row: Record<string, unknown>) {
  return {
    matchId,
    timestamp: '2026-11-03T12:00:00Z',
    period: 1,
    metadata: {},
    voided: false,
    ...row,
  }
}

const events = [
  event({
    id: 'e1',
    eventType: 'GOAL',
    timestamp: '2026-11-03T12:12:00Z',
    gameTime: 12 * 60,
    period: 1,
    teamId: homeTeamId,
    playerId: 'p-home-scorer',
    playerFirstName: 'Иван-Сергей',
    playerLastName: 'Константинопольский-Константинопольский',
    playerJersey: 10,
    secondaryPlayerId: 'p-home-assist',
    secondaryPlayerFirstName: 'Пётр',
    secondaryPlayerLastName: 'Передающий-Оченьдлинноеимя',
    secondaryPlayerJersey: 8,
  }),
  event({
    id: 'e2',
    eventType: 'GOAL',
    timestamp: '2026-11-03T12:23:00Z',
    gameTime: 23 * 60,
    period: 2,
    teamId: awayTeamId,
    playerId: 'p-away-scorer',
    playerFirstName: 'Сидор',
    playerLastName: 'Фамилиякоторуюнельзясократить-Александрович',
    playerJersey: 7,
    secondaryPlayerId: 'p-away-assist',
    secondaryPlayerFirstName: 'Алексей',
    secondaryPlayerLastName: 'Безномера',
    secondaryPlayerJersey: null,
  }),
  event({
    id: 'e3',
    eventType: 'YELLOW_CARD',
    timestamp: '2026-11-03T12:30:00Z',
    gameTime: 30 * 60,
    period: 2,
    teamId: homeTeamId,
    playerId: 'p-card',
    playerFirstName: 'Олег',
    playerLastName: 'Жёлтокарточный-Длиннаяфамилия',
    playerJersey: 4,
  }),
  event({
    id: 'e4',
    eventType: 'SUBSTITUTION',
    timestamp: '2026-11-03T12:35:00Z',
    gameTime: 35 * 60,
    period: 2,
    teamId: awayTeamId,
    playerId: 'p-sub-out',
    playerFirstName: 'Игрок',
    playerLastName: 'Уходящий-Составной',
    playerJersey: null,
    secondaryPlayerId: 'p-sub-in',
    secondaryPlayerFirstName: 'Игрок',
    secondaryPlayerLastName: 'Выходящий-Запасной',
    secondaryPlayerJersey: 19,
  }),
]

async function install(page: Page) {
  await page.route('**/api/v1/**', (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path.endsWith('/teams') && route.request().method() === 'GET') {
      return json(route, {
        content: [
          { id: homeTeamId, name: 'Север', shortName: 'Север' },
          { id: awayTeamId, name: 'Юг', shortName: 'Юг' },
        ],
      })
    }
    if (path.endsWith(`/matches/${matchId}`) && route.request().method() === 'GET') {
      return json(route, {
        id: matchId,
        tournamentId,
        status: 'FINISHED',
        sportCode: 'FOOTBALL',
        homeTeamId,
        awayTeamId,
        homeTeamName: 'Север',
        awayTeamName: 'Юг',
        homeScore: 1,
        awayScore: 1,
        period: 2,
        periodCount: 2,
        periodLengthSeconds: 900,
        scheduledAt: '2026-11-03T12:00:00Z',
      })
    }
    if (path.endsWith(`/matches/${matchId}/events`)) return json(route, events)
    if (path.endsWith(`/matches/${matchId}/lineups`)) {
      return json(route, { home: { confirmed: false, starters: [], bench: [] }, away: { confirmed: false, starters: [], bench: [] } })
    }
    if (path.endsWith(`/matches/${matchId}/referees`)) return json(route, [])
    if (path.endsWith(`/matches/${matchId}/shootout`)) return json(route, { kicks: [], decisive: false })
    if (path.endsWith(`/teams/${homeTeamId}/form`) || path.endsWith(`/teams/${awayTeamId}/form`)) return json(route, [])
    if (path.endsWith(`/tournaments/${tournamentId}`)) return json(route, { id: tournamentId, name: 'Кубок' })
    return json(route, {})
  })
  await page.routeWebSocket(/\/ws/, (ws) => {
    ws.onMessage(() => {})
  })
}

test('overview mirror feed keeps minutes, icons, and one column', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await install(page)
  await page.goto(`/matches/${matchId}`)
  const card = page.locator('.card.events')
  await expect(card.getByText('События')).toBeVisible()
  await expect(card.getByText('1-Й ТАЙМ')).toBeVisible()
  await expect(card.getByText('2-Й ТАЙМ')).toBeVisible()
  await expect(card.getByText('ИТОГ')).toBeVisible()
  await expect(card.locator('.half').nth(0)).toContainText('1 : 0')
  await expect(card.locator('.half').nth(1)).toContainText('0 : 1')
  await expect(card.locator('.total b')).toHaveText('1 : 1')
  await expect(card.getByText('Начало тайма')).toHaveCount(0)
  await expect(card.getByText('Конец тайма')).toHaveCount(0)
  await expect(card.getByText(homeScorer)).toBeVisible()
  await expect(card.getByText(`передача · #8 ${homeAssist}`)).toBeVisible()
  await expect(card.getByText(`передача · ${awayAssist}`)).toBeVisible()

  const shots: Array<[number, string]> = [
    [1440, '/opt/cursor/artifacts/match-feed-mirror-1440.png'],
    [375, '/opt/cursor/artifacts/match-feed-mirror-375.png'],
    [320, '/opt/cursor/artifacts/match-feed-mirror-320.png'],
  ]
  for (const [width, shot] of shots) {
    await page.setViewportSize({ width, height: width >= 720 ? 1000 : 900 })
    await card.scrollIntoViewIfNeeded()
    const facts = await card.evaluate((node) => {
      const rows = [...node.querySelectorAll('.ev')].map((row) => {
        const who = row.querySelector('.who') as HTMLElement
        const scorer = row.querySelector('b') as HTMLElement
        const assist = row.querySelector('.assist') as HTMLElement | null
        const minute = row.querySelector('.min') as HTMLElement
        const score = row.querySelector('.run') as HTMLElement | null
        const box = (el: HTMLElement) => el.getBoundingClientRect()
        const scorerBox = box(scorer)
        const minuteBox = box(minute)
        const rowBox = box(row as HTMLElement)
        const icon = row.querySelector('.mark') as HTMLElement
        const iconBox = box(icon)
        const scoreBox = score ? box(score) : null
        const minuteStyle = getComputedStyle(minute)
        const iconStyle = getComputedStyle(icon)
        return {
          home: row.classList.contains('home'),
          goal: row.classList.contains('goal'),
          textAlign: getComputedStyle(who).textAlign,
          minute: minute.textContent?.trim() || '',
          scorer: scorer.textContent?.trim() || '',
          assist: assist?.textContent?.trim() || '',
          markClass: icon.className,
          markW: iconBox.width,
          markH: iconBox.height,
          minuteShrink: minuteStyle.flexShrink,
          iconShrink: iconStyle.flexShrink,
          rowHeight: rowBox.height,
          scorerTop: scorerBox.top,
          scorerBottom: scorerBox.bottom,
          scorerLeft: scorerBox.left,
          scorerRight: scorerBox.right,
          scorerOverflow: scorer.scrollWidth > scorer.clientWidth + 1,
          assistTop: assist ? box(assist).top : null,
          assistLeft: assist ? box(assist).left : null,
          assistRight: assist ? box(assist).right : null,
          minuteLeft: minuteBox.left,
          minuteRight: minuteBox.right,
          markLeft: iconBox.left,
          markRight: iconBox.right,
          scoreLeft: scoreBox?.left ?? null,
          scoreRight: scoreBox?.right ?? null,
          score: score?.textContent?.trim() || '',
          rowRight: rowBox.right,
          rowWidth: rowBox.width,
          parens: (row.textContent || '').includes('('),
        }
      })
      return {
        pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        cardOverflow: node.scrollWidth - node.clientWidth,
        rows,
      }
    })
    expect(facts.pageOverflow, `page scroll at ${width}`).toBeLessThanOrEqual(1)
    expect(facts.cardOverflow, `events scroll at ${width}`).toBeLessThanOrEqual(1)
    expect(facts.rows).toHaveLength(4)
    for (const row of facts.rows) {
      expect(row.rowHeight, `${row.scorer} height at ${width}`).toBeGreaterThanOrEqual(44)
      expect(row.rowWidth).toBeLessThanOrEqual(width)
      expect(row.markW).toBeGreaterThanOrEqual(13.5)
      expect(row.markW).toBeLessThanOrEqual(14.5)
      expect(row.markH).toBeGreaterThanOrEqual(13.5)
      expect(row.markH).toBeLessThanOrEqual(14.5)
      expect(row.parens).toBe(false)
      if (row.home) {
        expect(row.textAlign).toBe('left')
        expect(row.minuteRight).toBeLessThanOrEqual(row.markLeft + 1)
        if (row.goal) {
          expect(row.scoreLeft).not.toBeNull()
          expect(row.markRight).toBeLessThanOrEqual((row.scoreLeft ?? 0) + 1)
          expect((row.scoreRight ?? 0)).toBeLessThanOrEqual(row.scorerLeft + 1)
        } else {
          expect(row.markRight).toBeLessThanOrEqual(row.scorerLeft + 1)
        }
      } else {
        expect(row.textAlign).toBe('right')
        expect(row.markRight).toBeLessThanOrEqual(row.minuteLeft + 1)
        expect(row.minuteRight).toBeGreaterThanOrEqual(row.rowRight - 2)
        if (row.goal) {
          expect((row.scoreRight ?? 0)).toBeLessThanOrEqual(row.markLeft + 1)
          expect(row.scorerRight).toBeLessThanOrEqual((row.scoreLeft ?? 0) + 1)
        } else {
          expect(row.scorerRight).toBeLessThanOrEqual(row.markLeft + 1)
        }
      }
      if (row.assist) {
        if (width >= 720) {
          expect(row.assistTop!).toBeLessThanOrEqual(row.scorerBottom + 1)
          expect(row.assistTop!).toBeGreaterThanOrEqual(row.scorerTop - 2)
          if (row.home) expect(row.assistLeft!).toBeGreaterThanOrEqual(row.scorerRight - 2)
          else expect(row.assistRight!).toBeLessThanOrEqual(row.scorerLeft + 2)
        } else {
          expect(row.assistTop!).toBeGreaterThanOrEqual(row.scorerBottom - 2)
        }
      }
    }
    const goal = facts.rows[0]
    const awayGoal = facts.rows[1]
    const cardRow = facts.rows[2]
    const sub = facts.rows[3]
    expect(goal.minute).toBe("12'")
    expect(goal.score).toBe('1 : 0')
    expect(goal.assist).toBe(`передача · #8 ${homeAssist}`)
    expect(awayGoal.minute).toBe("23'")
    expect(awayGoal.score).toBe('1 : 1')
    expect(awayGoal.assist).toBe(`передача · ${awayAssist}`)
    expect(cardRow.minute).toBe("30'")
    expect(cardRow.score).toBe('')
    expect(cardRow.assist).toBe('Жёлтая')
    expect(cardRow.markClass).toContain('yellow_card')
    expect(cardRow.scorer).toContain(cardPlayer)
    expect(goal.markClass).toContain('goal')
    expect(sub.markClass).toContain('substitution')
    expect(sub.scorer).toContain(subIn)
    expect(sub.assist).toBe(`Замена · ${subOut}`)
    if (width < 720) {
      const clip = await card.locator('.ev').first().locator('b').evaluate((el) => {
        const style = getComputedStyle(el)
        return { whiteSpace: style.whiteSpace, overflow: style.overflowX, textOverflow: style.textOverflow }
      })
      expect(clip).toEqual({ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' })
    }
    await card.screenshot({ path: shot })
  }
  await page.getByRole('button', { name: 'Протокол' }).click()
  const protocolCard = page.locator('.timeline li', { hasText: cardPlayer })
  await expect(protocolCard.locator('strong')).toHaveText('Жёлтая')
  await expect(protocolCard).toContainText(cardPlayer)
})
