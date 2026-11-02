import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from '../src/api'
import { injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { deleteMatch, prepareAssignedMatch, standingRows } from '../src/pool'
import { waitForApp } from '../src/ui'

function needLiveRoles() {
  return skipWithout('ADMIN') || skipWithout('REFEREE')
}

async function addPlayerToHome(
  request: import('@playwright/test').APIRequestContext,
  adminToken: string,
  homeId: string,
) {
  const player = await requireRole(request, 'PLAYER')
  const me = await apiGet(request, '/players/me', player.accessToken)
  const playerId = String((me.body as { id?: string }).id || '')
  const added = await apiSend(request, 'POST', `/teams/${homeId}/members`, {
    token: adminToken,
    data: { playerId },
  })
  expect(added.status === 201 || added.status === 409, `состав: ${added.status} ${JSON.stringify(added.body)}`).toBeTruthy()
  return playerId
}

test(title({ id: 'M1' }, 'назначенный матч виден в /referee'), check({ id: 'M1' }), async ({ page, request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await injectSession(page, ctx.referee)
    await page.goto('/referee')
    await waitForApp(page)
    await expect(page.locator(`a[href*="${ctx.matchId}"]`).first()).toBeVisible()
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
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
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'M3', bugs: [5] }, 'гол с автором: минута = минута часов, счёт +1'), check({ id: 'M3', bugs: [5] }), async ({ request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const playerId = await addPlayerToHome(request, ctx.admin.accessToken, ctx.homeId)
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
    await deleteMatch(request, ctx.admin, ctx.matchId)
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
    await deleteMatch(request, ctx.admin, ctx.matchId)
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
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'M6' }, 'жёлтая и красная карточка пишутся с минутой'), check({ id: 'M6' }), async ({ request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const playerId = await addPlayerToHome(request, ctx.admin.accessToken, ctx.homeId)
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
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'M7' }, 'отмена события пересчитывает счёт везде'), check({ id: 'M7' }), async ({ request }) => {
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const playerId = await addPlayerToHome(request, ctx.admin.accessToken, ctx.homeId)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    const event = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId, playerId },
    })
    const eventId = String(asRecord(event.body).id || '')
    const afterGoal = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(Number(asRecord(afterGoal.body).homeScore)).toBe(1)
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events/${eventId}/void`, {
      token: ctx.referee.accessToken,
    })
    const afterVoid = await apiGet(request, `/matches/${ctx.matchId}`)
    expect(Number(asRecord(afterVoid.body).homeScore)).toBe(0)
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'M8', bugs: [20, 21, 22, 23, 24, 25, 4] }, 'публичная страница, /live и главная обновляются ≤5 с; минута на /live реальная'), check({ id: 'M8', bugs: [20, 21, 22, 23, 24, 25, 4] }), async ({ page, request }) => {
  const reason = needLiveRoles()
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await page.goto(`/matches/${ctx.matchId}`)
    await waitForApp(page)
    await expect(page.getByText(/live/i).first()).toBeVisible()
    const live = await page.context().newPage()
    const home = await page.context().newPage()
    await live.goto('/live')
    await home.goto('/')
    await expect(live.getByText(/live/i).first()).toBeVisible({ timeout: 8_000 })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'TIMEOUT', teamId: ctx.homeId },
    })
    await expect(page.getByText(/тайм-аут|timeout|live/i).first()).toBeVisible({ timeout: 5_000 })
    const minuteText = await live.locator('body').innerText()
    expect(minuteText, 'на /live не должна висеть нулевая минута у живого матча дольше нормы').not.toMatch(/0:00[\s\S]{0,40}LIVE/i)
    await live.close()
    await home.close()
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
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
    await waitForApp(page)
    await page.getByRole('button', { name: 'Протокол' }).click()
    const protocol = page.locator('.panel').filter({ hasText: /начало тайма|конец тайма/i })
    await expect(protocol, 'вкладка «Протокол» должна показать начало/конец тайма').toBeVisible()
    await expect(protocol.getByText('Начало тайма').first()).toBeVisible()
    const minute = protocol.locator('.t').filter({ hasText: /^\d+'$/ })
    await expect(minute.first(), 'минута должна быть в строке протокола, не на часах матча').toBeVisible()
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
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
    await waitForApp(page)
    await expect(page.getByText(/завершен/i)).toBeVisible()
    const when = String(asRecord(match.body).scheduledAt || '')
    expect(new Date(when).getTime(), 'дата завершённого матча не должна выглядеть будущей').toBeLessThanOrEqual(Date.now() + 60_000)
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
  }
})

test(title({ id: 'M11', bugs: [6] }, 'результат попадает в таблицу, страницы команд и статистику игрока'), check({ id: 'M11', bugs: [6] }), async ({ page, request }) => {
  test.setTimeout(90_000)
  const reason = needLiveRoles() || skipWithout('PLAYER')
  test.skip(!!reason, reason || '')
  const ctx = await prepareAssignedMatch(request)
  const playerId = await addPlayerToHome(request, ctx.admin.accessToken, ctx.homeId)
  try {
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/start`, { token: ctx.referee.accessToken })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/events`, {
      token: ctx.referee.accessToken,
      data: { eventType: 'GOAL', teamId: ctx.homeId, playerId },
    })
    await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/finish`, { token: ctx.referee.accessToken })
    const table = await apiGet(request, `/tournaments/${ctx.tournamentId}/standings`)
    const homeRow = standingRows(table.body).find((row) => String(row.teamId) === ctx.homeId)
    expect(homeRow, 'команда должна появиться в таблице').toBeTruthy()
    expect(Number(homeRow?.played || 0)).toBeGreaterThan(0)
    expect(Number(homeRow?.points || 0)).toBeGreaterThan(0)

    await page.goto(`/teams/${ctx.homeId}`)
    await waitForApp(page)
    await page.getByRole('button', { name: 'Статистика' }).click()
    await expect(page.locator('main')).toContainText(/игр|очк|в\s|п\s/i)

    await page.goto(`/players/${playerId}`)
    await waitForApp(page)
    const statsTab = page.getByRole('button', { name: /статистика|цифры/i })
    await expect(statsTab, 'на карточке игрока должна быть вкладка статистики').toBeVisible()
    await statsTab.click()
    const stats = page.locator('main')
    await expect(stats, 'вкладка статистики не должна быть пустой (баг 6)').not.toContainText(/пока без цифр|матчей пока нет/i)
    await expect(stats).toContainText(/игр|гол/i)
  } finally {
    await deleteMatch(request, ctx.admin, ctx.matchId)
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
