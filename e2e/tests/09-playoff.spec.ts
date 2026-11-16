import { expect, test } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord, errorMessage } from '../src/api'
import { fanSession, requireRole, skipWithout } from '../src/accounts'
import { check, title } from '../src/check'
import { createMatch, idOf } from '../src/entities'
import { hasRole } from '../src/env'
import {
  PLAYOFF_SKIP,
  assignGroupMatch,
  ensurePoTeams,
  ensurePoTournament,
  ensureWin,
  firstRoundSlots,
  goalEventId,
  groupsOf,
  listTables,
  nextRoundSlot,
  openNamedPlayoff,
  openPlayoff,
  pairList,
  placesForSize,
  playoffDeployed,
  playoffRounds,
  qualifiedTeams,
  replaceTables,
  setPlaces,
  startRefereeMatch,
  tryDrawFinish,
  updateSlot,
  viewPlayoff,
  voidEvent,
} from '../src/playoff'
import { disposeSpec } from '../src/pool'
import { entityName } from '../src/run'
import { Tracker } from '../src/tracker'
import { hasHorizontalOverflow, waitForApp } from '../src/ui'

const bag = new Tracker()
const NAMES = {
  two: entityName('PO-2'),
  four: entityName('PO-4'),
  eight: entityName('PO-8'),
  sixteen: entityName('PO-16'),
  snap: entityName('PO-snap'),
  sizes: entityName('PO-sizes'),
  recalc: entityName('PO-6b-reset'),
  locked: entityName('PO-6b-lock'),
}

test.afterAll(async ({ request }) => {
  await disposeSpec(request, bag, '09-playoff')
})

async function needPlayoff(request: import('@playwright/test').APIRequestContext, needAdmin = true) {
  test.skip(!(await playoffDeployed(request)), PLAYOFF_SKIP)
  if (!needAdmin) return
  const reason = skipWithout('ADMIN')
  test.skip(!!reason, reason || '')
}

function expectRussianError(body: unknown, status: number, extra?: RegExp) {
  expect(status, `ожидали 400 с русским текстом: ${status} ${JSON.stringify(body)}`).toBeGreaterThanOrEqual(400)
  expect(status, `не 401/403: ${status}`).toBeLessThan(500)
  const message = errorMessage(body)
  expect(message, `нужно понятное сообщение, получили: ${JSON.stringify(body)}`).toMatch(/[А-Яа-яЁё]/)
  if (extra) expect(message, message).toMatch(extra)
}

function slotTeamIds(slot: Record<string, unknown>) {
  return [String(slot.homeTeamId || ''), String(slot.awayTeamId || '')]
}

test(title({ id: 'PO1' }, 'перевод в плей-офф: 2/4/8/16 собирают сетку; 3/5/6/12 — ошибка'), check({ id: 'PO1' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 16, bag)

  const valid: { name: string; count: number; groupSize: number; rounds: number }[] = [
    { name: NAMES.two, count: 2, groupSize: 2, rounds: 1 },
    { name: NAMES.four, count: 4, groupSize: 4, rounds: 2 },
    { name: NAMES.eight, count: 8, groupSize: 8, rounds: 3 },
    { name: NAMES.sixteen, count: 16, groupSize: 4, rounds: 4 },
  ]
  for (const item of valid) {
    const ids = teams.slice(0, item.count)
    const opened = await openNamedPlayoff(
      request,
      admin,
      item.name,
      ids,
      groupsOf(ids, item.groupSize),
      placesForSize(item.groupSize),
      bag,
    )
    const rounds = playoffRounds(opened.body)
    expect(asRecord(opened.body).competitionStage, `${item.name}: стадия`).toBe('PLAYOFF')
    expect(rounds.length, `${item.name}: раундов`).toBe(item.rounds)
    expect(firstRoundSlots(opened.body).length, `${item.name}: пар 1-го круга`).toBe(item.count / 2)
    expect(rounds.at(-1)?.title, `${item.name}: финал`).toMatch(/финал/i)
  }

  const sizes = await ensurePoTournament(request, admin, NAMES.sizes, teams.slice(0, 12), [teams.slice(0, 12)], bag)
  expect(sizes.stage, 'E2E PO-sizes не открываем — только негативные размеры').not.toBe('PLAYOFF')
  const dummy = pairList(teams.slice(0, 2))
  for (const count of [3, 5, 6, 12]) {
    const places = await setPlaces(request, admin, sizes.id, placesForSize(count))
    expect(places.status, `места 1–${count}`).toBe(200)
    const opened = await openPlayoff(request, admin, sizes.id, dummy)
    expectRussianError(opened.body, opened.status, /без автоматических пропусков/i)
    expect(errorMessage(opened.body), `в тексте должно быть «${count}»`).toContain(String(count))
    const after = await viewPlayoff(request, sizes.id)
    expect(asRecord(after.body).competitionStage, `после ${count} команд стадия не PLAYOFF`).not.toBe('PLAYOFF')
    expect(playoffRounds(after.body).length, `сетка на ${count} не создаётся`).toBe(0)
  }
})

