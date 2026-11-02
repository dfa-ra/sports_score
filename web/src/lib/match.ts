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
  const date = kickoffDate(value)
  if (!date) return ''
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${day}/${month}`
}

export function kickoffClock(value?: string | number | Date | null) {
  const date = kickoffDate(value)
  if (!date) return ''
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

function kickoffDate(value?: string | number | Date | null) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
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
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = date.getFullYear()
  const time = date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
  return `${day}.${month}.${year} ${time}`
}

export function matchStateLabel(status?: string | null) {
  if (status === 'FINISHED') return 'Завершен'
  if (status === 'LIVE') return 'Live'
  if (status === 'PAUSED') return 'Пауза'
  if (status === 'CANCELLED') return 'Отменён'
  if (status === 'SCHEDULED') return 'Не начался'
  return status || ''
}

export { boundaryMinute, buildPeriodBlocks, compareMatchEvents, eventMinute, eventOnHomeSide, protocolMinute } from './matchMinute'

export function ymd(value?: string | number | Date | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}
