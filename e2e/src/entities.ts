import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord } from './api'
import type { Session } from './accounts'
import { uniqueEntityName } from './env'

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
  options: { name?: string; startDate?: string; endDate?: string; status?: string } = {},
) {
  const name = options.name || uniqueEntityName('турнир')
  return apiSend(request, 'POST', '/tournaments', {
    token: admin.accessToken,
    data: {
      name,
      description: 'E2E автотест, можно удалить',
      sportId: FOOTBALL_ID,
      seasonYear: 2026,
      startDate: options.startDate ?? '2026-11-10',
      endDate: options.endDate ?? '2026-12-10',
      status: options.status ?? 'REGISTRATION',
      format: 'ROUND_ROBIN',
    },
  })
}

export async function createTeam(request: APIRequestContext, admin: Session, name?: string) {
  return apiSend(request, 'POST', '/teams', {
    token: admin.accessToken,
    data: {
      name: name || uniqueEntityName('команда'),
      shortName: `E2${Math.random().toString(36).slice(2, 5)}`.slice(0, 8),
    },
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
  data: { tournamentId: string; homeTeamId: string; awayTeamId: string; scheduledAt?: string },
) {
  return apiSend(request, 'POST', '/matches', {
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

export async function firstPublicTeam(request: APIRequestContext) {
  const call = await apiGet(request, '/teams?size=20')
  return asList(call.body)[0]
}

export async function cleanupCreated(request: APIRequestContext, admin: Session, ids: CreatedIds) {
  for (const matchId of ids.matchIds) {
    await apiSend(request, 'DELETE', `/matches/${matchId}`, { token: admin.accessToken })
  }
  for (const teamId of ids.teamIds) {
    await apiSend(request, 'DELETE', `/teams/${teamId}`, { token: admin.accessToken })
    await apiSend(request, 'DELETE', `/teams/${teamId}?purge=true`, { token: admin.accessToken })
  }
  for (const galleryId of ids.galleryIds) {
    await apiSend(request, 'DELETE', `/admin/gallery/${galleryId}`, { token: admin.accessToken })
  }
  if (ids.tournamentId) {
    await apiSend(request, 'PUT', `/tournaments/${ids.tournamentId}`, {
      token: admin.accessToken,
      data: { status: 'CANCELLED', name: uniqueEntityName('архив') },
    })
  }
}

export function idOf(body: unknown): string {
  return String(asRecord(body).id || '')
}