test(title({ id: 'PO2' }, 'места 1–2 из каждой группы; вышедшие фиксируются в момент перевода'), check({ id: 'PO2' }), async ({ request }) => {
  test.setTimeout(120_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 8, bag)
  const groups = groupsOf(teams, 4)
  const opened = await openNamedPlayoff(request, admin, NAMES.snap, teams, groups, [1, 2], bag)
  const qualified = qualifiedTeams(opened.body)
  expect(qualified.length, 'из двух групп по 1–2 месту → 4 вышедших').toBe(4)
  const byGroup = new Map<string, number[]>()
  for (const row of qualified) {
    const group = String(row.groupName || '')
    byGroup.set(group, [...(byGroup.get(group) || []), Number(row.place)])
  }
  expect(byGroup.size, 'места применяются к каждой группе').toBe(2)
  for (const [group, places] of byGroup) {
    expect(places.sort((a, b) => a - b), `${group}: 1-е и 2-е`).toEqual([1, 2])
  }
  const snapshot = qualified.map((row) => String(row.teamId)).sort()

  const placesAgain = await setPlaces(request, admin, opened.id, [1])
  expectRussianError(placesAgain.body, placesAgain.status, /уже в плей-офф/i)
  const tablesAgain = await replaceTables(request, admin, opened.id, [
    { name: 'Группа A', teamIds: teams.slice(4, 8) },
    { name: 'Группа B', teamIds: teams.slice(0, 4) },
  ])
  expectRussianError(tablesAgain.body, tablesAgain.status, /плей-офф/i)

  const after = await viewPlayoff(request, opened.id)
  const later = qualifiedTeams(after.body).map((row) => String(row.teamId)).sort()
  expect(later, 'изменение таблицы после перевода не меняет список вышедших').toEqual(snapshot)
  const firstRound = firstRoundSlots(after.body).flatMap(slotTeamIds).filter(Boolean).sort()
  expect(firstRound, 'сетка = снимок вышедших').toEqual(snapshot)
})

test(title({ id: 'PO3' }, 'ручные пары: нельзя дубль, чужую и пустую пару'), check({ id: 'PO3' }), async ({ request }) => {
  test.setTimeout(120_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 12, bag)
  const sizes = await ensurePoTournament(request, admin, NAMES.sizes, teams, [teams], bag)
  expect(sizes.stage).not.toBe('PLAYOFF')
  const places = await setPlaces(request, admin, sizes.id, [1, 2, 3, 4])
  expect(places.status).toBe(200)
  const preview = await viewPlayoff(request, sizes.id)
  const qualified = qualifiedTeams(preview.body).map((row) => String(row.teamId || ''))
  expect(qualified.length, 'для ошибок пар нужно 4 вышедших').toBe(4)
  const outsider = teams.find((id) => !qualified.includes(id)) || ''
  expect(outsider, 'нужна команда не из вышедших').toBeTruthy()

  const kickoff = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString()
  const duplicate = await openPlayoff(request, admin, sizes.id, [
    { homeTeamId: qualified[0], awayTeamId: qualified[1] },
    { homeTeamId: qualified[0], awayTeamId: qualified[2] },
  ])
  expectRussianError(duplicate.body, duplicate.status, /один раз/i)

  const foreign = await openPlayoff(request, admin, sizes.id, [
    { homeTeamId: qualified[0], awayTeamId: outsider },
    { homeTeamId: qualified[1], awayTeamId: qualified[2] },
  ])
  expectRussianError(foreign.body, foreign.status, /выбранных мест|не выходят/i)

  const empty = await apiSend(request, 'POST', `/tournaments/${sizes.id}/playoff`, {
    token: admin.accessToken,
    data: {
      pairs: [
        { homeTeamId: qualified[0], awayTeamId: qualified[1], scheduledAt: kickoff, venue: 'E2E зал' },
        { scheduledAt: kickoff, venue: 'E2E зал' },
      ],
    },
  })
  expect(empty.status, `пустая пара → ${empty.status} ${JSON.stringify(empty.body)}`).toBeGreaterThanOrEqual(400)
  expect(empty.status).toBeLessThan(500)
  const emptyText = `${errorMessage(empty.body)} ${JSON.stringify(empty.body)}`
  expect(emptyText, 'пустая пара — понятная ошибка').toMatch(/[А-Яа-яЁё]|must not be null|NotNull|required/i)

  const same = await openPlayoff(request, admin, sizes.id, [
    { homeTeamId: qualified[0], awayTeamId: qualified[0] },
    { homeTeamId: qualified[1], awayTeamId: qualified[2] },
  ])
  expectRussianError(same.body, same.status, /разн/i)

  const after = await viewPlayoff(request, sizes.id)
  expect(asRecord(after.body).competitionStage).not.toBe('PLAYOFF')
  expect(playoffRounds(after.body).length).toBe(0)
})

