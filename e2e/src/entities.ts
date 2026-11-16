import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from './api'
import type { Session } from './accounts'
import { uniqueEntityName } from './env'
import { Tracker } from './tracker'

const FOOTBALL_ID = '11111111-1111-1111-1111-111111111101'

export type CreatedIds = {
  tournamentId?: string
  teamIds: string[]
  matchIds: string[]
  galleryIds: string[]
}

export function emptyIds(): CreatedIds {
  return { teamIds: [], matchIds: [], galleryIds: [] }
}

export async function createTournament(
  request: APIRequestContext,
  admin: Session,
  options: { name?: string; startDate?: string; endDate?: string; status?: string; format?: string; track?: Tracker } = {},
) {
  const name = options.name || uniqueEntityName('турнир')
  const created = await apiSend(request, 'POST', '/tournaments', {
    token: admin.accessToken,
    data: {
      name,
      description: 'E2E автотест, можно удалить',
      sportId: FOOTBALL_ID,
      seasonYear: 2026,
      startDate: options.startDate ?? '2026-11-10',
      endDate: options.endDate ?? '2026-12-10',
      status: options.status ?? 'REGISTRATION',
      format: options.format ?? 'ROUND_ROBIN',
    },
  })
  if (created.status === 201) options.track?.tournament(idOf(created.body))
  return created
}

export async function createTeam(
  request: APIRequestContext,
  admin: Session,
  name?: string,
  shortName?: string,
  track?: Tracker,
) {
  const created = await apiSend(request, 'POST', '/teams', {
    token: admin.accessToken,
    data: {
      name: name || uniqueEntityName('команда'),
      shortName: (shortName || `E2${Math.random().toString(36).slice(2, 5)}`).slice(0, 8),
    },
  })
  if (created.status === 201) track?.team(idOf(created.body))
  return created
}

export async function updateTournament(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  data: Record<string, unknown>,
) {
  return apiSend(request, 'PUT', `/tournaments/${tournamentId}`, {
    token: admin.accessToken,
    data,
  })
}

export async function applyTeam(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  teamId: string,
) {
  return apiSend(request, 'POST', `/tournaments/${tournamentId}/teams`, {
    token: admin.accessToken,
    data: { teamId },
  })
}

export async function approveTeam(
  request: APIRequestContext,
  admin: Session,
  tournamentId: string,
  teamId: string,
) {
  return apiSend(request, 'POST', `/tournaments/${tournamentId}/teams/${teamId}/approve`, {
    token: admin.accessToken,
  })
}

export async function createMatch(
  request: APIRequestContext,
  admin: Session,
  data: { tournamentId: string; homeTeamId: string; awayTeamId: string; scheduledAt?: string; track?: Tracker },
) {
  const created = await apiSend(request, 'POST', '/matches', {
    token: admin.accessToken,
    data: {
      tournamentId: data.tournamentId,
      homeTeamId: data.homeTeamId,
      awayTeamId: data.awayTeamId,
      scheduledAt: data.scheduledAt || new Date().toISOString(),
      periodCount: 2,
      periodLengthMinutes: 15,
    },
  })
  if (created.status === 201) data.track?.match(idOf(created.body))
  return created
}

export async function assignReferee(
  request: APIRequestContext,
  admin: Session,
  matchId: string,
  refereeId: string,
) {
  return apiSend(request, 'POST', `/matches/${matchId}/referees`, {
    token: admin.accessToken,
    data: { refereeId },
  })
}

export async function firstPublicTournament(request: APIRequestContext) {
  const call = await apiGet(request, '/tournaments?size=20')
  return asList(call.body)[0]
}

export async function firstPublicMatch(request: APIRequestContext) {
  const call = await apiGet(request, '/matches?size=20&sort=scheduledAt,desc')
  return asList(call.body)[0]
}

export async function firstPublicTeam(request: APIRequestContext, excludeIds: string[] = []) {
  const skip = new Set(excludeIds.filter(Boolean))
  const rows: Record<string, unknown>[] = []
  for (let page = 0; page < 4; page++) {
    const call = await apiGet(request, `/teams?size=50&page=${page}`)
    const batch = asList(call.body)
    rows.push(...batch)
    if (batch.length < 50) break
  }
  return rows.find((row) => !skip.has(String(row.id)))
}

export async function cleanupCreated(request: APIRequestContext, admin: Session, ids: CreatedIds) {
  const { disposeBag } = await import('./cleanup')
  const bag = new Tracker()
  if (ids.tournamentId) bag.tournament(ids.tournamentId)
  for (const id of ids.teamIds) bag.team(id)
  for (const id of ids.matchIds) bag.match(id)
  for (const id of ids.galleryIds) bag.gallery(id)
  await disposeBag(request, bag)
}

export function idOf(body: unknown): string {
  return String(asRecord(body).id || '')
}
