import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from './api'
import { requireRole, type Session } from './accounts'
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

/** Постоянные сущности на dev. Капитанская «E2E Команда» — чужая, её не трогаем. */
export const POOL = {
  tournament: 'E2E Пул',
  home: 'E2E Хозяева',
  away: 'E2E Гости',
  apply: 'E2E Заявка',
  captain: 'E2E Команда',
} as const

export type LivePool = {
  tournamentId: string
  homeId: string
  awayId: string
  applyTeamId: string
}

let cached: LivePool | undefined

export async function listPages(
  request: APIRequestContext,
  path: string,
  token?: string,
): Promise<Record<string, unknown>[]> {
  const rows: Record<string, unknown>[] = []
  for (let page = 0; page < 6; page++) {
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

export async function ensureTeam(
  request: APIRequestContext,
  admin: Session,
  name: string,
  shortName: string,
): Promise<string> {
  const existing = await findNamed(await listPages(request, '/teams'), name)
  if (existing?.id) return String(existing.id)
  const created = await createTeam(request, admin, name, shortName)
  if (created.status === 201) return idOf(created.body)
  const again = await findNamed(await listPages(request, '/teams'), name)
  if (again?.id) return String(again.id)
  throw new Error(`не удалось получить команду «${name}»: ${created.status} ${JSON.stringify(created.body)}`)
}

export async function ensureTournament(
  request: APIRequestContext,
  admin: Session,
  name: string,
): Promise<string> {
  const existing = await findNamed(await listPages(request, '/tournaments'), name)
  if (existing?.id) {
    const status = String(existing.status || '')
    if (status !== 'REGISTRATION' && status !== 'DRAFT') {
      const updated = await updateTournament(request, admin, String(existing.id), { status: 'REGISTRATION' })
      if (updated.status >= 400) {
        throw new Error(`не перевести «${name}» в REGISTRATION: ${updated.status} ${JSON.stringify(updated.body)}`)
      }
    }
    return String(existing.id)
  }
  const created = await createTournament(request, admin, { name, status: 'REGISTRATION' })
  if (created.status === 201) return idOf(created.body)
  const again = await findNamed(await listPages(request, '/tournaments'), name)
  if (again?.id) return String(again.id)
  throw new Error(`не удалось получить турнир «${name}»: ${created.status} ${JSON.stringify(created.body)}`)
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

export async function ensureLivePool(request: APIRequestContext, admin: Session): Promise<LivePool> {
  if (cached) return cached
  const tournamentId = await ensureTournament(request, admin, POOL.tournament)
  const homeId = await ensureTeam(request, admin, POOL.home, 'E2H')
  const awayId = await ensureTeam(request, admin, POOL.away, 'E2A')
  const applyTeamId = await ensureTeam(request, admin, POOL.apply, 'E2Z')
  await ensureApproved(request, admin, tournamentId, homeId)
  await ensureApproved(request, admin, tournamentId, awayId)
  cached = { tournamentId, homeId, awayId, applyTeamId }
  return cached
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
  options: { activate?: boolean } = {},
) {
  const admin = await requireRole(request, 'ADMIN')
  const referee = await requireRole(request, 'REFEREE')
  const pool = await ensureLivePool(request, admin)
  if (options.activate) {
    await activateIfNeeded(request, admin, pool.tournamentId)
  }
  const match = await createMatch(request, admin, {
    tournamentId: pool.tournamentId,
    homeTeamId: pool.homeId,
    awayTeamId: pool.awayId,
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