test(title({ id: 'PO4' }, 'на 8 командах победитель пары 1 играет с победителем пары 2 до финала'), check({ id: 'PO4' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 8, bag)
  const opened = await openNamedPlayoff(request, admin, NAMES.eight, teams, [teams], placesForSize(8), bag)
  const first = firstRoundSlots(opened.body)
  expect(first.length).toBe(4)
  const winners: string[] = []
  for (const slot of first) {
    const matchId = String(slot.matchId || '')
    const home = String(slot.homeTeamId || '')
    expect(matchId && home, `пара ${slot.slotIndex}: матч и хозяева`).toBeTruthy()
    await ensureWin(request, admin, matchId, home)
    winners.push(home)
  }
  const after = await viewPlayoff(request, opened.id)
  const rounds = playoffRounds(after.body)
  expect(rounds.map((round) => round.title).join(',')).toMatch(/1\/4.*1\/2.*финал/i)
  const semis = rounds.find((round) => /1\/2/i.test(round.title))?.slots ?? []
  expect(semis.length).toBe(2)
  expect(slotTeamIds(semis[0]).sort(), 'полуфинал 1 = победители пар 1 и 2').toEqual([winners[0], winners[1]].sort())
  expect(slotTeamIds(semis[1]).sort(), 'полуфинал 2 = победители пар 3 и 4').toEqual([winners[2], winners[3]].sort())

  for (const [index, slot] of semis.entries()) {
    const matchId = String(slot.matchId || '')
    expect(matchId, `полуфинал ${index + 1} должен собраться`).toBeTruthy()
    await ensureWin(request, admin, matchId, winners[index * 2])
  }
  const finalView = await viewPlayoff(request, opened.id)
  const final = playoffRounds(finalView.body).find((round) => /^финал$/i.test(round.title))?.slots?.[0]
  expect(final, 'финал есть').toBeTruthy()
  expect(slotTeamIds(final || {}).sort(), 'финал = победители полуфиналов').toEqual([winners[0], winners[2]].sort())
})

test(title({ id: 'PO5' }, 'после перевода пары нельзя менять; время и место — можно'), check({ id: 'PO5' }), async ({ request }) => {
  test.setTimeout(120_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 2, bag)
  const opened = await openNamedPlayoff(request, admin, NAMES.two, teams, [teams], [1, 2], bag)
  const slot = firstRoundSlots(opened.body)[0]
  expect(slot?.id).toBeTruthy()
  const home = String(slot.homeTeamId || '')
  const away = String(slot.awayTeamId || '')
  const swap = await updateSlot(request, admin, opened.id, String(slot.id), { homeTeamId: away, awayTeamId: home })
  expectRussianError(swap.body, swap.status, /нельзя|время и место/i)
  const locked = await viewPlayoff(request, opened.id)
  const afterLock = firstRoundSlots(locked.body)[0]
  expect(String(afterLock.homeTeamId)).toBe(home)
  expect(String(afterLock.awayTeamId)).toBe(away)

  const when = new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString()
  const moved = await updateSlot(request, admin, opened.id, String(slot.id), {
    scheduledAt: when,
    venue: 'E2E зал PO5',
  })
  expect(moved.status, `время/место: ${moved.status} ${JSON.stringify(moved.body)}`).toBe(200)
  expect(String(asRecord(moved.body).venue || '')).toMatch(/E2E зал PO5/)
})

