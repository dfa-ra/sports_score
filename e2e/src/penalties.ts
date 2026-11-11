import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord, errorMessage } from './api'
import type { Session } from './accounts'
import { PENALTIES_SKIP, penaltiesDeployed, routeExists } from './playoff'

export { PENALTIES_SKIP, penaltiesDeployed }

const VIEW_PATHS = (id: string) => [
  `/matches/${id}/penalties`,
  `/matches/${id}/shootout`,
  `/matches/${id}/penalty-kicks`,
]

const START_PATHS = (id: string) => [
  `/referee/matches/${id}/penalties/start`,
  `/referee/matches/${id}/shootout/start`,
  `/referee/matches/${id}/penalties`,
  `/referee/matches/${id}/shootout`,
]

const KICK_PATHS = (id: string) => [
  `/referee/matches/${id}/penalties/kicks`,
  `/referee/matches/${id}/shootout/kicks`,
]

const ADMIN_KICK_PATHS = (id: string) => [
  `/admin/matches/${id}/penalties/kicks`,
  `/admin/matches/${id}/shootout/kicks`,
]

export function penaltyLabel(body: unknown): string {
  const row = asRecord(body)
  const direct = [
    row.displayScore,
    row.scoreLabel,
    row.resultLabel,
    row.publicScore,
    row.scoreText,
  ].filter((item) => typeof item === 'string') as string[]
  if (direct.length) return direct.join(' ')
  const home = row.homeScore ?? row.home_score
  const away = row.awayScore ?? row.away_score
  const penHome = row.homePenaltyScore ?? row.penaltyHomeScore ?? row.homeShootoutScore
  const penAway = row.awayPenaltyScore ?? row.penaltyAwayScore ?? row.awayShootoutScore
  if (penHome != null && penAway != null) {
    return `${home}:${away} (пен. ${penHome}:${penAway})`
  }
  return `${home ?? ''}:${away ?? ''} ${JSON.stringify(row)}`
}

export function looksLikePenaltyScore(text: string): boolean {
  return /1\s*:\s*1/.test(text) && /пен\.?\s*4\s*:\s*3|пен\.?\s*3\s*:\s*4/i.test(text)
}

export async function viewPenalties(request: APIRequestContext, matchId: string, token?: string) {
  for (const path of VIEW_PATHS(matchId)) {
    const call = await apiGet(request, path, token)
    if (routeExists(call) && call.status !== 401) return { ...call, path }
  }
  const match = await apiGet(request, `/matches/${matchId}`, token)
  return { ...match, path: `/matches/${matchId}` }
}

export async function startPenalties(
  request: APIRequestContext,
  token: string,
  matchId: string,
) {
  for (const path of START_PATHS(matchId)) {
    const call = await apiSend(request, 'POST', path, { token, data: {} })
    if (call.status === 404 && !routeExists(call)) continue
    return { ...call, path }
  }
  return apiSend(request, 'POST', `/referee/matches/${matchId}/penalties/start`, { token, data: {} })
}

export async function addPenaltyKick(
  request: APIRequestContext,
  token: string,
  matchId: string,
  data: { teamId: string; scored: boolean; playerId?: string },
  via: 'referee' | 'admin' = 'referee',
) {
  const payload = {
    teamId: data.teamId,
    scored: data.scored,
    playerId: data.playerId,
    eventType: data.scored ? 'PENALTY_GOAL' : 'PENALTY_MISS',
    metadata: { penalty: true, shootout: true, scored: data.scored },
  }
  const paths = via === 'admin' ? ADMIN_KICK_PATHS(matchId) : KICK_PATHS(matchId)
  for (const path of paths) {
    const call = await apiSend(request, 'POST', path, { token, data: payload })
    if (call.status === 404 && !routeExists(call)) continue
    return { ...call, path }
  }
  const fallback = via === 'admin'
    ? `/admin/matches/${matchId}/events`
    : `/referee/matches/${matchId}/events`
  return apiSend(request, 'POST', fallback, { token, data: payload })
}

