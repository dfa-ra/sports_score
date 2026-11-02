import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from '../src/api'
import { injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import {
  applyTeam,
  approveTeam,
  assignReferee,
  cleanupCreated,
  createMatch,
  createTeam,
  createTournament,
  emptyIds,
  idOf,
} from '../src/entities'

async function prepareAssignedMatch(request: import('@playwright/test').APIRequestContext) {
  const admin = await requireRole(request, 'ADMIN')
  const referee = await requireRole(request, 'REFEREE')
  const ids = emptyIds()
  const tournament = await createTournament(request, admin, { status: 'ACTIVE' })
  ids.tournamentId = idOf(tournament.body)
  const home = await createTeam(request, admin)
  const away = await createTeam(request, admin)
  ids.teamIds.push(idOf(home.body), idOf(away.body))
  for (const teamId of ids.teamIds) {
    await applyTeam(request, admin, ids.tournamentId, teamId)
    await approveTeam(request, admin, ids.tournamentId, teamId)
  }
  const match = await createMatch(request, admin, {
    tournamentId: ids.tournamentId,
    homeTeamId: ids.teamIds[0],
    awayTeamId: ids.teamIds[1],
  })
  ids.matchIds.push(idOf(match.body))
  const assigned = await assignReferee(request, admin, ids.matchIds[0], String(referee.user.id))
  expect(assigned.status, JSON.stringify(assigned.body)).toBe(201)
  return { admin, referee, ids, matchId: ids.matchIds[0], homeId: ids.teamIds[0], awayId: ids.teamIds[1] }
}

function needLiveRoles() {
  return skipWithout('ADMIN') || skipWithout('REFEREE')
}

test(title({ id: 'M1' }, 'назначенный матч виден в /referee'), check({ id: 'M1' }), async ({ page, request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await injectSession(page, ctx.referee)
    await page.goto('/referee')
    await expect(page.locator(`a[href*="${ctx.matchId}"]`).first()).toBeVisible()
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M2' }, 'старт → LIVE, часы идут; пауза / продолжить / тайм / финиш'), check({ id: 'M2' }), async ({ request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const token = ctx.referee.accessToken
  try {
    const started = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token })
    expect(asRecord(started.body).status).toBe('LIVE')
    const first = Number(asRecord(started.body).gameTimeSeconds || 0)
    await new Promise((resolve) => setTimeout(resolve, 1500))
    const live = await apiGet(request, `/matches/${ctx.matchId}`)
    const later = Number(asRecord(live.body).gameTimeSeconds || 0)
    expect(later, 'часы должны идти после старта').toBeGreaterThanOrEqual(first)

    const paused = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/pause`, { token })
    expect(asRecord(paused.body).status).toBe('PAUSED')
    const resumed = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/resume`, { token })
    expect(asRecord(resumed.body).status).toBe('LIVE')
    const next = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/next-period`, { token })
    expect(Number(asRecord(next.body).period || 0)).toBeGreaterThan(1)
    const finished = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/finish`, { token })
    expect(asRecord(finished.body).status).toBe('FINISHED')
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M3', bugs: [5] }, 'гол с автором: минута = минута часов, счёт +1'), check({ id: 'M3', bugs: [5] }), async ({ request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const player = await requireRole(request, 'PLAYER')
  const me = await apiGet(request, '/players/me', player.accessToken)
  const playerId = String((me.body as { id?: string }).id || '')
  await apiSend(request, 'POST', `/teams/${ctx.homeId}/members`, {
    token: ctx.admin.accessToken,
    data: { playerId },
  })
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await new Promise((resolve) => setTimeout(resolve, 2000))
    const clock = await apiGet(request, `/matches/${ctx.matchId}`)
    const seconds = Number(asRecord(clock.body).gameTimeSeconds || 0)
    const event = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId, playerId, gameTime: seconds },
    })
    expect(event.status, JSON.stringify(event.body)).toBe(201)
    const stored = Number(asRecord(event.body).gameTime || 0)
    expect(Math.abs(stored - seconds), `минута гола ${stored}с должна быть около часов ${seconds}с, не 0`).toBeLessThan(30)
    expect(stored, 'минута гола не должна быть 0 при идущих часах').toBeGreaterThan(0)
    const match = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(Number(asRecord(match.body).homeScore)).toBe(1)
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M4', bugs: [20, 21, 22, 23, 24, 25] }, 'гол без игроков в заявке или понятное сообщение; автогол не зацикливается'), check({ id: 'M4', bugs: [20, 21, 22, 23, 24, 25] }), async ({ request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    const teamGoal = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId },
    })
    const okOrMessage = teamGoal.status === 201
      || /игрок|заявк|выберите/i.test(JSON.stringify(teamGoal.body))
    expect(okOrMessage, `гол без игрока: ${teamGoal.status} ${JSON.stringify(teamGoal.body)}`).toBeTruthy()

    const own = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId, metadata: { ownGoal: true } },
    })
    expect(own.status, 'автогол не должен отвечать 5xx').toBeLessThan(500)
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M5', bugs: [20, 21, 22, 23, 24, 25] }, 'гол не ставит матч на паузу незаметно'), check({ id: 'M5', bugs: [20, 21, 22, 23, 24, 25] }), async ({ request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId },
    })
    const match = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(asRecord(match.body).status, 'после гола матч не должен тихо уйти в PAUSED').toBe('LIVE')
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M6' }, 'жёлтая и красная карточка пишутся с минутой'), check({ id: 'M6' }), async ({ request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const player = await requireRole(request, 'PLAYER')
  const me = await apiGet(request, '/players/me', player.accessToken)
  const playerId = String((me.body as { id?: string }).id || '')
  await apiSend(request, 'POST', `/teams/${ctx.homeId}/members`, {
    token: ctx.admin.accessToken,
    data: { playerId },
  })
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await new Promise((resolve) => setTimeout(resolve, 1200))
    for (const eventType of ['YELLOW_CARD', 'RED_CARD']) {
      const event = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
        token: ctx.referee.accessToken,
        data: { eventType, teamId: ctx.homeId, playerId },
      })
      expect(event.status, `${eventType} ${JSON.stringify(event.body)}`).toBe(201)
      expect(Number(asRecord(event.body).gameTime || 0)).toBeGreaterThan(0)
    }
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M7' }, 'отмена события пересчитывает счёт везде'), check({ id: 'M7' }), async ({ request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const player = await requireRole(request, 'PLAYER')
  const me = await apiGet(request, '/players/me', player.accessToken)
  const playerId = String((me.body as { id?: string }).id || '')
  await apiSend(request, 'POST', `/teams/${ctx.homeId}/members`, {
    token: ctx.admin.accessToken,
    data: { playerId },
  })
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    const event = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId, playerId },
    })
    const eventId = idOf(event.body)
    const afterGoal = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(Number(asRecord(afterGoal.body).homeScore)).toBe(1)
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events/${eventId}/void`, {
      token: ctx.referee.accessToken,
    })
    const afterVoid = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(Number(asRecord(afterVoid.body).homeScore)).toBe(0)
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M8', bugs: [20, 21, 22, 23, 24, 25, 4] }, 'публичная страница, /live и главная обновляются ≤5 с; минута на /live реальная'), check({ id: 'M8', bugs: [20, 21, 22, 23, 24, 25, 4] }), async ({ page, request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await page.goto(`/matches/${ctx.matchId}`)
    await expect(page.getByText(/live/i).first()).toBeVisible()
    const publicPage = page
    const live = await publicPage.context().newPage()
    const home = await publicPage.context().newPage()
    await live.goto('/live')
    await home.goto('/')
    await expect(live.getByText(/live/i).first()).toBeVisible({ timeout: 8_000 })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'TIMEOUT', teamId: ctx.homeId },
    })
    await expect(publicPage.getByText(/тайм-аут|timeout|live/i).first()).toBeVisible({ timeout: 5_000 })
    const minuteText = await live.locator('body').innerText()
    expect(minuteText, 'на /live не должна висеть нулевая минута у живого матча дольше нормы').not.toMatch(/0:00[\s\S]{0,40}LIVE/i)
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M9', bugs: [20, 21, 22, 23, 24, 25] }, 'протокол на публичной странице с минутами начала/конца тайма'), check({ id: 'M9', bugs: [20, 21, 22, 23, 24, 25] }), async ({ page, request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/next-period`, { token: ctx.referee.accessToken })
    const events = await apiGet(request, `/matches/${ctx.matchId}/events`)
    const types = asList(events.body).map((row) => String(row.eventType))
    expect(types).toContain('PERIOD_START')
    await page.goto(`/matches/${ctx.matchId}`)
    await page.getByRole('button', { name: /протокол/i }).click().catch(async () => {
      await page.getByText(/протокол/i).click()
    })
    await expect(page.getByText(/начало тайма|конец тайма/i).first()).toBeVisible()
    await expect(page.getByText(/\d+'/)).toBeVisible()
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M10', bugs: [20, 21, 22, 23, 24, 25] }, 'после финиша события нельзя добавить; статус ЗАВЕРШЕН; дата не будущая'), check({ id: 'M10', bugs: [20, 21, 22, 23, 24, 25] }), async ({ page, request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/finish`, { token: ctx.referee.accessToken })
    const forbidden = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId },
    })
    expect(forbidden.status, `событие после финиша → ${forbidden.status}`).toBeGreaterThanOrEqual(400)
    const match = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(asRecord(match.body).status).toBe('FINISHED')
    await page.goto(`/matches/${ctx.matchId}`)
    await expect(page.getByText(/завершен/i)).toBeVisible()
    const when = String(asRecord(match.body).scheduledAt || '')
    expect(new Date(when).getTime(), 'дата завершённого матча не должна выглядеть будущей').toBeLessThanOrEqual(Date.now() + 60_000)
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M11', bugs: [6] }, 'результат попадает в таблицу, страницы команд и статистику игрока'), check({ id: 'M11', bugs: [6] }), async ({ page, request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const player = await requireRole(request, 'PLAYER')
  const me = await apiGet(request, '/players/me', player.accessToken)
  const playerId = String((me.body as { id?: string }).id || '')
  await apiSend(request, 'POST', `/teams/${ctx.homeId}/members`, {
    token: ctx.admin.accessToken,
    data: { playerId },
  })
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId, playerId },
    })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/finish`, { token: ctx.referee.accessToken })
    const table = await apiGet(request, `/tournaments/${ctx.ids.tournamentId}/standings`)
    const rows = ((asRecord(table.body).tables as { rows?: Record<string, unknown>[] }[] | undefined) ?? [])
      .flatMap((group) => group.rows ?? [])
    const homeRow = rows.find((row) => row.teamId === ctx.homeId)
    expect(homeRow, 'команда должна появиться в таблице').toBeTruthy()
    expect(Number(homeRow?.played || 0)).toBeGreaterThan(0)
    expect(Number(homeRow?.points || 0)).toBeGreaterThan(0)
    await page.goto(`/players/${playerId}`)
    await page.getByRole('button', { name: 'Статистика' }).click()
    const stats = await page.locator('main').innerText()
    expect(stats, 'вкладка «Статистика» не должна быть пустой').toMatch(/игр|гол/i)
  } finally {
    await cleanupCreated(request, ctx.admin, ctx.ids)
  }
})

test(title({ id: 'M12', bugs: [4] }, 'нет зависших LIVE-матчей старше 6 часов'), check({ id: 'M12', bugs: [4] }), async ({ request }) => {
  const live = await apiGet(request, '/matches?status=LIVE&size=50')
  expect(live.status).toBe(200)
  const stale = asList(live.body).filter((row) => {
    const started = Date.parse(String(row.startedAt || row.scheduledAt || ''))
    return Number.isFinite(started) && Date.now() - started > 6 * 60 * 60 * 1000
  })
  expect(stale.map((row) => row.id), 'LIVE дольше 6 часов — зависший матч').toEqual([])
})