test(title({ id: 'PO6' }, 'победитель выходит дальше; отмена гола админом пересчитывает или блокирует пару'), check({ id: 'PO6' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 4, bag)
  const opened = await openNamedPlayoff(request, admin, NAMES.four, teams, [teams], placesForSize(4), bag)
  const first = firstRoundSlots(opened.body)
  expect(first.length).toBe(2)
  const slot = first[0]
  const matchId = String(slot.matchId || '')
  const home = String(slot.homeTeamId || '')
  const away = String(slot.awayTeamId || '')
  await ensureWin(request, admin, matchId, home)

  const advanced = await viewPlayoff(request, opened.id)
  const final = playoffRounds(advanced.body).find((round) => /^финал$/i.test(round.title))?.slots?.[0]
  expect(String(final?.homeTeamId || ''), 'победитель пары 1 — хозяин финала').toBe(home)
  expect(String(final?.homeWaitingLabel || '')).toBe('')

  const eventId = await goalEventId(request, matchId)
  expect(eventId, 'в протоколе должен быть гол').toBeTruthy()
  const voided = await voidEvent(request, admin, matchId, eventId, 'admin')
  const recalc = await viewPlayoff(request, opened.id)
  const finalAfter = playoffRounds(recalc.body).find((round) => /^финал$/i.test(round.title))?.slots?.[0]
  if (voided.status >= 400) {
    expectRussianError(voided.body, voided.status, /ничью|уже начался|состав пары/i)
    expect(String(finalAfter?.homeTeamId || '')).toBe(home)
    return
  }
  expect(voided.status, `void админа: ${voided.status} ${JSON.stringify(voided.body)}`).toBeLessThan(400)
  expect(
    String(finalAfter?.homeTeamId || ''),
    'после отмены гола админом финал должен пересчитаться (победитель снят) или API должен вернуть понятную ошибку',
  ).not.toBe(home)
})

test(title({ id: 'PO6b' }, 'отмена гола: слот сбрасывается, если следующий матч не начат; иначе запрет на русском'), check({ id: 'PO6b' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 4, bag)

  const reset = await openNamedPlayoff(request, admin, NAMES.recalc, teams, [teams], placesForSize(4), bag)
  const resetSlot = firstRoundSlots(reset.body)[0]
  const resetMatchId = String(resetSlot.matchId || '')
  const resetHome = String(resetSlot.homeTeamId || '')
  await ensureWin(request, admin, resetMatchId, resetHome)
  const afterWin = await viewPlayoff(request, reset.id)
  const nextOpen = nextRoundSlot(afterWin.body, resetSlot)
  const nextStatus = String(nextOpen?.matchStatus || '')
  expect(['LIVE', 'PAUSED', 'FINISHED'].includes(nextStatus), `следующий матч ещё не начат, статус «${nextStatus}»`).toBeFalsy()
  const alreadyReset = String(nextOpen?.homeTeamId || '') !== resetHome
  if (!alreadyReset) {
    expect(String(nextOpen?.homeTeamId || ''), 'победитель должен выйти в следующий слот').toBe(resetHome)
    const eventId = await goalEventId(request, resetMatchId)
    expect(eventId, 'в протоколе должен быть гол').toBeTruthy()
    const voided = await voidEvent(request, admin, resetMatchId, eventId, 'admin')
    expect(voided.status, `отмена при неначатом следующем: ${voided.status} ${JSON.stringify(voided.body)}`).toBeLessThan(400)
  }
  const afterVoid = await viewPlayoff(request, reset.id)
  const nextCleared = nextRoundSlot(afterVoid.body, resetSlot)
  expect(
    String(nextCleared?.homeTeamId || ''),
    'слот следующего раунда должен сброситься и пересчитаться, пока следующий матч не начат',
  ).not.toBe(resetHome)

  const locked = await openNamedPlayoff(request, admin, NAMES.locked, teams, [teams], placesForSize(4), bag)
  const first = firstRoundSlots(locked.body)
  expect(first.length).toBe(2)
  for (const slot of first) {
    await ensureWin(request, admin, String(slot.matchId || ''), String(slot.homeTeamId || ''))
  }
  const assembled = await viewPlayoff(request, locked.id)
  const final = nextRoundSlot(assembled.body, first[0])
  const finalMatchId = String(final?.matchId || '')
  expect(finalMatchId, 'финал должен собраться').toBeTruthy()
  const started = await startRefereeMatch(request, admin.accessToken, finalMatchId)
  expect(
    ['LIVE', 'PAUSED'].includes(String(asRecord(started.body).status || '')) || started.status < 400,
    `старт финала: ${started.status} ${JSON.stringify(started.body)}`,
  ).toBeTruthy()

  const lockedGoal = await goalEventId(request, String(first[0].matchId || ''))
  expect(lockedGoal, 'гол полуфинала для запрета').toBeTruthy()
  const blocked = await voidEvent(request, admin, String(first[0].matchId || ''), lockedGoal, 'admin')
  expectRussianError(blocked.body, blocked.status, /начался|нельзя|запрещ|состав пары/i)
  const still = await viewPlayoff(request, locked.id)
  const finalAfter = nextRoundSlot(still.body, first[0])
  expect(String(finalAfter?.homeTeamId || ''), 'при начатом следующем матче слот не должен тихо поехать').toBe(String(first[0].homeTeamId || ''))
})

