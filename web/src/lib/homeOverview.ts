import { eventMinute } from './matchMinute'
import {
  MOSCOW_TIME_ZONE,
  civilDateParts,
  moscowClock,
  moscowDayMonth,
  moscowYmd,
  parseInstant,
  shiftYmd,
} from './moscowTime'

export type HomeMatch = {
  id: string
  tournamentId: string
  tournamentName: string
  tournamentShortName: string
  scheduledAt: string
  startedAt?: string | null
  finishedAt?: string | null
  status: string
  homeTeamId: string
  homeTeamName: string
  homeTeamShortName?: string | null
  homeLogoUrl?: string | null
  awayTeamId: string
  awayTeamName: string
  awayTeamShortName?: string | null
  awayLogoUrl?: string | null
  homeScore: number
  awayScore: number
  minute?: number | null
  gameTimeSeconds?: number | null
  period?: number | null
  periodCount?: number
  periodLengthSeconds?: number
  clockRunningSince?: string | null
  bracketRound?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
  stageLabel?: string | null
  round?: number | null
}

export type HomeLeader = {
  teamId: string
  teamName: string
  shortName?: string | null
  logoUrl?: string | null
  points: number
  played: number
  groupName?: string | null
}

export type HomeTournament = {
  id: string
  name: string
  shortName: string
  status: string
  seasonYear?: number | null
  seasonLabel?: string | null
  startDate?: string | null
  endDate?: string | null
  format?: string | null
  formatTitle?: string | null
  teamsCount: number
  competitionStage?: string | null
  stageLabel?: string | null
  currentRound?: number | null
  roundsTotal?: number | null
  progress?: number | null
  leader?: HomeLeader | null
  playoffRoundTitle?: string | null
  playoffPlayedPairs?: number | null
  playoffTotalPairs?: number | null
  registrationClosesOn?: string | null
  lastMatch?: HomeMatch | null
  nextMatch?: HomeMatch | null
}

export type HomeScorer = {
  playerId: string
  displayName: string
  goals: number
  assists: number
  appearances: number
  teamId?: string | null
  teamName?: string | null
  avatarUrl?: string | null
  teamLogoUrl?: string | null
}

export type HomeScorers = {
  tournamentId: string
  tournamentName: string
  shortName: string
  scorers: HomeScorer[]
}

export type LeagueSummary = {
  matchesPlayed: number
  goals: number
  goalsPerMatch: number
  teams: number
  players: number
  updatedAt?: string
}

export type HomeOverview = {
  generatedAt?: string
  next?: HomeMatch | null
  live: HomeMatch[]
  upcoming: HomeMatch[]
  recent: HomeMatch[]
  tournaments: HomeTournament[]
  scorersByTournament: HomeScorers[]
  summary?: LeagueSummary | null
}

export function matchTournamentChips<T extends { status?: string }>(tournaments: T[]) {
  return tournaments.filter((tournament) => tournament.status === 'ACTIVE')
}

export function emptyOverview(): HomeOverview {
  return { live: [], upcoming: [], recent: [], tournaments: [], scorersByTournament: [], summary: null, next: null }
}

const LIVE_MATCH_STATUS = new Set(['LIVE', 'PAUSED'])

function tournamentHasLiveMatch(tournament: HomeTournament, live: HomeMatch[]) {
  if (live.some((match) => match.tournamentId === tournament.id && LIVE_MATCH_STATUS.has(match.status))) return true
  return [tournament.lastMatch, tournament.nextMatch].some((match) => !!match && LIVE_MATCH_STATUS.has(match.status))
}

/** Earliest upcoming kickoff already chosen for the card. Missing when the tournament has no future match. */
function nearestKickoff(tournament: HomeTournament) {
  const match = tournament.nextMatch
  if (!match?.scheduledAt || match.status !== 'SCHEDULED') return null
  const instant = parseInstant(match.scheduledAt)
  return instant ? instant.getTime() : null
}

/**
 * «Идут сейчас»: live matches, then other ACTIVE tournaments by nearest kickoff,
 * then REGISTRATION. Input order is the API start-date order and stays in place
 * when kickoff times are equal or missing. A live tournament is not repeated.
 */
