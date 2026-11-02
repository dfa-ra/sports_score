import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord, errorMessage } from './api'
import type { Session } from './accounts'
import { applyTeam, approveTeam, createTeam, createTournament, idOf } from './entities'
import { findNamed, listPages } from './pool'

export const PLAYOFF_SKIP = 'плей-офф не задеплоен на dev (PR #56)'
export const PENALTIES_SKIP = 'серия пенальти не задеплоена на dev'

function callText(body: unknown): string {
  return `${errorMessage(body)} ${typeof body === 'string' ? body : JSON.stringify(body)}`
}

/** Unmapped API path on the stand: «Адрес не найден» (main) or «Файл не найден» (older builds). */
export function missingRoute(body: unknown): boolean {
  const text = callText(body)
  return /адрес не найден|файл не найден/i.test(text) || /<!doctype html/i.test(text)
}

export async function playoffDeployed(request: APIRequestContext): Promise<boolean> {
  const list = await apiGet(request, '/tournaments?size=1')
  const sample = asList(list.body)[0]
  const id = String(sample?.id || '00000000-0000-0000-0000-000000000001')
  const call = await apiGet(request, `/tournaments/${id}/playoff`)
  if (missingRoute(call.body)) return false
  if (call.status === 200 || call.status === 400 || call.status === 401 || call.status === 403) return true
  if (call.status === 404 && /not_found|не найден|tournament/i.test(callText(call.body))) return true
  return false
}

export async function viewPlayoff(request: APIRequestContext, tournamentId: string, token?: string) {
  return apiGet(request, `/tournaments/${tournamentId}/playoff`, token)
}

export async function setPlaces(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  places: number[],
) {
  return apiSend(request, 'PUT', `/tournaments/${tournamentId}/playoff/places`, {
    token: admin.accessToken,
    data: { places },
  })
}

export async function openPlayoff(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  pairs: { homeTeamId: string; awayTeamId: string; venue?: string }[],
) {
  const kickoff = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString()
  return apiSend(request, 'POST', `/tournaments/${tournamentId}/playoff`, {
    token: admin.accessToken,
    data: {
      pairs: pairs.map((pair) => ({
        homeTeamId: pair.homeTeamId,
        awayTeamId: pair.awayTeamId,
        scheduledAt: kickoff,
        venue: pair.venue || 'E2E зал',
      })),
    },
  })
}

export async function replaceTables(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  tables: { name: string; teamIds: string[] }[],
) {
  return apiSend(request, 'PUT', `/tournaments/${tournamentId}/tables`, {
    token: admin.accessToken,
    data: { tables },
  })
}

export async function listTables(request: APIRequestContext, tournamentId: string, token?: string) {
  return apiGet(request, `/tournaments/${tournamentId}/tables`, token)
}

export async function assignGroupMatch(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  matchId: string,
  tableId: string,
) {
  return apiSend(request, 'PUT', `/tournaments/${tournamentId}/matches/${matchId}/group`, {
    token: admin.accessToken,
    data: { tableId },
  })
}

export async function updateSlot(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  slotId: string,
  data: { scheduledAt?: string; venue?: string; homeTeamId?: string; awayTeamId?: string },
) {
  return apiSend(request, 'PUT', `/tournaments/${tournamentId}/playoff/slots/${slotId}`, {
    token: admin.accessToken,
    data,
  })
}

export async function ensurePoTeam(
  request: APIRequestContext,
  admin: Session,
  index: number,
): Promise<string> {
  const name = `E2E PO ${String(index).padStart(2, '0')}`
  const existing = await findNamed(await listPages(request, '/teams'), name)
  if (existing?.id) return String(existing.id)
  const created = await createTeam(request, admin, name, `P${String(index).padStart(2, '0')}`)
  if (created.status === 201) return idOf(created.body)
  const again = await findNamed(await listPages(request, '/teams'), name)
  if (again?.id) return String(again.id)
  throw new Error(`команда «${name}»: ${created.status} ${JSON.stringify(created.body)}`)
}

export async function ensurePoTeams(
  request: APIRequestContext,
  admin: Session,
  count: number,
): Promise<string[]> {
  const ids: string[] = []
  for (let i = 1; i <= count; i++) ids.push(await ensurePoTeam(request, admin, i))
  return ids
}

export async function ensurePoTournament(
  request: APIRequestContext,
  admin: Session,
  name: string,
  teamIds: string[],
  groups: string[][],
): Promise<{ id: string; stage: string }> {
  const existing = await findNamed(await listPages(request, '/tournaments'), name)
  const id = existing?.id
    ? String(existing.id)
    : await createPoTournament(request, admin, name)
  const view = await viewPlayoff(request, id)
  const stage = String(asRecord(view.body).competitionStage || existing?.status || '')
  if (stage === 'PLAYOFF') return { id, stage }
  for (const teamId of teamIds) {
    await applyTeam(request, admin, id, teamId)
    await approveTeam(request, admin, id, teamId)
  }
  const tables = groups.map((ids, index) => ({
    name: `Группа ${String.fromCharCode(65 + index)}`,
    teamIds: ids,
  }))
  const grouped = await replaceTables(request, admin, id, tables)
  if (grouped.status >= 400) {
    throw new Error(`группы «${name}»: ${grouped.status} ${JSON.stringify(grouped.body)}`)
  }
  return { id, stage: stage || 'GROUP' }
}

