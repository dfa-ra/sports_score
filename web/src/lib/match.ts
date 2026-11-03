import { moscowClock, moscowDateTimeLabel, moscowDayMonth, moscowYmd } from './moscowTime.ts'

export function matchOutcome(match: {
  status?: string
  homeTeamId?: string
  awayTeamId?: string
  homeScore?: number
  awayScore?: number
  penaltyHome?: number | null
  penaltyAway?: number | null
}, teamId?: string | null) {
  if (!teamId || match.status !== 'FINISHED') return null
  const home = teamId === match.homeTeamId
  if (!home && teamId !== match.awayTeamId) return null
  const scored = home ? Number(match.homeScore) : Number(match.awayScore)
  const conceded = home ? Number(match.awayScore) : Number(match.homeScore)
  if (scored > conceded) return 'WIN'
  if (scored < conceded) return 'LOSS'
  const pens = penaltyPair(match)
  if (!pens) return 'DRAW'
  const taken = home ? pens.home : pens.away
  const allowed = home ? pens.away : pens.home
  if (taken > allowed) return 'WIN'
  if (taken < allowed) return 'LOSS'
  return 'DRAW'
}

export function penaltyPair(match: {
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
}) {
  const home = Number(match.homeScore ?? 0)
  const away = Number(match.awayScore ?? 0)
  if (home !== away || match.penaltyHome == null || match.penaltyAway == null) return null
  return { home: match.penaltyHome, away: match.penaltyAway }
}

export function publicScore(match: {
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
}) {
  const home = Number(match.homeScore ?? 0)
  const away = Number(match.awayScore ?? 0)
  const pens = penaltyPair(match)
  if (!pens) return `${home}:${away}`
  return `${home}:${away} (пен. ${pens.home}:${pens.away})`
}

export function kickoffDayMonth(value?: string | number | Date | null) {
  return moscowDayMonth(value)
}

export function kickoffClock(value?: string | number | Date | null) {
  return moscowClock(value)
}

export function shortKickoff(value?: string | number | Date | null, status?: string) {
  if (status === 'LIVE' || status === 'PAUSED') return 'LIVE'
  if (!value) return '—'
  const date = kickoffDayMonth(value)
  if (!date) return '—'
  if (status === 'FINISHED' || status === 'CANCELLED') return date.replace('/', '.') + '.'
  return kickoffClock(value) || '—'
}

export function longKickoff(value?: string | number | Date | null) {
  if (!value) return '—'
  return moscowDateTimeLabel(value) || '—'
}

export function matchStateLabel(status?: string | null) {
  if (status === 'FINISHED') return 'Завершен'
  if (status === 'LIVE') return 'Live'
  if (status === 'PAUSED') return 'Пауза'
  if (status === 'CANCELLED') return 'Отменён'
  if (status === 'SCHEDULED') return 'Не начался'
  return status || ''
}

export { boundaryMinute, buildPeriodBlocks, compareMatchEvents, eventMinute, eventOnHomeSide, protocolMinute, protocolMinuteLimit } from './matchMinute.ts'

export function ymd(value?: string | number | Date | null) {
  return moscowYmd(value)
}

export { matchInstant, type MatchDateSource } from './matchDate.ts'