export function orderRunningTournaments(tournaments: HomeTournament[], live: HomeMatch[] = []) {
  const liveGroup: HomeTournament[] = []
  const activeGroup: HomeTournament[] = []
  const registrationGroup: HomeTournament[] = []
  const otherGroup: HomeTournament[] = []
  for (const tournament of tournaments) {
    if (tournamentHasLiveMatch(tournament, live)) liveGroup.push(tournament)
    else if (tournament.status === 'ACTIVE') activeGroup.push(tournament)
    else if (tournament.status === 'REGISTRATION') registrationGroup.push(tournament)
    else otherGroup.push(tournament)
  }
  activeGroup.sort((left, right) => {
    const a = nearestKickoff(left)
    const b = nearestKickoff(right)
    if (a == null && b == null) return 0
    if (a == null) return 1
    if (b == null) return -1
    return a - b
  })
  return [...liveGroup, ...activeGroup, ...registrationGroup, ...otherGroup]
}

/** Same unfiltered list as /tournaments: totalElements, or the rendered page when the total is absent. */
export function tournamentDirectoryCount(payload: { content?: unknown; totalElements?: unknown } | null | undefined) {
  if (!payload || typeof payload !== 'object') return null
  const total = payload.totalElements
  if (typeof total === 'number' && Number.isFinite(total)) return total
  return Array.isArray(payload.content) ? payload.content.length : null
}

export function crestClass(id?: string | null) {
  if (!id) return 'c0'
  let hash = 0
  for (let i = 0; i < id.length; i += 1) hash = (hash * 31 + id.charCodeAt(i)) >>> 0
  return `c${hash % 6}`
}

const BADGES = ['tb-0', 'tb-1', 'tb-2', 'tb-3'] as const

export function badgeClass(index: number) {
  const slot = ((index % BADGES.length) + BADGES.length) % BADGES.length
  return BADGES[slot]
}

export function badgeIndex(tournaments: { id: string }[], tournamentId?: string | null) {
  const index = tournaments.findIndex((item) => item.id === tournamentId)
  return index < 0 ? 0 : index
}

export function isFutureScheduled(
  match: { id?: string; status?: string; scheduledAt?: string | null } | null | undefined,
  nowMs: number = Date.now(),
) {
  if (!match?.id || match.status !== 'SCHEDULED' || !match.scheduledAt) return false
  const instant = parseInstant(match.scheduledAt)
  return !!instant && instant.getTime() >= nowMs
}

export function heroWhen(value?: string | null, now: Date = new Date()) {
  const day = moscowYmd(value)
  const clock = moscowClock(value)
  if (!day || !clock) return ''
  const today = moscowYmd(now)
  if (day === today) return `сегодня, ${clock}`
  if (day === shiftYmd(today, 1)) return `завтра, ${clock}`
  const instant = parseInstant(value)
  if (!instant) return clock
  const label = new Intl.DateTimeFormat('ru-RU', {
    timeZone: MOSCOW_TIME_ZONE,
    day: '2-digit',
    month: 'short',
  }).format(instant).replace(/\./g, '')
  return `${label}, ${clock}`
}

export function countdown(value?: string | null, nowMs: number = Date.now()) {
  const instant = parseInstant(value)
  if (!instant) return ''
  const delta = instant.getTime() - nowMs
  if (delta <= 60_000) return 'скоро'
  const minutes = Math.floor(delta / 60_000)
  const days = Math.floor(minutes / (60 * 24))
  const hours = Math.floor((minutes - days * 24 * 60) / 60)
  const mins = minutes % 60
  if (days > 0) return `через ${days} д ${hours} ч`
  if (hours > 0) return `через ${hours} ч ${mins} мин`
  return `через ${mins} мин`
}

export function matchClockLabel(value?: string | null) {
  return moscowClock(value)
}

export function matchDayLabel(value?: string | null) {
  return moscowDayMonth(value)
}

export function isToday(value?: string | null, now: Date = new Date()) {
  const day = moscowYmd(value)
  return !!day && day === moscowYmd(now)
}

export function statusChip(match: { status?: string; scheduledAt?: string | null }, now: Date = new Date()) {
  if (match.status === 'LIVE' || match.status === 'PAUSED') return 'live'
  if (match.status === 'FINISHED') return 'done'
  if (isToday(match.scheduledAt, now)) return 'today'
  return 'soon'
}

export function statusText(
  match: { status?: string; scheduledAt?: string | null; minute?: number | null },
  now: Date = new Date(),
) {
  if (match.status === 'LIVE' || match.status === 'PAUSED') {
    return match.minute == null ? 'Live' : `Live ${match.minute}′`
  }
  if (match.status === 'FINISHED') return 'Завершён'
  if (isToday(match.scheduledAt, now)) return 'Сегодня'
  return 'Скоро'
}

