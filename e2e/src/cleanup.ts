import type { APIRequestContext } from '@playwright/test'
import { apiGet, apiSend, asList, asRecord, errorMessage } from './api'
import { login, requireRole } from './accounts'
import { accounts, hasRole } from './env'
import { isE2EName } from './run'
import { Tracker } from './tracker'

export type Leftover = {
  kind: 'tournament' | 'team' | 'match' | 'gallery'
  id: string
  name: string
  reason: string
}

export type SweepReport = {
  deletedMatches: number
  excludedEntries: number
  cancelledTournaments: number
  deletedTournaments: number
  purgedTeams: number
  deletedGalleries: number
  leftovers: Leftover[]
}

function emptyReport(): SweepReport {
  return {
    deletedMatches: 0,
    excludedEntries: 0,
    cancelledTournaments: 0,
    deletedTournaments: 0,
    purgedTeams: 0,
    deletedGalleries: 0,
    leftovers: [],
  }
}

export async function listAll(
  request: APIRequestContext,
  path: string,
  token?: string,
): Promise<Record<string, unknown>[]> {
  const rows: Record<string, unknown>[] = []
  const sep = path.includes('?') ? '&' : '?'
  for (let page = 0; page < 40; page++) {
    const call = await apiGet(request, `${path}${sep}size=50&page=${page}`, token)
    const batch = asList(call.body)
    rows.push(...batch)
    if (batch.length < 50) break
  }
  return rows
}

export async function adminForCleanup(request: APIRequestContext) {
  if (!hasRole('ADMIN')) return undefined
  return requireRole(request, 'ADMIN')
}

async function protectedTeamIds(request: APIRequestContext): Promise<Set<string>> {
  const skip = new Set<string>()
  if (hasRole('CAPTAIN') && accounts.captain) {
    const captain = await login(request, accounts.captain)
    const mine = await apiGet(request, '/teams/mine', captain.accessToken)
    const id = String(asRecord(mine.body).id || '')
    if (id) skip.add(id)
  }
  return skip
}

async function deleteMatch(request: APIRequestContext, token: string, matchId: string) {
  return apiSend(request, 'DELETE', `/matches/${matchId}`, { token })
}

async function excludeTeam(request: APIRequestContext, token: string, tournamentId: string, teamId: string) {
  return apiSend(request, 'DELETE', `/tournaments/${tournamentId}/teams/${teamId}`, { token })
}

async function tryDeleteTournament(request: APIRequestContext, token: string, tournamentId: string) {
  return apiSend(request, 'DELETE', `/tournaments/${tournamentId}`, { token })
}

async function cancelTournament(request: APIRequestContext, token: string, tournamentId: string) {
  const current = await apiGet(request, `/tournaments/${tournamentId}`, token)
  const body = asRecord(current.body)
  return apiSend(request, 'PUT', `/tournaments/${tournamentId}`, {
    token,
    data: {
      name: body.name,
      description: body.description,
      sportId: body.sportId,
      seasonYear: body.seasonYear,
      startDate: body.startDate,
      endDate: body.endDate,
      status: 'CANCELLED',
      format: body.format,
    },
  })
}

async function purgeTeam(request: APIRequestContext, token: string, teamId: string) {
  const disband = await apiSend(request, 'DELETE', `/teams/${teamId}`, { token })
  if (disband.status >= 500) return disband
  return apiSend(request, 'DELETE', `/teams/${teamId}?purge=true`, { token })
}