test(title({ id: 'PO7' }, 'ничья: если следующая пара собрана — нельзя; иначе фиксируем поведение'), check({ id: 'PO7' }), async ({ request }) => {
  test.setTimeout(180_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const fourTeams = await ensurePoTeams(request, admin, 4, bag)
  const four = await openNamedPlayoff(request, admin, NAMES.four, fourTeams, [fourTeams], placesForSize(4), bag)
  const fourSlots = firstRoundSlots((await viewPlayoff(request, four.id)).body)
  const pending = fourSlots.find((slot) => String(slot.matchStatus || '') === 'SCHEDULED')
  if (pending?.matchId) {
    const draw = await tryDrawFinish(request, admin, String(pending.matchId))
    const after = await viewPlayoff(request, four.id)
    const note = `ничья при пустой следующей паре: HTTP ${draw.status} ${errorMessage(draw.body) || JSON.stringify(draw.body)}`
    expect(asRecord(draw.body).status || draw.status, note).toBeTruthy()
    if (draw.status < 400) {
      expect(asRecord(draw.body).status, `${note}; счёт ${asRecord(draw.body).homeScore}:${asRecord(draw.body).awayScore}`).toBe('FINISHED')
      const final = playoffRounds(after.body).find((round) => /^финал$/i.test(round.title))?.slots?.[0]
      const side = Number(pending.slotIndex) % 2 === 0 ? final?.homeTeamId : final?.awayTeamId
      expect(side, 'при ничьей и несобранной следующей паре победитель не ставится').toBeFalsy()
    } else {
      expectRussianError(draw.body, draw.status)
    }
  }

  const eightTeams = await ensurePoTeams(request, admin, 8, bag)
  const eight = await openNamedPlayoff(request, admin, NAMES.eight, eightTeams, [eightTeams], placesForSize(8), bag)
  const qf = firstRoundSlots((await viewPlayoff(request, eight.id)).body)
  for (const slot of qf.slice(0, 2)) {
    await ensureWin(request, admin, String(slot.matchId || ''), String(slot.homeTeamId || ''))
  }
  const assembled = await viewPlayoff(request, eight.id)
  const semi = playoffRounds(assembled.body).find((round) => /1\/2/i.test(round.title))?.slots?.[0]
  expect(semi?.matchId, 'после двух четвертьфиналов полуфинал должен собраться').toBeTruthy()
  const source = qf[0]
  const eventId = await goalEventId(request, String(source.matchId || ''))
  expect(eventId).toBeTruthy()
  const blocked = await voidEvent(request, admin, String(source.matchId || ''), eventId, 'referee')
  expectRussianError(blocked.body, blocked.status, /ничью|уже начался|состав пары/i)
})

test(title({ id: 'PO8' }, 'прикрепление сыгранного матча группы: счёт и составы не меняются'), check({ id: 'PO8' }), async ({ request }) => {
  test.setTimeout(120_000)
  await needPlayoff(request)
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 12, bag)
  const sizes = await ensurePoTournament(request, admin, NAMES.sizes, teams, [teams], bag)
  expect(sizes.stage).not.toBe('PLAYOFF')
  const tables = asList((await listTables(request, sizes.id)).body)
  const tableId = String(tables[0]?.id || '')
  expect(tableId, 'у E2E PO-sizes должна быть группа').toBeTruthy()

  const created = await createMatch(request, admin, {
    tournamentId: sizes.id,
    homeTeamId: teams[0],
    awayTeamId: teams[1],
    track: bag,
  })
  expect(created.status, `матч группы: ${created.status} ${JSON.stringify(created.body)}`).toBe(201)
  const matchId = idOf(created.body)
  await ensureWin(request, admin, matchId, teams[0])
  const before = asRecord((await apiGet(request, `/matches/${matchId}`)).body)
  expect(Number(before.homeScore)).toBe(1)
  expect(Number(before.awayScore)).toBe(0)

  const attached = await assignGroupMatch(request, admin, sizes.id, matchId, tableId)
  expect(attached.status, `прикрепить: ${attached.status} ${JSON.stringify(attached.body)}`).toBe(200)
  const after = asRecord((await apiGet(request, `/matches/${matchId}`)).body)
  expect(String(after.homeTeamId)).toBe(String(before.homeTeamId))
  expect(String(after.awayTeamId)).toBe(String(before.awayTeamId))
  expect(Number(after.homeScore)).toBe(Number(before.homeScore))
  expect(Number(after.awayScore)).toBe(Number(before.awayScore))
  expect(String(after.status)).toBe('FINISHED')
  expect(String(after.tableId || ''), 'матч должен попасть в группу').toBe(tableId)
})