export function stageText(match: HomeMatch) {
  const stage = match.stageLabel?.trim() || ''
  const pens = penaltyText(match)
  if (stage && pens) return `${stage} · ${pens}`
  return stage || pens
}

export function penaltyText(match: { homeScore?: number | null; awayScore?: number | null; penaltyHome?: number | null; penaltyAway?: number | null }) {
  if (match.penaltyHome == null || match.penaltyAway == null) return ''
  if (Number(match.homeScore) !== Number(match.awayScore)) return ''
  return `пен. ${match.penaltyHome}:${match.penaltyAway}`
}

export function tickingMinute(match: HomeMatch, nowMs: number) {
  if (match.status !== 'LIVE' && match.status !== 'PAUSED') return null
  if (match.period == null && match.gameTimeSeconds == null && match.clockRunningSince == null) {
    return match.minute ?? null
  }
  const cap = match.periodLengthSeconds && match.periodLengthSeconds > 0 ? match.periodLengthSeconds : 900
  let base = match.gameTimeSeconds ?? 0
  if (match.status === 'LIVE' && match.clockRunningSince) {
    const since = parseInstant(match.clockRunningSince)
    if (since) base += Math.floor((nowMs - since.getTime()) / 1000)
  }
  const elapsed = Math.min(Math.max(0, base), cap)
  const period = Math.max(1, Number(match.period) || 1)
  return eventMinute((period - 1) * cap + elapsed, period, cap)
}

export function withLiveMinute(match: HomeMatch, nowMs: number): HomeMatch {
  const minute = tickingMinute(match, nowMs)
  return minute == null ? match : { ...match, minute }
}

export function feedUpcoming(overview: HomeOverview, tournamentId: string | null, nextId?: string | null) {
  return overview.upcoming
    .filter((match) => match.status === 'SCHEDULED')
    .filter((match) => !nextId || match.id !== nextId)
    .filter((match) => !tournamentId || match.tournamentId === tournamentId)
    .slice(0, 3)
}

export function feedRecent(overview: HomeOverview, tournamentId: string | null) {
  return overview.recent
    .filter((match) => match.status === 'FINISHED')
    .filter((match) => !tournamentId || match.tournamentId === tournamentId)
    .slice(0, 3)
}

export function ruDayMonth(value?: string | null) {
  const parts = civilDateParts(value)
  if (!parts) return ''
  const noonMoscow = new Date(Date.UTC(parts.year, parts.month - 1, parts.day, 9, 0, 0))
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: MOSCOW_TIME_ZONE,
    day: 'numeric',
    month: 'long',
  }).format(noonMoscow)
}

export function goalsPerMatchLabel(value: number) {
  return new Intl.NumberFormat('ru-RU', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value)
}

export function countLabel(count: number, one: string, few: string, many: string) {
  const abs = Math.abs(count) % 100
  const last = abs % 10
  if (abs > 10 && abs < 20) return many
  if (last === 1) return one
  if (last >= 2 && last <= 4) return few
  return many
}

export function progressWidth(value?: number | null) {
  if (value == null || Number.isNaN(value)) return 0
  return Math.max(0, Math.min(100, Math.round(value * 100)))
}

export function leaderLine(tournament: HomeTournament) {
  if (tournament.status === 'REGISTRATION') {
    return `Заявлено ${tournament.teamsCount} ${countLabel(tournament.teamsCount, 'команда', 'команды', 'команд')}`
  }
  if (tournament.competitionStage === 'PLAYOFF' && tournament.playoffRoundTitle) {
    const played = tournament.playoffPlayedPairs ?? 0
    const total = tournament.playoffTotalPairs ?? 0
    return `Стадия: ${tournament.playoffRoundTitle} · сыграно ${played} из ${total} пар`
  }
  const leader = tournament.leader
  if (!leader) return ''
  const group = leader.groupName?.trim()
  const title = group ? `Лидер ${group}` : 'Лидер таблицы'
  return `${title} · ${leader.points} очк. · ${leader.played} ${countLabel(leader.played, 'игра', 'игры', 'игр')}`
}

export function registrationHeadline(tournament: HomeTournament) {
  const date = ruDayMonth(tournament.registrationClosesOn || tournament.startDate)
  return date ? `Набор команд до ${date}` : 'Регистрация'
}

export function registrationEmpty(tournament: HomeTournament) {
  const date = ruDayMonth(tournament.registrationClosesOn || tournament.startDate)
  return date ? `Регистрация команд открыта · до ${date}` : 'Регистрация команд открыта'
}
