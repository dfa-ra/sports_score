import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const tournamentId = '00000000-0000-4000-8000-000000000105'
const longName = 'QA Толян Альфа Северный Кампус Лиги ИТМО'
const nav = ['Главная', 'Таблица', 'Игры', 'Live', 'Турниры', 'Команды', 'Игроки']

const roleItem: Record<string, string | null> = {
  guest: null,
  player: 'Моя команда',
  referee: 'Пульт судьи',
  admin: 'Админка',
  analyst: 'Аналитик',
}

const forbidden: Record<string, string[]> = {
  guest: ['Профиль', 'Моя команда', 'Пульт судьи', 'Админка', 'Аналитик', 'Выйти'],
  player: ['Пульт судьи', 'Админка', 'Аналитик'],
  referee: ['Моя команда', 'Админка', 'Аналитик'],
  admin: ['Моя команда', 'Пульт судьи', 'Аналитик'],
  analyst: ['Моя команда', 'Пульт судьи', 'Админка'],
}

function liveMatch(scheduledAt: string | null) {
  return {
    id: 'live-tolyan',
    tournamentId: 'tolyan',
    tournamentName: 'QA Толян Live',
    tournamentShortName: 'QA Толян Live',
    scheduledAt,
    status: 'LIVE',
    homeTeamId: 'l1',
    homeTeamName: 'L-1',
    awayTeamId: 'l2',
    awayTeamName: 'L-2',
    homeScore: 3,
    awayScore: 1,
    minute: 12,
    stageLabel: '1/8 финала',
  }
}

function overview(scheduledAt: string | null) {
  const live = liveMatch(scheduledAt)
  return {
    generatedAt: '2026-11-03T12:00:00Z',
    next: null,
    live: [live],
    upcoming: [],
    recent: [{
      id: 'recent-1',
      tournamentId: 'tolyan',
      tournamentName: 'QA Толян Live',
      tournamentShortName: 'QA Толян Live',
      scheduledAt: '2026-10-01T12:00:00Z',
      status: 'FINISHED',
      homeTeamId: 'a1',
      homeTeamName: 'A-1',
      awayTeamId: 'a2',
      awayTeamName: 'A-2',
      homeScore: 2,
      awayScore: 0,
    }],
    tournaments: [{
      id: 'tolyan',
      name: 'QA Толян Live',
      shortName: 'QA Толян Live',
      status: 'ACTIVE',
      seasonYear: 2026,
      seasonLabel: 'Сезон 2026',
      format: 'CUP',
      formatTitle: 'Кубок',
      teamsCount: 16,
      competitionStage: 'PLAYOFF',
      stageLabel: 'Плей-офф · 1/8 финала',
      progress: 0.1,
      leader: null,
      lastMatch: null,
      nextMatch: null,
    }],
    scorersByTournament: [],
    summary: { matchesPlayed: 1, goals: 4, goalsPerMatch: 4, teams: 16, players: 80 },
  }
}

function bracket() {
  const titles = ['1/8 финала', '1/4 финала', '1/2 финала', 'Финал']
  return titles.map((title, index) => {
    const count = 2 ** (titles.length - index - 1)
    return {
      round: index + 1,
      title,
      slots: Array.from({ length: count }, (_, slotIndex) => {
        const filled = index === 0
        return {
          id: `slot-${index}-${slotIndex}`,
          round: index + 1,
          slotIndex,
          roundTitle: title,
          homeTeamId: filled ? `home-${slotIndex}` : null,
          homeTeamName: filled ? (slotIndex === 0 ? longName : `Хозяева ${slotIndex + 1}`) : null,
          awayTeamId: filled ? `away-${slotIndex}` : null,
          awayTeamName: filled ? `Гости ${slotIndex + 1}` : null,
          matchStatus: filled ? 'SCHEDULED' : null,
          scheduledAt: filled ? '2026-11-05T16:00:00Z' : null,
          venue: filled ? 'Зал' : null,
        }
      }),
    }
  })
}