test(title({ id: 'PO9' }, 'управление сеткой: 401 аноним, 403 не-админ, 200 админ'), check({ id: 'PO9' }), async ({ request }) => {
  test.setTimeout(120_000)
  await needPlayoff(request, false)
  const sample = asList((await apiGet(request, '/tournaments?size=1')).body)[0]
  const tournamentId = String(sample?.id || '00000000-0000-0000-0000-000000000001')
  const matchId = '00000000-0000-0000-0000-000000000002'
  const slotId = '00000000-0000-0000-0000-000000000003'
  const tableId = '00000000-0000-0000-0000-000000000004'
  const kickoff = new Date(Date.now() + 86_400_000).toISOString()
  const pair = {
    homeTeamId: '00000000-0000-0000-0000-000000000011',
    awayTeamId: '00000000-0000-0000-0000-000000000012',
    scheduledAt: kickoff,
    venue: 'E2E',
  }
  const writes: { method: 'POST' | 'PUT'; path: string; data: unknown }[] = [
    { method: 'PUT', path: `/tournaments/${tournamentId}/playoff/places`, data: { places: [1, 2] } },
    { method: 'PUT', path: `/tournaments/${tournamentId}/matches/${matchId}/group`, data: { tableId } },
    { method: 'POST', path: `/tournaments/${tournamentId}/playoff`, data: { pairs: [pair] } },
    { method: 'PUT', path: `/tournaments/${tournamentId}/playoff/slots/${slotId}`, data: { venue: 'E2E' } },
  ]

  for (const item of writes) {
    const anon = await apiSend(request, item.method, item.path, { data: item.data })
    expect(anon.status, `аноним ${item.method} ${item.path} → ${anon.status}`).toBe(401)
  }

  const roles: Array<'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE'> = ['FAN', 'PLAYER', 'CAPTAIN', 'REFEREE']
  for (const role of roles) {
    if (role !== 'FAN' && !hasRole(role)) continue
    const session = role === 'FAN' ? await fanSession(request) : await requireRole(request, role)
    for (const item of writes) {
      const call = await apiSend(request, item.method, item.path, { token: session.accessToken, data: item.data })
      expect(call.status, `${role} ${item.method} ${item.path} → ${call.status}`).toBe(403)
    }
  }

  const adminReason = skipWithout('ADMIN')
  test.skip(!!adminReason, adminReason || '')
  const admin = await requireRole(request, 'ADMIN')
  const teams = await ensurePoTeams(request, admin, 12, bag)
  const sizes = await ensurePoTournament(request, admin, NAMES.sizes, teams, [teams], bag)
  const two = await openNamedPlayoff(request, admin, NAMES.two, teams.slice(0, 2), [teams.slice(0, 2)], [1, 2], bag)
  const slot = firstRoundSlots(two.body)[0]
  const tables = asList((await listTables(request, sizes.id)).body)
  const created = await createMatch(request, admin, {
    tournamentId: sizes.id,
    homeTeamId: teams[2],
    awayTeamId: teams[3],
    track: bag,
  })

  const places = await setPlaces(request, admin, sizes.id, [1, 2])
  expect(places.status, `ADMIN places → ${places.status}`).toBe(200)
  if (created.status === 201 && tables[0]?.id) {
    const attach = await assignGroupMatch(request, admin, sizes.id, idOf(created.body), String(tables[0].id))
    expect(attach.status, `ADMIN attach → ${attach.status}`).toBe(200)
  }
  const reopen = await openPlayoff(request, admin, two.id, pairList(teams.slice(0, 2)))
  expect(reopen.status, `ADMIN POST /playoff на уже открытом → ${reopen.status}`).toBeGreaterThanOrEqual(400)
  expect(reopen.status, 'админ не должен получать 401/403').toBeLessThan(500)
  expect(reopen.status).not.toBe(401)
  expect(reopen.status).not.toBe(403)
  expect(errorMessage(reopen.body)).toMatch(/[А-Яа-яЁё]/)
  const venue = await updateSlot(request, admin, two.id, String(slot.id), { venue: 'E2E зал PO9' })
  expect(venue.status, `ADMIN slot → ${venue.status}`).toBe(200)
})