export async function patchPenaltyKick(
  request: APIRequestContext,
  token: string,
  matchId: string,
  kickId: string,
  data: { scored: boolean },
) {
  const paths = [
    `/referee/matches/${matchId}/penalties/kicks/${kickId}`,
    `/referee/matches/${matchId}/shootout/kicks/${kickId}`,
    `/admin/matches/${matchId}/events/${kickId}`,
  ]
  for (const path of paths) {
    const call = await apiSend(request, 'PUT', path, { token, data })
    if (call.status === 404 && !routeExists(call)) {
      const patch = await apiSend(request, 'PATCH', path, { token, data })
      if (patch.status === 404 && !routeExists(patch)) continue
      return { ...patch, path }
    }
    return { ...call, path }
  }
  return apiSend(request, 'PATCH', `/admin/matches/${matchId}/events/${kickId}`, { token, data })
}

export async function voidPenaltyKick(
  request: APIRequestContext,
  token: string,
  matchId: string,
  kickId: string,
  via: 'admin' | 'referee' = 'admin',
) {
  const prefix = via === 'admin' ? '/admin/matches' : '/referee/matches'
  const paths = [
    `${prefix}/${matchId}/penalties/kicks/${kickId}/void`,
    `${prefix}/${matchId}/shootout/kicks/${kickId}/void`,
    `${prefix}/${matchId}/events/${kickId}/void`,
  ]
  for (const path of paths) {
    const call = await apiSend(request, 'POST', path, { token })
    if (call.status === 404 && !routeExists(call)) continue
    return { ...call, path }
  }
  return apiSend(request, 'POST', `${prefix}/${matchId}/events/${kickId}/void`, { token })
}

export function penaltyKicks(body: unknown): Record<string, unknown>[] {
  const row = asRecord(body)
  for (const key of ['kicks', 'shots', 'penalties', 'attempts']) {
    const list = row[key]
    if (Array.isArray(list)) return list as Record<string, unknown>[]
  }
  const nested = asRecord(row.shootout || row.series || row.penaltyShootout)
  for (const key of ['kicks', 'shots', 'penalties']) {
    const list = nested[key]
    if (Array.isArray(list)) return list as Record<string, unknown>[]
  }
  return asList(body).filter((item) => /penalt|shootout|пен/i.test(JSON.stringify(item)))
}

export async function kickIdOf(request: APIRequestContext, matchId: string): Promise<string> {
  const view = await viewPenalties(request, matchId)
  const kick = penaltyKicks(view.body).find((row) => row.voided !== true)
  return String(kick?.id || asRecord(view.body).id || '')
}

export async function playShootout(
  request: APIRequestContext,
  session: Session,
  matchId: string,
  homeId: string,
  awayId: string,
  homeMade: number,
  awayMade: number,
  rounds = 5,
) {
  const started = await startPenalties(request, session.accessToken, matchId)
  if (started.status >= 500) {
    throw new Error(`старт серии: ${started.status} ${JSON.stringify(started.body)}`)
  }
  for (let i = 0; i < rounds; i++) {
    const home = await addPenaltyKick(request, session.accessToken, matchId, {
      teamId: homeId,
      scored: i < homeMade,
    })
    if (home.status >= 400 && home.status !== 409) {
      throw new Error(`удар хозяев ${i + 1}: ${home.status} ${JSON.stringify(home.body)}`)
    }
    const away = await addPenaltyKick(request, session.accessToken, matchId, {
      teamId: awayId,
      scored: i < awayMade,
    })
    if (away.status >= 400 && away.status !== 409) {
      throw new Error(`удар гостей ${i + 1}: ${away.status} ${JSON.stringify(away.body)}`)
    }
  }
  return viewPenalties(request, matchId)
}

export async function assertPenaltiesAvailable(request: APIRequestContext): Promise<void> {
  if (!(await penaltiesDeployed(request))) {
    throw new Error(PENALTIES_SKIP)
  }
}
