import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from './api'
import { requireRole, type Session } from './accounts'
import { disposeBag, printReport } from './cleanup'
import {
  applyTeam,
  approveTeam,
  assignReferee,
  createMatch,
  createTeam,
  createTournament,
  idOf,
  updateTournament,
} from './entities'
import { entityName, entityShort } from './run'
import { Tracker } from './tracker'

export type LivePool = {
  tournamentId: string
  homeId: string
  awayId: string
  applyTeamId: string
  homeName: string
  applyName: string
  bag: Tracker
}

export async function listPages(
  request: APIRequestContext,
  path: string,
  token?: string,
): Promise<Record<string, unknown>[]> {
  const rows: Record<string, unknown>[] = []
  for (let page = 0; page < 40; page++) {
    const sep = path.includes('?') ? '&' : '?'
    const call = await apiGet(request, `${path}${sep}size=50&page=${page}`, token)
    const batch = asList(call.body)
    rows.push(...batch)
    if (batch.length < 50) break
  }
  return rows
}

export async function findNamed(
  rows: Record<string, unknown>[],
  name: string,
): Promise<Record<string, unknown> | undefined> {
  return rows.find((row) => String(row.name || '') === name)
}

export async function ensureApproved(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  teamId: string,
): Promise<void> {
  const entries = asList((await apiGet(request, `/tournaments/${tournamentId}/teams`)).body)
  const row = entries.find((item) => String(item.teamId || item.id) === teamId)
  const status = String(row?.status || '')
  if (status === 'APPROVED') return
  if (!row) {
    const applied = await applyTeam(request, admin, tournamentId, teamId)
    if (applied.status !== 201 && applied.status !== 409) {
      throw new Error(`заявка команды: ${applied.status} ${JSON.stringify(applied.body)}`)
    }
  }
  const approved = await approveTeam(request, admin, tournamentId, teamId)
  if (approved.status >= 400 && asRecord(approved.body).status !== 'APPROVED') {
    const again = asList((await apiGet(request, `/tournaments/${tournamentId}/teams`)).body)
    const ok = again.some((item) => String(item.teamId || item.id) === teamId && item.status === 'APPROVED')
    if (!ok) throw new Error(`допуск команды: ${approved.status} ${JSON.stringify(approved.body)}`)
  }
}

export async function createLivePool(
  request: APIRequestContext,
  admin: Session,
  bag = new Tracker(),
  label = 'Пул',
): Promise<LivePool> {
  const homeName = entityName(`${label} хозяева`)
  const awayName = entityName(`${label} гости`)
  const applyName = entityName(`${label} заявка`)
  const tournament = await createTournament(request, admin, {
    name: entityName(label),
    status: 'REGISTRATION',
    track: bag,
  })
  if (tournament.status !== 201) {
    throw new Error(`турнир «${label}»: ${tournament.status} ${JSON.stringify(tournament.body)}`)
  }
  const tournamentId = idOf(tournament.body)
  const home = await createTeam(request, admin, homeName, entityShort('H'), bag)
  const away = await createTeam(request, admin, awayName, entityShort('A'), bag)
  const apply = await createTeam(request, admin, applyName, entityShort('Z'), bag)
  if (home.status !== 201 || away.status !== 201 || apply.status !== 201) {
    throw new Error(`команды пула: ${home.status}/${away.status}/${apply.status}`)
  }
  const homeId = idOf(home.body)
  const awayId = idOf(away.body)
  const applyTeamId = idOf(apply.body)
  await ensureApproved(request, admin, tournamentId, homeId)
  await ensureApproved(request, admin, tournamentId, awayId)
  return { tournamentId, homeId, awayId, applyTeamId, homeName, applyName, bag }
}

export async function activateIfNeeded(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
): Promise<void> {
  const current = await apiGet(request, `/tournaments/${tournamentId}`)
  const status = String(asRecord(current.body).status || '')
  if (status === 'ACTIVE') return
  await updateTournament(request, admin, tournamentId, { status: 'ACTIVE' })
}

export async function prepareAssignedMatch(
  request: APIRequestContext,
  bag: Tracker,
  options: { activate?: boolean; pool?: LivePool } = {},
) {
  const admin = await requireRole(request, 'ADMIN')
  const referee = await requireRole(request, 'REFEREE')
  const pool = options.pool || await createLivePool(request, admin, bag, 'live')
  if (options.activate) {
    await activateIfNeeded(request, admin, pool.tournamentId)
  }
  const match = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
    track: bag,
  })
  if (match.status !== 201) {
    throw new Error(`матч пула: ${match.status} ${JSON.stringify(match.body)}`)
  }
  const matchId = idOf(match.body)
  const assigned = await assignReferee(request, admin, matchId, String(referee.user.id))
  if (assigned.status !== 201 && assigned.status !== 409) {
    throw new Error(`назначение судьи: ${assigned.status} ${JSON.stringify(assigned.body)}`)
  }
  return {
    admin,
    referee,
    matchId,
    homeId: pool.homeId,
    awayId: pool.awayId,
    tournamentId: pool.tournamentId,
    applyTeamId: pool.applyTeamId,
    homeName: pool.homeName,
    applyName: pool.applyName,
    bag,
  }
}

export async function deleteMatch(request: APIRequestContext, admin: Session, matchId: string) {
  await apiSend(request, 'DELETE', `/matches/${matchId}`, { token: admin.accessToken })
}

export async function excludeTeamFromTournament(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  teamId: string,
) {
  return apiSend(request, 'DELETE', `/tournaments/${tournamentId}/teams/${teamId}`, {
    token: admin.accessToken,
  })
}

export async function disposeSpec(request: APIRequestContext, bag: Tracker, label = 'afterAll') {
  const report = await disposeBag(request, bag)
  printReport(report, label)
}

export function standingRows(body: unknown): Record<string, unknown>[] {
  if (Array.isArray(body)) {
    const first = body[0]
    if (first && typeof first === 'object' && 'rows' in first) {
      return (body as { rows?: Record<string, unknown>[] }[]).flatMap((group) => group.rows ?? [])
    }
    return body as Record<string, unknown>[]
  }
  const record = asRecord(body)
  if (Array.isArray(record.tables)) {
    return (record.tables as { rows?: Record<string, unknown>[] }[]).flatMap((group) => group.rows ?? [])
  }
  if (Array.isArray(record.rows)) return record.rows as Record<string, unknown>[]
  return []
}
