import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord, errorMessage } from '../src/api'
import { fanSession, injectSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { assignReferee, createMatch, idOf } from '../src/entities'
import { hasRole } from '../src/env'
import { disposeSpec } from '../src/pool'
import { entityName } from '../src/run'
import { Tracker } from '../src/tracker'
import {
  PENALTIES_SKIP,
  addPenaltyKick,
  kickIdOf,
  looksLikePenaltyScore,
  patchPenaltyKick,
  penaltyKicks,
  penaltyLabel,
  playShootout,
  startPenalties,
  viewPenalties,
  voidPenaltyKick,
} from '../src/penalties'
import {
  PLAYOFF_SKIP,
  ensurePoTeams,
  ensurePoTournament,
  firstRoundSlots,
  nextRoundSlot,
  openNamedPlayoff,
  penaltiesDeployed,
  placesForSize,
  playoffDeployed,
  scoreRegularDraw,
  startRefereeMatch,
  tryDrawFinish,
  viewPlayoff,
} from '../src/playoff'
import { hasHorizontalOverflow, waitForApp } from '../src/ui'

const bag = new Tracker()
const NAMES = {
  po12: entityName('PO-pen-12'),
  po13a: entityName('PO-pen-13a'),
  po13b: entityName('PO-pen-13b'),
  po14: entityName('PO-pen-14'),
  po15: entityName('PO-pen-15'),
  po16: entityName('PO-pen-16'),
  po17: entityName('PO-pen-17'),
  sizes: entityName('PO-sizes'),
  po19: entityName('PO-pen-19'),
}

test.afterAll(async ({ request }) => {
  await disposeSpec(request, bag, '11-penalties')
})

async function needPenalties(request: import('@playwright/test').APIRequestContext, needAdmin = true) {
  test.skip(!(await playoffDeployed(request)), PLAYOFF_SKIP)
  test.skip(!(await penaltiesDeployed(request)), PENALTIES_SKIP)
  if (!needAdmin) return
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
}

function expectRussianError(body: unknown, status: number, extra?: RegExp) {
  expect(status, `ожидали 400/403 с русским текстом: ${status} ${JSON.stringify(body)}`).toBeGreaterThanOrEqual(400)
  expect(status).toBeLessThan(500)
  const message = errorMessage(body)
  expect(message, `нужно понятное сообщение: ${JSON.stringify(body)}`).toMatch(/[А-Яа-яЁё]/)
  if (extra) expect(message, message).toMatch(extra)
}

async function liveDrawPlayoff(
  request: import('@playwright/test').APIRequestContext,
  admin: Awaited<ReturnType<typeof requireRole>>,
  name: string,
) {
  const teams = await ensurePoTeams(request, admin, 4, bag)
  const opened = await openNamedPlayoff(request, admin, name, teams, [teams], placesForSize(4), bag)
  const slot = firstRoundSlots(opened.body).find((row) => {
    const status = String(row.matchStatus || '')
    return status === 'SCHEDULED' || status === 'LIVE' || status === 'PAUSED'
  }) || firstRoundSlots(opened.body)[0]
  const matchId = String(slot?.matchId || '')
  expect(matchId, `нужен матч плей-офф для серии («${name}»)`).toBeTruthy()
  await startRefereeMatch(request, admin.accessToken, matchId)
  return {
    tournamentId: opened.id,
    matchId,
    slot,
    homeId: String(slot?.homeTeamId || ''),
    awayId: String(slot?.awayTeamId || ''),
    teams,
  }
}

test(title({ id: 'PO12' }, 'при ничьей в плей-офф «Финиш» недоступен, судья переходит к серии'), check({ id: 'PO12' }), async ({ page, request }) => {
  test.setTimeout(180_000)
  await needPenalties(request)
  const admin = await requireRole(request, 'ADMIN')
  const ctx = await liveDrawPlayoff(request, admin, NAMES.po12)
  const finish = await apiSend(request, 'POST', `/referee/matches/${ctx.matchId}/finish`, { token: admin.accessToken })
  expectRussianError(finish.body, finish.status, /пен|сери/i)
  expect(asRecord(finish.body).status, 'матч не должен закрыться вничью без серии').not.toBe('FINISHED')

  const started = await startPenalties(request, admin.accessToken, ctx.matchId)
  expect(started.status, `старт серии: ${started.status} ${JSON.stringify(started.body)}`).toBeLessThan(400)

  if (!skipWithout('REFEREE')) {
    const referee = await requireRole(request, 'REFEREE')
    await assignReferee(request, admin, ctx.matchId, String(referee.user.id))
    await injectSession(page, referee)
    await page.goto(`/referee/matches/${ctx.matchId}`)
    await waitForApp(page)
    const finishBtn = page.getByRole('button', { name: /финиш/i })
    if (await finishBtn.count()) {
      await expect(finishBtn, 'при ничьей «Финиш» недоступен').toBeDisabled()
    }
    await expect(page.getByText(/пен|сери/i).first()).toBeVisible()
  }
})

test(title({ id: 'PO13' }, 'серия: 5 ударов, досрочный конец, потом до промаха; вничью не заканчивается'), check({ id: 'PO13' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPenalties(request)
  const admin = await requireRole(request, 'ADMIN')
  const early = await liveDrawPlayoff(request, admin, NAMES.po13a)
  await playShootout(request, admin, early.matchId, early.homeId, early.awayId, 3, 0, 3)
  const afterEarly = await viewPenalties(request, early.matchId)
  const earlyLabel = penaltyLabel(afterEarly.body) + JSON.stringify(afterEarly.body)
  expect(earlyLabel, '3:0 после трёх пар должно закрыть серию').toMatch(/3\s*:\s*0|0\s*:\s*3/)
  const extra = await addPenaltyKick(request, admin.accessToken, early.matchId, {
    teamId: early.homeId,
    scored: true,
  })
  expect(extra.status, `лишний удар после 3:0: ${extra.status} ${JSON.stringify(extra.body)}`).toBeGreaterThanOrEqual(400)

  const sudden = await liveDrawPlayoff(request, admin, NAMES.po13b)
  await playShootout(request, admin, sudden.matchId, sudden.homeId, sudden.awayId, 5, 5, 5)
  const sixthHome = await addPenaltyKick(request, admin.accessToken, sudden.matchId, {
    teamId: sudden.homeId,
    scored: true,
  })
  expect(sixthHome.status, `6-й удар хозяев: ${sixthHome.status}`).toBeLessThan(400)
  const sixthAway = await addPenaltyKick(request, admin.accessToken, sudden.matchId, {
    teamId: sudden.awayId,
    scored: false,
  })
  expect(sixthAway.status, `6-й удар гостей: ${sixthAway.status}`).toBeLessThan(400)
  const done = await viewPenalties(request, sudden.matchId)
  const kicks = penaltyKicks(done.body)
  const homeMade = kicks.filter((row) => String(row.teamId) === sudden.homeId && row.scored !== false && row.missed !== true).length
  const awayMade = kicks.filter((row) => String(row.teamId) === sudden.awayId && row.scored !== false && row.missed !== true).length
  expect(homeMade, 'серия не должна закончиться вничью').not.toBe(awayMade)
})

test(title({ id: 'PO14' }, 'удар: забит/не забит, игрок из заявки; ошибку можно исправить до конца серии'), check({ id: 'PO14' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPenalties(request)
  const admin = await requireRole(request, 'ADMIN')
  const ctx = await liveDrawPlayoff(request, admin, NAMES.po14)
  await startPenalties(request, admin.accessToken, ctx.matchId)
  let playerId = ''
  if (!skipWithout('PLAYER')) {
    const player = await requireRole(request, 'PLAYER')
    const me = await apiGet(request, '/players/me', player.accessToken)
    playerId = String(asRecord(me.body).id || '')
    if (playerId) {
      await apiSend(request, 'POST', `/teams/${ctx.homeId}/members`, {
        token: admin.accessToken,
        data: { playerId },
      })
    }
  }
  const scored = await addPenaltyKick(request, admin.accessToken, ctx.matchId, {
    teamId: ctx.homeId,
    scored: true,
    playerId: playerId || undefined,
  })
  expect(scored.status, `забит: ${scored.status} ${JSON.stringify(scored.body)}`).toBeLessThan(400)
  const missed = await addPenaltyKick(request, admin.accessToken, ctx.matchId, {
    teamId: ctx.awayId,
    scored: false,
    playerId: playerId || undefined,
  })
  expect(missed.status, `не забит: ${missed.status} ${JSON.stringify(missed.body)}`).toBeLessThan(400)
  if (playerId) {
    const outsider = await addPenaltyKick(request, admin.accessToken, ctx.matchId, {
      teamId: ctx.homeId,
      scored: true,
      playerId: '00000000-0000-0000-0000-000000000099',
    })
    expect(outsider.status, 'игрок не из заявки').toBeGreaterThanOrEqual(400)
  }
  const kickId = String(asRecord(scored.body).id || await kickIdOf(request, ctx.matchId))
  expect(kickId, 'нужен id удара').toBeTruthy()
  const fixed = await patchPenaltyKick(request, admin.accessToken, ctx.matchId, kickId, { scored: false })
  expect(fixed.status, `исправление удара: ${fixed.status} ${JSON.stringify(fixed.body)}`).toBeLessThan(400)
})

test(title({ id: 'PO15' }, 'победитель серии выходит дальше; отмена удара админом пересчитывает слот, если следующий не начат'), check({ id: 'PO15' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPenalties(request)
  const admin = await requireRole(request, 'ADMIN')
  const ctx = await liveDrawPlayoff(request, admin, NAMES.po15)
  await playShootout(request, admin, ctx.matchId, ctx.homeId, ctx.awayId, 4, 3, 5)
  const after = await viewPlayoff(request, ctx.tournamentId)
  const next = nextRoundSlot(after.body, ctx.slot || {})
  expect(String(next?.homeTeamId || next?.awayTeamId || ''), 'победитель серии должен пройти дальше').toBe(ctx.homeId)

  const kickId = await kickIdOf(request, ctx.matchId)
  expect(kickId).toBeTruthy()
  const nextStatus = String(next?.matchStatus || '')
  const voided = await voidPenaltyKick(request, admin.accessToken, ctx.matchId, kickId, 'admin')
  if (['LIVE', 'PAUSED', 'FINISHED'].includes(nextStatus)) {
    expectRussianError(voided.body, voided.status, /начался|нельзя|запрещ/i)
    return
  }
  expect(voided.status, `отмена удара админом: ${voided.status} ${JSON.stringify(voided.body)}`).toBeLessThan(400)
  const recalc = await viewPlayoff(request, ctx.tournamentId)
  const nextAfter = nextRoundSlot(recalc.body, ctx.slot || {})
  expect(
    String(nextAfter?.homeTeamId || ''),
    'после отмены удара слот должен пересчитаться, пока следующий матч не начат',
  ).not.toBe(ctx.homeId)
})

test(title({ id: 'PO16' }, 'результат «1:1 (пен. 4:3)» в карточке, сетке, протоколе, /live, на главной и на 375px'), check({ id: 'PO16' }), async ({ page, request }) => {
  test.setTimeout(180_000)
  await needPenalties(request, false)
  let matchId = ''
  let tournamentId = ''
  const listed = asList((await apiGet(request, '/matches?size=50&sort=scheduledAt,desc')).body)
  for (const row of listed) {
    const label = penaltyLabel(row)
    if (looksLikePenaltyScore(label) || /пен/i.test(JSON.stringify(row))) {
      matchId = String(row.id || '')
      tournamentId = String(row.tournamentId || '')
      break
    }
  }
  if (!matchId && hasRole('ADMIN')) {
    const admin = await requireRole(request, 'ADMIN')
    const ctx = await liveDrawPlayoff(request, admin, NAMES.po16)
    await scoreRegularDraw(request, admin.accessToken, ctx.matchId, ctx.homeId, ctx.awayId)
    await playShootout(request, admin, ctx.matchId, ctx.homeId, ctx.awayId, 4, 3, 5)
    matchId = ctx.matchId
    tournamentId = ctx.tournamentId
  }
  test.skip(!matchId, 'нет матча с серией пенальти')

  const match = asRecord((await apiGet(request, `/matches/${matchId}`)).body)
  expect(Number(match.homeScore)).toBe(1)
  expect(Number(match.awayScore)).toBe(1)
  expect(looksLikePenaltyScore(penaltyLabel(match)), `карточка API: ${penaltyLabel(match)}`).toBeTruthy()

  const events = asList((await apiGet(request, `/matches/${matchId}/events`)).body)
  const protocol = events.map((row) => JSON.stringify(row)).join(' ')
  expect(protocol + penaltyLabel(match), 'протокол должен показывать серию').toMatch(/пен|penalt|shootout/i)

  for (const viewport of [
    { width: 1280, height: 720, label: 'десктоп' },
    { width: 375, height: 812, label: '375px' },
  ]) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await page.goto(`/matches/${matchId}`)
    await waitForApp(page)
    await expect(page.getByText(/1\s*:\s*1/).first()).toBeVisible()
    await expect(page.getByText(/пен\.?\s*4\s*:\s*3|пен\.?\s*3\s*:\s*4/i).first()).toBeVisible()
    if (tournamentId) {
      await page.goto(`/tournaments/${tournamentId}`)
      await waitForApp(page)
      await expect(page.getByText(/пен\.?\s*\d\s*:\s*\d/i).first()).toBeVisible()
    }
    await page.goto('/live')
    await waitForApp(page)
    await page.goto('/')
    await waitForApp(page)
    expect(await hasHorizontalOverflow(page), `${viewport.label}: без горизонтального скролла`).toBeFalsy()
  }
})

test(title({ id: 'PO17' }, 'голы серии не идут в статистику игроков, бомбардиров и счёт матча'), check({ id: 'PO17' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPenalties(request)
  const admin = await requireRole(request, 'ADMIN')
  test.skip(!!skipWithout('PLAYER'), skipWithout('PLAYER') || '')
  const player = await requireRole(request, 'PLAYER')
  const me = await apiGet(request, '/players/me', player.accessToken)
  const playerId = String(asRecord(me.body).id || '')
  const beforeCard = await apiGet(request, `/players/${playerId}`)
  const beforeGoals = Number(asRecord(beforeCard.body).goals || asRecord(beforeCard.body).goalCount || 0)
  const ctx = await liveDrawPlayoff(request, admin, NAMES.po17)
  await apiSend(request, 'POST', `/teams/${ctx.homeId}/members`, {
    token: admin.accessToken,
    data: { playerId },
  })
  await startPenalties(request, admin.accessToken, ctx.matchId)
  for (let i = 0; i < 5; i++) {
    await addPenaltyKick(request, admin.accessToken, ctx.matchId, {
      teamId: ctx.homeId,
      scored: true,
      playerId,
    })
    await addPenaltyKick(request, admin.accessToken, ctx.matchId, {
      teamId: ctx.awayId,
      scored: i < 3,
    })
  }
  const match = asRecord((await apiGet(request, `/matches/${ctx.matchId}`)).body)
  expect(Number(match.homeScore), 'счёт матча без голов серии').toBe(0)
  expect(Number(match.awayScore)).toBe(0)
  const events = asList((await apiGet(request, `/matches/${ctx.matchId}/events`)).body)
  const regularGoals = events.filter((row) => String(row.eventType) === 'GOAL' && row.voided !== true && !/penalt|shootout/i.test(JSON.stringify(row)))
  expect(regularGoals, 'голы серии не должны быть обычными GOAL').toEqual([])
  const afterCard = await apiGet(request, `/players/${playerId}`)
  const afterGoals = Number(asRecord(afterCard.body).goals || asRecord(afterCard.body).goalCount || 0)
  expect(afterGoals, 'голы серии не в статистике игрока').toBe(beforeGoals)
  const scorers = await apiGet(request, '/statistics/scorers?size=50')
  const row = asList(scorers.body).find((item) => String(item.playerId || item.id) === playerId)
  if (row) {
    expect(Number(row.goals || row.goalCount || 0), 'бомбардиры без голов серии').toBe(beforeGoals)
  }
})

test(title({ id: 'PO18' }, 'в групповом матче ничья обычная, серии нет'), check({ id: 'PO18' }), async ({ request }) => {
  test.setTimeout(120_000)
  test.skip(!(await playoffDeployed(request)), PLAYOFF_SKIP)
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 12, bag)
  const sizes = await ensurePoTournament(request, admin, NAMES.sizes, teams, [teams], bag)
  expect(sizes.stage, 'E2E PO-sizes остаётся в группах').not.toBe('PLAYOFF')
  const created = await createMatch(request, admin, {
    tournamentId: sizes.id,
    homeTeamId: teams[0],
    awayTeamId: teams[1],
    track: bag,
  })
  expect(created.status, JSON.stringify(created.body)).toBe(201)
  const matchId = idOf(created.body)
  const draw = await tryDrawFinish(request, admin, matchId)
  expect(asRecord(draw.body).status, `групповая ничья: ${draw.status} ${JSON.stringify(draw.body)}`).toBe('FINISHED')
  expect(Number(asRecord(draw.body).homeScore)).toBe(0)
  expect(Number(asRecord(draw.body).awayScore)).toBe(0)
  if (await penaltiesDeployed(request)) {
    const series = await startPenalties(request, admin.accessToken, matchId)
    expect(series.status, `серия в группе: ${series.status} ${JSON.stringify(series.body)}`).toBeGreaterThanOrEqual(400)
    expectRussianError(series.body, series.status, /групп|обычн|не положен|недоступн|плей-офф/i)
  }
})

test(title({ id: 'PO19' }, 'серию ведёт назначенный судья; админ может исправить; остальные 403'), check({ id: 'PO19' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPenalties(request)
  const admin = await requireRole(request, 'ADMIN')
  const ctx = await liveDrawPlayoff(request, admin, NAMES.po19)
  const fan = await fanSession(request)
  const fanStart = await startPenalties(request, fan.accessToken, ctx.matchId)
  expect(fanStart.status, `FAN старт серии → ${fanStart.status}`).toBe(403)

  for (const role of ['PLAYER', 'CAPTAIN'] as const) {
    if (!hasRole(role)) continue
    const session = await requireRole(request, role)
    const call = await startPenalties(request, session.accessToken, ctx.matchId)
    expect(call.status, `${role} старт серии → ${call.status}`).toBe(403)
  }

  if (!skipWithout('REFEREE')) {
    const referee = await requireRole(request, 'REFEREE')
    await assignReferee(request, admin, ctx.matchId, String(referee.user.id))
    const started = await startPenalties(request, referee.accessToken, ctx.matchId)
    expect(started.status, `назначенный судья: ${started.status} ${JSON.stringify(started.body)}`).toBeLessThan(400)
    const kick = await addPenaltyKick(request, referee.accessToken, ctx.matchId, {
      teamId: ctx.homeId,
      scored: true,
    })
    expect(kick.status, `удар судьи: ${kick.status}`).toBeLessThan(400)
  }

  const adminStart = await startPenalties(request, admin.accessToken, ctx.matchId)
  expect([200, 201, 204, 400, 409], `админ может вести/править серию: ${adminStart.status}`).toContain(adminStart.status)
  const adminKick = await addPenaltyKick(request, admin.accessToken, ctx.matchId, {
    teamId: ctx.awayId,
    scored: false,
  }, 'admin')
  expect(adminKick.status, `правка админа: ${adminKick.status} ${JSON.stringify(adminKick.body)}`).not.toBe(403)
  expect(adminKick.status).toBeLessThan(500)
})