test(title({ id: 'PO10' }, 'публичная сетка: десктоп и 375px, имена видны, TBD понятен'), check({ id: 'PO10' }), async ({ page, request }) => {
  test.setTimeout(120_000)
  await needPlayoff(request, false)
  let tournamentId = ''
  const listed = asList((await apiGet(request, '/tournaments?size=50')).body)
  for (const row of listed) {
    const view = await viewPlayoff(request, String(row.id))
    if (playoffRounds(view.body).length) {
      tournamentId = String(row.id)
      break
    }
  }
  if (!tournamentId && hasRole('ADMIN')) {
    const admin = await requireRole(request, 'ADMIN')
    const teams = await ensurePoTeams(request, admin, 4, bag)
    const opened = await openNamedPlayoff(request, admin, NAMES.four, teams, [teams], placesForSize(4), bag)
    tournamentId = opened.id
  }
  test.skip(!tournamentId, 'на стенде нет открытой сетки плей-офф')

  const view = await viewPlayoff(request, tournamentId)
  const expectedNames = firstRoundSlots(view.body)
    .flatMap((slot) => [String(slot.homeTeamName || ''), String(slot.awayTeamName || '')])
    .filter(Boolean)
  const waiting = playoffRounds(view.body).some((round) =>
    round.slots.some((slot) => !slot.homeTeamId || !slot.awayTeamId),
  )

  for (const viewport of [
    { width: 1280, height: 720, label: 'десктоп' },
    { width: 375, height: 812, label: '375px' },
  ]) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await page.goto(`/tournaments/${tournamentId}`)
    await waitForApp(page)
    await expect(page.getByRole('heading', { name: /сетка плей-офф/i })).toBeVisible()
    const body = await page.locator('main').innerText()
    for (const name of expectedNames) {
      expect(body, `${viewport.label}: имя «${name}» не должно пропадать`).toContain(name)
    }
    if (waiting) {
      expect(body, `${viewport.label}: TBD-слот должен быть понятен`).toMatch(/ожидает/i)
    }
    const unknown = playoffRounds(view.body).flatMap((round) => round.slots).filter((slot) => !slot.matchId)
    if (unknown.length) {
      const cards = page.locator('article.slot, .slot, [class*="slot"]')
      expect(await cards.count(), `${viewport.label}: пустые карточки для неизвестных матчей`).toBeGreaterThan(0)
    }
    const names = page.locator('.slot b, article.slot b, [class*="slot"] b')
    const count = await names.count()
    for (let i = 0; i < count; i++) {
      const clipped = await names.nth(i).evaluate((el) => (el as HTMLElement).scrollWidth > (el as HTMLElement).clientWidth + 1)
      expect(clipped, `${viewport.label}: «${await names.nth(i).innerText()}» обрезается ellipsis`).toBeFalsy()
    }
    expect(await hasHorizontalOverflow(page), `${viewport.label}: страница не должна скроллиться горизонтально`).toBeFalsy()
  }
})

test(title({ id: 'PO11' }, 'матч за 3-е место — уточнить у Костика'), check({ id: 'PO11' }), async () => {
  test.skip(true, 'матч за 3-е место в PR #56 не описан и не реализован — уточнить у Костика')
})