export async function disposeBag(
  request: APIRequestContext,
  bag: Tracker,
  report: SweepReport = emptyReport(),
): Promise<SweepReport> {
  const admin = await adminForCleanup(request)
  if (!admin) {
    if (!bag.empty) {
      report.leftovers.push({
        kind: 'tournament',
        id: '',
        name: '',
        reason: 'нет E2E_ADMIN_* — afterAll не смог удалить сущности',
      })
    }
    return report
  }
  const token = admin.accessToken
  const keepTeams = await protectedTeamIds(request)

  for (const id of bag.galleries) {
    const call = await apiSend(request, 'DELETE', `/admin/gallery/${id}`, { token })
    if (call.status < 400 || call.status === 404) report.deletedGalleries += 1
    else {
      report.leftovers.push({ kind: 'gallery', id, name: id, reason: `${call.status} ${errorMessage(call.body)}` })
    }
  }

  const matchIds = new Set(bag.matches)
  for (const tournamentId of bag.tournaments) {
    const matches = await listAll(request, `/tournaments/${tournamentId}/matches`)
    for (const row of matches) {
      if (row.id) matchIds.add(String(row.id))
    }
  }

  for (const matchId of matchIds) {
    const call = await deleteMatch(request, token, matchId)
    if (call.status < 400 || call.status === 404) report.deletedMatches += 1
    else {
      report.leftovers.push({
        kind: 'match',
        id: matchId,
        name: matchId,
        reason: `${call.status} ${errorMessage(call.body) || 'скорее всего FK playoff_slots.match_id'}`,
      })
    }
  }

  for (const tournamentId of bag.tournaments) {
    const entries = asList((await apiGet(request, `/tournaments/${tournamentId}/teams`, token)).body)
    for (const row of entries) {
      const teamId = String(row.teamId || row.id || '')
      if (!teamId) continue
      const call = await excludeTeam(request, token, tournamentId, teamId)
      if (call.status < 400 || call.status === 404) report.excludedEntries += 1
    }
    const removed = await tryDeleteTournament(request, token, tournamentId)
    if (removed.status < 400 || removed.status === 404) {
      report.deletedTournaments += 1
      continue
    }
    const cancelled = await cancelTournament(request, token, tournamentId)
    if (cancelled.status < 400) report.cancelledTournaments += 1
    const view = await apiGet(request, `/tournaments/${tournamentId}`)
    const name = String(asRecord(view.body).name || tournamentId)
    report.leftovers.push({
      kind: 'tournament',
      id: tournamentId,
      name,
      reason: `DELETE /tournaments/{id} → ${removed.status} ${errorMessage(removed.body) || 'эндпоинта нет'}; оставили CANCELLED`,
    })
  }

  for (const teamId of bag.teams) {
    if (keepTeams.has(teamId)) continue
    const call = await purgeTeam(request, token, teamId)
    if (call.status < 400 || call.status === 404) report.purgedTeams += 1
    else {
      const view = await apiGet(request, `/teams/${teamId}`)
      report.leftovers.push({
        kind: 'team',
        id: teamId,
        name: String(asRecord(view.body).name || teamId),
        reason: `${call.status} ${errorMessage(call.body) || 'purge не прошёл (есть матчи)'}`,
      })
    }
  }

  return report
}

export async function sweepE2E(request: APIRequestContext): Promise<SweepReport> {
  const report = emptyReport()
  const admin = await adminForCleanup(request)
  if (!admin) {
    console.warn('[e2e cleanup] нет E2E_ADMIN_* — зачистка пропущена')
    return report
  }
  const bag = new Tracker()
  const keepTeams = await protectedTeamIds(request)

  for (const row of await listAll(request, '/tournaments')) {
    if (isE2EName(row.name) && row.id) bag.tournament(String(row.id))
  }
  for (const row of await listAll(request, '/teams?includeDisbanded=true')) {
    const id = String(row.id || '')
    if (isE2EName(row.name) && id && !keepTeams.has(id)) bag.team(id)
  }
  for (const row of await listAll(request, '/matches?sort=scheduledAt,desc')) {
    const blob = `${row.homeTeamName || ''} ${row.awayTeamName || ''} ${row.tournamentName || ''}`
    if (row.id && (isE2EName(row.homeTeamName) || isE2EName(row.awayTeamName) || isE2EName(row.tournamentName) || /E2E/.test(blob))) {
      bag.match(String(row.id))
    }
  }
  const gallery = await apiGet(request, '/admin/gallery?size=100', admin.accessToken)
  for (const row of asList(asRecord(gallery.body).photos || gallery.body)) {
    if (isE2EName(row.title) && row.id) bag.gallery(String(row.id))
  }

  return disposeBag(request, bag, report)
}

export function printReport(report: SweepReport, title = 'e2e cleanup') {
  const lines = [
    `[${title}] matches=${report.deletedMatches} entries=${report.excludedEntries} tournamentsDeleted=${report.deletedTournaments} cancelled=${report.cancelledTournaments} teams=${report.purgedTeams} gallery=${report.deletedGalleries} leftovers=${report.leftovers.length}`,
  ]
  for (const item of report.leftovers) {
    lines.push(`  leftover ${item.kind} ${item.id} «${item.name}»: ${item.reason}`)
  }
  console.log(lines.join('\n'))
}