async function mockApi(page: Page, user: Record<string, unknown> | null, scheduledAt: string | null = null) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/api/v1/auth/me') return json(user ?? {}, user ? 200 : 401)
    if (path === '/api/v1/home/overview') return json(overview(scheduledAt))
    if (path.endsWith(`/tournaments/${tournamentId}/overview`)) {
      return json({
        name: 'QA Толян Сетка',
        format: 'GROUPS_PLAYOFF',
        stage: 'PLAYOFF',
        hasPlayoff: true,
        teamsCount: 16,
        matchesCount: 8,
        seasonYear: 2026,
        playoffLive: false,
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/playoff`)) {
      return json({ competitionStage: 'PLAYOFF', rounds: bracket() })
    }
    if (path.endsWith(`/tournaments/${tournamentId}`)) {
      return json({
        id: tournamentId,
        name: 'QA Толян Сетка',
        status: 'ACTIVE',
        format: 'GROUPS_PLAYOFF',
        competitionStage: 'PLAYOFF',
        seasonYear: 2026,
        regulations: '',
      })
    }
    if (path.endsWith(`/tournaments/${tournamentId}/teams`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/standings`)) return json([])
    if (path.endsWith(`/tournaments/${tournamentId}/matches`)) return json({ content: [] })
    if (path.endsWith(`/tournaments/${tournamentId}/tables`)) return json([])
    if (path === '/api/v1/teams') return json({ content: [] })
    return json({})
  })
}

async function signIn(page: Page, kind: string) {
  if (kind === 'guest') return
  const user = {
    id: `user-${kind}`,
    email: `${kind}@liga.test`,
    role: kind.toUpperCase(),
    enabled: true,
    firstName: 'Иван',
    lastName: 'Толян',
    roles: [{ role: kind.toUpperCase(), status: 'APPROVED' }],
  }
  await page.addInitScript((stored) => {
    const exp = Math.floor(Date.now() / 1000) + 3600
    const payload = btoa(JSON.stringify({ exp })).replace(/=+$/g, '').replace(/\+/g, '-').replace(/\//g, '_')
    const token = `e30.${payload}.sig`
    const raw = JSON.stringify(stored)
    localStorage.setItem('sl_access', token)
    localStorage.setItem('sl_user', raw)
    sessionStorage.setItem('sl_access', token)
    sessionStorage.setItem('sl_user', raw)
  }, user)
  return user
}

function panelItems(page: Page) {
  return page.locator('.menu-panel').evaluate((el) =>
    [...el.children].map((child) => ({
      sep: child.getAttribute('role') === 'separator',
      text: (child.textContent || '').replace(/\s+/g, ' ').trim(),
    })),
  )
}

async function barText(page: Page) {
  return page.locator('.nav-inner').evaluate((el) => {
    const clone = el.cloneNode(true) as HTMLElement
    clone.querySelectorAll('.menu-panel').forEach((node) => node.remove())
    return (clone.innerText || '').replace(/\s+/g, ' ')
  })
}

test('the running card shows the hero live match even when its kickoff is missing', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await mockApi(page, null, null)
  for (const width of [375, 768, 1280, 1920]) {
    await page.setViewportSize({ width, height: 1400 })
    await page.goto('/')
    const card = page.locator('.tcard', { has: page.getByRole('heading', { name: 'QA Толян Live' }) })
    await expect(card).toBeVisible()
    await expect(page.getByText('Матчей пока нет')).toHaveCount(0)
    await expect(card.getByText('Матчей пока нет')).toHaveCount(0)
    await expect(card.locator('.irow')).toContainText('L-1')
    await expect(card.locator('.irow')).toContainText('L-2')
    await expect(card.locator('.sc')).toContainText('3')
    await expect(card.locator('.sc')).toContainText('1')
    const hero = page.locator('.livebar')
    await expect(hero).toContainText('L-1')
    await expect(hero).toContainText('3:1')
    await expect(hero).toContainText('L-2')
    await page.screenshot({ path: `/opt/cursor/artifacts/qa105-home-${width}.png`, fullPage: true })
  }
})

test('a past kickoff does not hide the live score on the running card', async ({ page }) => {
  await mockApi(page, null, '2020-01-01T00:00:00Z')
  await page.setViewportSize({ width: 1280, height: 1200 })
  await page.goto('/')
  const card = page.locator('.tcard', { has: page.getByRole('heading', { name: 'QA Толян Live' }) })
  await expect(card.getByText('Матчей пока нет')).toHaveCount(0)
  await expect(card.locator('.sc')).toContainText('3')
  await expect(card.locator('.sc')).toContainText('1')
})