async function createPoTournament(
  request: APIRequestContext,
  admin: Session,
  name: string,
): Promise<string> {
  const created = await createTournament(request, admin, {
    name,
    status: 'REGISTRATION',
    format: 'GROUPS_PLAYOFF',
  })
  if (created.status === 201) return idOf(created.body)
  const again = await findNamed(await listPages(request, '/tournaments'), name)
  if (again?.id) return String(again.id)
  throw new Error(`турнир «${name}»: ${created.status} ${JSON.stringify(created.body)}`)
}

export function pairList(teamIds: string[]): { homeTeamId: string; awayTeamId: string; venue: string }[] {
  const pairs: { homeTeamId: string; awayTeamId: string; venue: string }[] = []
  for (let i = 0; i < teamIds.length; i += 2) {
    pairs.push({
      homeTeamId: teamIds[i],
      awayTeamId: teamIds[i + 1],
      venue: `E2E зал ${i / 2 + 1}`,
    })
  }
  return pairs
}

export function playoffRounds(body: unknown): {
  round: number
  title: string
  slots: Record<string, unknown>[]
}[] {
  const rounds = asRecord(body).rounds
  return Array.isArray(rounds) ? rounds as { round: number; title: string; slots: Record<string, unknown>[] }[] : []
}

export function qualifiedTeams(body: unknown): Record<string, unknown>[] {
  const rows = asRecord(body).qualified
  return Array.isArray(rows) ? rows as Record<string, unknown>[] : []
}

export function firstRoundSlots(body: unknown): Record<string, unknown>[] {
  const first = playoffRounds(body).find((round) => Number(round.round) === 1) || playoffRounds(body)[0]
  return first?.slots ?? []
}

export function nextRoundSlot(body: unknown, source: Record<string, unknown>): Record<string, unknown> | undefined {
  const nextRound = Number(source.round || 1) + 1
  const nextIndex = Math.floor(Number(source.slotIndex || 0) / 2)
  const round = playoffRounds(body).find((item) => Number(item.round) === nextRound)
  return round?.slots.find((slot) => Number(slot.slotIndex) === nextIndex) || round?.slots[0]
}

export function routeExists(call: { status: number; body: unknown }): boolean {
  if (missingRoute(call.body)) return false
  if ([200, 201, 204, 400, 401, 403, 404, 409, 422].includes(call.status)) return true
  return false
}

export async function penaltiesDeployed(request: APIRequestContext): Promise<boolean> {
  if (!(await playoffDeployed(request))) return false
  const sample = asList((await apiGet(request, '/matches?size=1')).body)[0]
  const id = String(sample?.id || '00000000-0000-0000-0000-000000000001')
  const match = await apiGet(request, `/matches/${id}`)
  const blob = `${JSON.stringify(asRecord(match.body))} ${Object.keys(asRecord(match.body)).join(' ')}`
  if (/penalt|shootout|пен\./i.test(blob)) return true
  for (const path of [
    `/matches/${id}/penalties`,
    `/matches/${id}/shootout`,
    `/matches/${id}/penalty-kicks`,
  ]) {
    if (routeExists(await apiGet(request, path))) return true
  }
  return false
}

export function placesForSize(teamCount: number): number[] {
  return Array.from({ length: teamCount }, (_, index) => index + 1)
}

export function groupsOf(teamIds: string[], groupSize: number): string[][] {
  const groups: string[][] = []
  for (let i = 0; i < teamIds.length; i += groupSize) {
    groups.push(teamIds.slice(i, i + groupSize))
  }
  return groups
}

export async function openNamedPlayoff(
  request: APIRequestContext,
  admin: Session,
  name: string,
  teamIds: string[],
  groups: string[][],
  places: number[],
): Promise<{ id: string; body: Record<string, unknown>; openedNow: boolean }> {
  const tournament = await ensurePoTournament(request, admin, name, teamIds, groups)
  const current = await viewPlayoff(request, tournament.id)
  if (String(asRecord(current.body).competitionStage || '') === 'PLAYOFF'
    && playoffRounds(current.body).length > 0) {
    return { id: tournament.id, body: asRecord(current.body), openedNow: false }
  }
  const saved = await setPlaces(request, admin, tournament.id, places)
  if (saved.status >= 400) {
    throw new Error(`места «${name}»: ${saved.status} ${JSON.stringify(saved.body)}`)
  }
  const preview = await viewPlayoff(request, tournament.id)
  const qualified = qualifiedTeams(preview.body).map((row) => String(row.teamId || ''))
  const opened = await openPlayoff(request, admin, tournament.id, pairList(qualified))
  if (opened.status >= 400) {
    throw new Error(`открытие «${name}»: ${opened.status} ${JSON.stringify(opened.body)}`)
  }
  return { id: tournament.id, body: asRecord(opened.body), openedNow: true }
}

