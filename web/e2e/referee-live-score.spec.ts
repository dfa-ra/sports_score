import { expect, test, type Page, type Route, type WebSocketRoute } from '@playwright/test'

const matchId = '054a825d-e176-4a5f-8c00-93b36f8958b1'
const homeTeamId = '11111111-1111-4111-8111-111111111111'
const awayTeamId = '22222222-2222-4222-8222-222222222222'
const playerId = '33333333-3333-4333-8333-333333333333'
const goalId = '44444444-4444-4444-8444-444444444444'

const user = {
  id: 'admin-1',
  email: 'referee@example.com',
  role: 'ADMIN',
  enabled: true,
  firstName: 'Судья',
  lastName: 'Лиги',
  roles: [{ role: 'ADMIN', status: 'APPROVED' }],
}

function accessToken() {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  const exp = Math.floor(Date.now() / 1000) + 60 * 60
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ exp })}.sig`
}

function matchBody(homeScore: number, awayScore: number) {
  return {
    id: matchId,
    tournamentId: '55555555-5555-4555-8555-555555555555',
    status: 'LIVE',
    sportCode: 'FOOTBALL',
    homeTeamId,
    awayTeamId,
    homeScore,
    awayScore,
    period: 1,
    periodCount: 2,
    periodLengthSeconds: 900,
    gameTimeSeconds: 30,
    clockRunningSince: '2026-11-03T12:00:00Z',
    scheduledAt: '2026-11-03T12:00:00Z',
  }
}

function goalEvent(voided: boolean) {
  return {
    id: goalId,
    matchId,
    eventType: 'GOAL',
    timestamp: '2026-11-03T12:01:00Z',
    gameTime: 60,
    period: 1,
    teamId: homeTeamId,
    playerId,
    playerName: 'Иванов Иван',
    playerFirstName: 'Иван',
    playerLastName: 'Иванов',
    playerJersey: 9,
    secondaryPlayerId: null,
    metadata: {},
    voided,
    voidedAt: voided ? '2026-11-03T12:02:00Z' : null,
    minute: 1,
  }
}

function liveFrame(voided: boolean) {
  return {
    type: voided ? 'MATCH_EVENT_VOIDED' : 'MATCH_EVENT',
    matchId,
    status: 'LIVE',
    homeScore: voided ? 0 : 1,
    awayScore: 0,
    gameTimeSeconds: 60,
    period: 1,
    periodCount: 2,
    periodLengthSeconds: 900,
    clockRunningSince: '2026-11-03T12:00:00Z',
    sportCode: 'FOOTBALL',
    lastEvent: goalEvent(voided),
  }
}

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

type Published = { homeScore: number; awayScore: number; events: ReturnType<typeof goalEvent>[] }

function deferred() {
  let resolve: () => void = () => {}
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

async function handshake(ws: WebSocketRoute, onSubscribe: () => void) {
  let subscription = 'sub-0'
  ws.onMessage((message) => {
    const text = typeof message === 'string' ? message : message.toString()
    let payload = ''
    try {
      const parsed = JSON.parse(text) as unknown
      payload = Array.isArray(parsed) ? String(parsed[0] ?? '') : text
    } catch {
      return
    }
    if (payload.startsWith('CONNECT')) {
      ws.send(`a${JSON.stringify(['CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0'])}`)
      return
    }
    if (payload.startsWith('SUBSCRIBE')) {
      subscription = (payload.match(/(?:^|\n)id:([^\n\r]+)/)?.[1] || subscription).trim()
      onSubscribe()
    }
  })
  ws.send('o')
  return {
    publish(body: unknown) {
      const frame = `MESSAGE\ndestination:/topic/matches/${matchId}\nsubscription:${subscription}\nmessage-id:${Date.now()}\ncontent-type:application/json\n\n${JSON.stringify(body)}\0`
      ws.send(`a${JSON.stringify([frame])}`)
    },
  }
}

async function installApi(
  page: Page,
  published: Published,
  gates: { goal: ReturnType<typeof deferred>; voidGoal: ReturnType<typeof deferred> },
  counts: { goals: number; voids: number },
  options: { staleReads: boolean; publish: (frame: unknown) => void },
) {
  await page.route('**/ws/info**', (route) => json(route, {
    entropy: 1,
    origins: ['*:*'],
    cookie_needed: false,
    websocket: true,
  }))
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    if (path.endsWith('/auth/me')) return json(route, user)
    if (path.endsWith('/teams') && method === 'GET') {
      return json(route, {
        content: [
          { id: homeTeamId, name: 'Север', shortName: 'Север' },
          { id: awayTeamId, name: 'Юг', shortName: 'Юг' },
        ],
      })
    }
    if (path.endsWith(`/teams/${homeTeamId}/members`) || path.endsWith(`/teams/${awayTeamId}/members`)) {
      const teamId = path.includes(homeTeamId) ? homeTeamId : awayTeamId
      return json(route, teamId === homeTeamId
        ? [{ playerId, teamId, firstName: 'Иван', lastName: 'Иванов', jerseyNumber: 9 }]
        : [{ playerId: '66666666-6666-4666-8666-666666666666', teamId, firstName: 'Пётр', lastName: 'Петров', jerseyNumber: 4 }])
    }
    if (path.endsWith(`/matches/${matchId}`) && method === 'GET') {
      const score = options.staleReads ? { home: 0, away: 0 } : { home: published.homeScore, away: published.awayScore }
      return json(route, matchBody(score.home, score.away))
    }
    if (path.endsWith(`/matches/${matchId}/events`) && method === 'GET') {
      return json(route, options.staleReads ? [] : published.events)
    }
    if (path.endsWith(`/matches/${matchId}/lineups`)) {
      return json(route, { home: { confirmed: false, starters: [], bench: [] }, away: { confirmed: false, starters: [], bench: [] } })
    }
    if (path.endsWith(`/matches/${matchId}/referees`)) return json(route, [])
    if (path.endsWith(`/matches/${matchId}/shootout`)) return json(route, { kicks: [], decisive: false })
    if (path.endsWith(`/matches/${matchId}/analyst-assignment`)) {
      return json(route, { matchId, mode: null, analyst: null, homeAnalyst: null, awayAnalyst: null })
    }
    if (path.endsWith('/referee/analysts')) return json(route, [])
    if (path.endsWith('/admin/referees')) return json(route, { content: [], totalPages: 0 })
    if (path.endsWith(`/teams/${homeTeamId}/form`) || path.endsWith(`/teams/${awayTeamId}/form`)) return json(route, [])
    if (path.endsWith('/matches') && method === 'GET') return json(route, { content: [] })
    if (path.includes('/tournaments/')) return json(route, { id: '55555555-5555-4555-8555-555555555555', name: 'Кубок' })
    if (method === 'POST' && path.endsWith(`/referee/matches/${matchId}/events`)) {
      counts.goals += 1
      await gates.goal.promise
      published.homeScore = 1
      published.awayScore = 0
      published.events = [goalEvent(false)]
      const event = goalEvent(false)
      try {
        options.publish(liveFrame(false))
      } catch {
        /* the public page can still read the published GET */
      }
      return json(route, event, 201)
    }
    if (method === 'POST' && path.endsWith(`/referee/matches/${matchId}/events/${goalId}/void`)) {
      counts.voids += 1
      await gates.voidGoal.promise
      published.homeScore = 0
      published.awayScore = 0
      published.events = [goalEvent(true)]
      const event = goalEvent(true)
      try {
        options.publish(liveFrame(true))
      } catch {
        /* the public page can still read the published GET */
      }
      return json(route, event)
    }
    return json(route, {})
  })
}

test('referee pad applies its own goal and void without a reload', async ({ page }) => {
  const published: Published = { homeScore: 0, awayScore: 0, events: [] }
  const gates = { goal: deferred(), voidGoal: deferred() }
  const counts = { goals: 0, voids: 0 }
  const viewers: Array<{ publish: (body: unknown) => void }> = []
  let subscribed = false
  let markSubscribed = () => {}
  const socketReady = new Promise<void>((resolve) => {
    markSubscribed = () => {
      if (subscribed) return
      subscribed = true
      resolve()
    }
  })

  const token = accessToken()
  await page.context().addInitScript(({ access, storedUser }) => {
    localStorage.setItem('sl_access', access)
    localStorage.setItem('sl_user', storedUser)
  }, { access: token, storedUser: JSON.stringify(user) })

  const viewer = await page.context().newPage()
  const publish = (frame: unknown) => viewers.forEach((socket) => socket.publish(frame))
  await installApi(viewer, published, gates, counts, { staleReads: false, publish })
  await viewer.routeWebSocket(/\/ws\/[^/]+\/[^/]+\/websocket/, async (ws) => {
    viewers.push(await handshake(ws, markSubscribed))
  })

  await installApi(page, published, gates, counts, {
    staleReads: true,
    publish,
  })
  await page.routeWebSocket(/\/ws\/[^/]+\/[^/]+\/websocket/, async (ws) => {
    await handshake(ws, () => {})
  })

  await Promise.all([
    viewer.goto(`/matches/${matchId}`),
    page.goto(`/referee/matches/${matchId}`),
  ])
  await expect(page.locator('.scoreboard .score')).toHaveText('0 : 0')
  await expect(viewer.locator('.board .score')).toHaveText(/0\s*-\s*0/)
  await socketReady

  const sheet = page.getByRole('dialog')
  await page.getByRole('button', { name: 'Гол', exact: true }).click()
  await sheet.getByRole('button', { name: 'Север' }).click()
  await sheet.getByRole('button', { name: '#9 Иванов Иван' }).click()
  await sheet.getByRole('button', { name: 'Без передачи' }).click()
  await expect(page.getByRole('button', { name: 'Гол', exact: true })).toBeDisabled()
  expect(counts.goals).toBe(1)
  gates.goal.resolve()

  await expect(page.locator('.scoreboard .score')).toHaveText('1 : 0')
  await expect(page.locator('.proto').filter({ hasText: 'Гол' })).toBeVisible()
  await expect(page.locator('.proto').filter({ hasText: 'Иванов' })).toBeVisible()
  expect(counts.goals).toBe(1)
  await expect(viewer.locator('.board .score')).toHaveText(/1\s*-\s*0/)
  await expect(viewer.locator('.sheet')).toContainText('Иванов')

  page.on('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Убрать' }).click()
  await expect(page.getByRole('button', { name: 'Убрать' })).toBeDisabled()
  expect(counts.voids).toBe(1)
  gates.voidGoal.resolve()

  await expect(page.locator('.scoreboard .score')).toHaveText('0 : 0')
  await expect(page.getByRole('button', { name: 'Убрать' })).toHaveCount(0)
  await expect(page.locator('.proto.voided')).toContainText('Гол')
  expect(counts.voids).toBe(1)
  await expect(viewer.locator('.board .score')).toHaveText(/0\s*-\s*0/)
  await expect(viewer.locator('.sheet')).not.toContainText('Иванов')
  await expect(viewer.locator('.empty-line')).toHaveText('Событий нет.')
})