test('16-team full grid wraps 1/8 names and keeps placeholder circles inside the row', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  await mockApi(page, null)
  await page.setViewportSize({ width: 1180, height: 900 })
  await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
  await expect(page.getByRole('heading', { name: 'Сетка плей-офф' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '1/8 финала' }).first()).toBeVisible()
  await expect(page.getByRole('tablist', { name: 'Раунды' })).toBeVisible()
  await expect(page.locator('.po-grid')).toHaveCount(0)
  const tabName = page.locator('.po-panel .pn', { hasText: longName })
  await expect(tabName).toBeVisible()
  const tabStyle = await tabName.evaluate((el) => getComputedStyle(el).whiteSpace)
  expect(tabStyle).toBe('nowrap')

  for (const width of [1280, 1920]) {
    await page.setViewportSize({ width, height: 1400 })
    await page.goto(`/tournaments/${tournamentId}?tab=playoff`)
    await expect(page.getByRole('tablist', { name: 'Раунды' })).toHaveCount(0)
    const headers = page.locator('.po-grid h3.rh')
    await expect(headers).toHaveCount(5)
    const name = page.locator('.po-grid .pn', { hasText: longName })
    await expect(name).toHaveAttribute('title', longName)
    const layout = await name.evaluate((el) => {
      const style = getComputedStyle(el)
      const box = el.getBoundingClientRect()
      const text = el.firstChild
      const range = document.createRange()
      if (text) range.selectNodeContents(text)
      const lines = text
        ? [...range.getClientRects()].filter((rect) => {
          if (rect.width <= 0 || rect.height <= 0) return false
          const mid = (rect.top + rect.bottom) / 2
          return mid >= box.top - 0.5 && mid <= box.bottom + 0.5
        })
        : []
      const tops: number[] = []
      for (const rect of lines) {
        if (!tops.some((top) => Math.abs(top - rect.top) < 2)) tops.push(rect.top)
      }
      const probe = el.cloneNode(true) as HTMLElement
      probe.style.position = 'fixed'
      probe.style.visibility = 'hidden'
      probe.style.width = `${box.width}px`
      probe.style.maxHeight = 'none'
      probe.style.webkitLineClamp = 'unset'
      probe.style.display = 'block'
      probe.style.whiteSpace = 'normal'
      document.body.appendChild(probe)
      const full = probe.getBoundingClientRect().height
      probe.remove()
      return {
        fontSize: style.fontSize,
        lineClamp: style.webkitLineClamp,
        display: style.display,
        boxOrient: style.webkitBoxOrient,
        whiteSpace: style.whiteSpace,
        wordBreak: style.wordBreak,
        overflowWrap: style.overflowWrap,
        lineHeight: style.lineHeight,
        maxHeight: style.maxHeight,
        lines: tops.length,
        shown: box.height,
        full,
      }
    })
    expect(layout.fontSize).toBe('13px')
    expect(layout.lineClamp).toBe('2')
    expect(layout.whiteSpace).not.toBe('nowrap')
    expect(layout.wordBreak).toBe('normal')
    expect(layout.overflowWrap).toBe('normal')
    expect(layout.lines, JSON.stringify(layout)).toBe(2)
    expect(layout.full).toBeGreaterThan(layout.shown + 1)

    const circles = page.locator('.po-grid .ph')
    await expect(circles.first()).toBeVisible()
    const placed = await circles.evaluateAll((nodes) => nodes.map((node) => {
      const mark = node.getBoundingClientRect()
      const row = node.closest('.pr')!.getBoundingClientRect()
      const style = getComputedStyle(node)
      return {
        width: mark.width,
        height: mark.height,
        top: mark.top,
        bottom: mark.bottom,
        left: mark.left,
        right: mark.right,
        rowTop: row.top,
        rowBottom: row.bottom,
        rowLeft: row.left,
        rowRight: row.right,
        borderStyle: style.borderTopStyle,
        label: node.parentElement?.querySelector('.pn')?.textContent || '',
      }
    }))
    expect(placed.length).toBeGreaterThan(0)
    expect(placed.some((item) => item.label.includes('Победитель 1/8'))).toBe(true)
    for (const item of placed) {
      expect(item.borderStyle).toBe('dashed')
      expect(item.width).toBeGreaterThan(22)
      expect(item.width).toBeLessThan(26)
      expect(item.height).toBeGreaterThan(22)
      expect(item.height).toBeLessThan(26)
      expect(item.top).toBeGreaterThanOrEqual(item.rowTop - 0.5)
      expect(item.bottom).toBeLessThanOrEqual(item.rowBottom + 0.5)
      expect(item.left).toBeGreaterThanOrEqual(item.rowLeft - 0.5)
      expect(item.right).toBeLessThanOrEqual(item.rowRight + 0.5)
    }
    const crests = await page.locator('.po-grid .team-crest').first().evaluate((el) => {
      const box = el.getBoundingClientRect()
      return { width: box.width, height: box.height }
    })
    expect(crests.width).toBeGreaterThan(22)
    expect(crests.width).toBeLessThan(26)

    await page.locator('.po').screenshot({ path: `/opt/cursor/artifacts/qa105-bracket-${width}.png` })
  }
})