export async function playWin(
  request: APIRequestContext,
  admin: Session,
  matchId: string,
  winnerTeamId: string,
): Promise<void> {
  const current = asRecord((await apiGet(request, `/matches/${matchId}`)).body)
  const status = String(current.status || '')
  if (status === 'FINISHED') return
  if (status === 'SCHEDULED') {
    const start = await apiSend(request, 'POST', `/referee/matches/${matchId}/start`, { token: admin.accessToken })
    if (start.status >= 400 && asRecord(start.body).status !== 'LIVE') {
      throw new Error(`старт ${matchId}: ${start.status} ${JSON.stringify(start.body)}`)
    }
  }
  const goal = await apiSend(request, 'POST', `/referee/matches/${matchId}/events`, {
    token: admin.accessToken,
    data: { eventType: 'GOAL', teamId: winnerTeamId },
  })
  if (goal.status !== 201) {
    throw new Error(`гол ${matchId}: ${goal.status} ${JSON.stringify(goal.body)}`)
  }
  const finish = await apiSend(request, 'POST', `/referee/matches/${matchId}/finish`, { token: admin.accessToken })
  if (asRecord(finish.body).status !== 'FINISHED') {
    throw new Error(`финиш ${matchId}: ${finish.status} ${JSON.stringify(finish.body)}`)
  }
}

export async function startRefereeMatch(
  request: APIRequestContext,
  token: string,
  matchId: string,
): Promise<{ status: number; body: unknown }> {
  const current = await apiGet(request, `/matches/${matchId}`)
  const status = String(asRecord(current.body).status || '')
  if (status === 'LIVE' || status === 'PAUSED') return current
  return apiSend(request, 'POST', `/referee/matches/${matchId}/start`, { token })
}

export async function addRegularGoal(
  request: APIRequestContext,
  token: string,
  matchId: string,
  teamId: string,
) {
  return apiSend(request, 'POST', `/referee/matches/${matchId}/events`, {
    token,
    data: { eventType: 'GOAL', teamId },
  })
}

/** Regular-time 1:1, without a series. Used for PO16 «1:1 (пен. 4:3)». */
export async function scoreRegularDraw(
  request: APIRequestContext,
  token: string,
  matchId: string,
  homeTeamId: string,
  awayTeamId: string,
): Promise<void> {
  await startRefereeMatch(request, token, matchId)
  const current = asRecord((await apiGet(request, `/matches/${matchId}`)).body)
  if (Number(current.homeScore || 0) < 1) {
    const goal = await addRegularGoal(request, token, matchId, homeTeamId)
    if (goal.status !== 201) {
      throw new Error(`гол хозяев ${matchId}: ${goal.status} ${JSON.stringify(goal.body)}`)
    }
  }
  const afterHome = asRecord((await apiGet(request, `/matches/${matchId}`)).body)
  if (Number(afterHome.awayScore || 0) < 1) {
    const goal = await addRegularGoal(request, token, matchId, awayTeamId)
    if (goal.status !== 201) {
      throw new Error(`гол гостей ${matchId}: ${goal.status} ${JSON.stringify(goal.body)}`)
    }
  }
}

export async function tryDrawFinish(
  request: APIRequestContext,
  admin: Session,
  matchId: string,
) {
  const current = await apiGet(request, `/matches/${matchId}`)
  const status = String(asRecord(current.body).status || '')
  if (status === 'FINISHED') {
    return current
  }
  if (status === 'SCHEDULED') {
    const start = await apiSend(request, 'POST', `/referee/matches/${matchId}/start`, { token: admin.accessToken })
    if (start.status >= 500) return start
  }
  return apiSend(request, 'POST', `/referee/matches/${matchId}/finish`, { token: admin.accessToken })
}

export async function matchEvents(request: APIRequestContext, matchId: string) {
  return apiGet(request, `/matches/${matchId}/events`)
}

export async function voidEvent(
  request: APIRequestContext,
  admin: Session,
  matchId: string,
  eventId: string,
  via: 'admin' | 'referee' = 'admin',
) {
  const path = via === 'admin'
    ? `/admin/matches/${matchId}/events/${eventId}/void`
    : `/referee/matches/${matchId}/events/${eventId}/void`
  return apiSend(request, 'POST', path, { token: admin.accessToken })
}

export async function goalEventId(request: APIRequestContext, matchId: string): Promise<string> {
  const events = asList((await matchEvents(request, matchId)).body)
  const goal = events.find((row) => String(row.eventType || '') === 'GOAL' && row.voided !== true)
  return String(goal?.id || '')
}

export async function ensureWin(
  request: APIRequestContext,
  admin: Session,
  matchId: string,
  winnerTeamId: string,
): Promise<Record<string, unknown>> {
  const current = await apiGet(request, `/matches/${matchId}`)
  const body = asRecord(current.body)
  if (String(body.status || '') === 'FINISHED') return body
  await playWin(request, admin, matchId, winnerTeamId)
  return asRecord((await apiGet(request, `/matches/${matchId}`)).body)
}