test('header menus follow the width and the signed-in role', async ({ page }) => {
  mkdirSync('/opt/cursor/artifacts', { recursive: true })
  for (const kind of ['guest', 'player', 'referee', 'admin', 'analyst'] as const) {
    const user = await signIn(page, kind)
    await mockApi(page, user ?? null)
    for (const width of [1920, 375]) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto('/')
      await expect(page.getByRole('heading', { name: 'QA Толян Live' })).toBeVisible()
      const wide = width >= 1024
      if (wide) {
        await expect(page.locator('.menu-button')).toBeHidden()
        await expect(page.locator('nav.primary')).toBeVisible()
        await expect(page.locator('nav.primary a')).toHaveCount(7)
      } else {
        await expect(page.locator('nav.primary')).toBeHidden()
        await expect(page.locator('.menu-button')).toBeVisible()
        await expect(page.locator('.profile-button')).toBeHidden()
      }
      if (kind === 'guest') {
        await expect(page.locator('.login-pill', { hasText: 'Войти' })).toBeVisible()
        await expect(page.locator('.profile-button')).toHaveCount(0)
        if (!wide) {
          await page.locator('.menu-button').click()
          const items = await panelItems(page)
          expect(items.map((item) => item.text)).toEqual(nav)
          expect(items.some((item) => item.sep)).toBe(false)
          await expect(page.locator('.menu-panel').getByText('Войти')).toHaveCount(0)
          await page.locator('.menu-button').click()
        }
        continue
      }
      await expect(page.locator('.login-pill')).toHaveCount(0)
      const expected = ['Профиль', roleItem[kind]!, 'Выйти']
      if (wide) {
        await page.locator('.profile-button').click()
        const items = await panelItems(page)
        expect(items.map((item) => item.text)).toEqual(expected)
        for (const label of forbidden[kind]) {
          expect(items.map((item) => item.text)).not.toContain(label)
        }
        await page.keyboard.press('Escape')
        await expect(page.locator('.menu-panel')).toHaveCount(0)
      } else {
        const closed = await barText(page)
        for (const label of ['Профиль', 'Выйти', 'Моя команда', 'Пульт судьи', 'Админка', 'Аналитик']) {
          expect(closed).not.toContain(label)
        }
        await page.locator('.menu-button').click()
        const items = await panelItems(page)
        const sepAt = items.findIndex((item) => item.sep)
        expect(sepAt).toBe(nav.length)
        expect(items.slice(0, sepAt).map((item) => item.text)).toEqual(nav)
        expect(items.slice(sepAt + 1).map((item) => item.text)).toEqual(expected)
        for (const label of forbidden[kind]) {
          expect(items.map((item) => item.text)).not.toContain(label)
        }
        const openBar = await barText(page)
        for (const label of ['Профиль', 'Выйти', roleItem[kind]!]) {
          expect(openBar).not.toContain(label)
        }
      }
      if (kind === 'referee' && width === 1920) {
        await expect(page.locator('.menu-button')).toBeHidden()
        await expect(page.locator('.profile-button')).toBeVisible()
        await page.screenshot({ path: '/opt/cursor/artifacts/qa105-referee-1920.png' })
      }
      if (kind === 'referee' && width === 375) {
        await expect(page.locator('.menu-panel')).toBeVisible()
        await page.screenshot({ path: '/opt/cursor/artifacts/qa105-referee-375.png' })
      }
    }
  }
})
