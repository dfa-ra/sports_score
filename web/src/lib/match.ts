import { eventOnHomeSide } from './matchMinute.ts'
import { DATE_PENDING, matchCardWhen, matchInstant, type MatchDateSource } from './matchDate.ts'
import { moscowClock, moscowDateTimeLabel, moscowDayMonth, moscowYmd } from './moscowTime.ts'

export { DATE_PENDING, matchCardWhen, matchInstant, type MatchDateSource }

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

/**
 * Score the public card is allowed to print.
 * SCHEDULED and CANCELLED stay a dash even when the API sends int 0.
 * LIVE, PAUSED and FINISHED keep the real numbers, including 0:0.
 */
export function unplayedCardScore(status?: string | null): string | null {
  if (status === 'SCHEDULED' || status === 'CANCELLED') return '– : –'
  return null
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

/** Referee board. A finished shootout uses the public line. Anything else keeps the spaced score. */
export function refereeBoardScore(
  match: {
    status?: string | null
    homeScore?: number | null
    awayScore?: number | null
    penaltyHome?: number | null
    penaltyAway?: number | null
  },
  series?: {
    kicks?: readonly unknown[] | null
    penaltyHome?: number | null
    penaltyAway?: number | null
  } | null,
  shootoutOpen = false,
) {
  const home = Number(match.homeScore ?? 0)
  const away = Number(match.awayScore ?? 0)
  if (shootoutOpen && (series?.kicks?.length ?? 0) > 0) {
    return publicScore({
      homeScore: home,
      awayScore: away,
      penaltyHome: series?.penaltyHome,
      penaltyAway: series?.penaltyAway,
    })
  }
  if (match.status === 'FINISHED') {
    const pens = penaltyPair({
      homeScore: home,
      awayScore: away,
      penaltyHome: match.penaltyHome ?? series?.penaltyHome,
      penaltyAway: match.penaltyAway ?? series?.penaltyAway,
    })
    if (pens) return publicScore({ homeScore: home, awayScore: away, penaltyHome: pens.home, penaltyAway: pens.away })
  }
  return `${home} : ${away}`
}

/**
 * Finish confirmation. A decided shootout names the score. Otherwise the early-finish
 * question stays when the half is still open.
 */
export function finishConfirmQuestion(input: {
  halfStillOpen: boolean
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
  decisive?: boolean | null
}) {
  const home = Number(input.homeScore ?? 0)
  const away = Number(input.awayScore ?? 0)
  if (input.decisive) {
    const pens = penaltyPair({
      homeScore: home,
      awayScore: away,
      penaltyHome: input.penaltyHome,
      penaltyAway: input.penaltyAway,
    })
    if (pens) return `Завершить матч? ${home}:${away}, пен. ${pens.home}:${pens.away}`
  }
  if (input.halfStillOpen) return 'Завершить матч до конца тайма? Счёт зафиксируется как есть.'
  return 'Завершить матч?'
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

type ProtocolEvent = {
  eventType?: string | null
  voided?: boolean
  teamId?: string | null
  metadata?: { ownGoal?: boolean; points?: number } | null
}

/**
 * Board score from the protocol the pad already has. Football and futsal count
 * goals (an own goal credits the other side). Other sports also count POINT.
 */
export function protocolScore(
  match: { homeTeamId?: string | null; awayTeamId?: string | null; sportCode?: string | null },
  events: readonly ProtocolEvent[],
) {
  const code = (match.sportCode || 'FOOTBALL').toUpperCase()
  const football = code === 'FOOTBALL' || code === 'FUTSAL'
  let home = 0
  let away = 0
  for (const event of events) {
    if (event.voided || !event.teamId) continue
    if (event.teamId !== match.homeTeamId && event.teamId !== match.awayTeamId) continue
    let points = 0
    if (event.eventType === 'GOAL' || event.eventType === 'OWN_GOAL') points = 1
    else if (!football && event.eventType === 'POINT') {
      const raw = event.metadata?.points
      points = typeof raw === 'number' && Number.isFinite(raw) ? Math.max(0, Math.trunc(raw)) : 1
    }
    if (points <= 0) continue
    if (eventOnHomeSide(event, match.homeTeamId)) home += points
    else away += points
  }
  return { home, away }
}

export function matchStateLabel(status?: string | null) {
  if (status === 'FINISHED') return 'Завершён'
  if (status === 'LIVE') return 'Live'
  if (status === 'PAUSED') return 'Пауза'
  if (status === 'CANCELLED') return 'Отменён'
  if (status === 'SCHEDULED') return 'Не начался'
  return status || ''
}

export { boundaryMinute, buildMirrorFeed, buildPeriodBlocks, compareFeedEvents, compareMatchEvents, eventMinute, eventOnHomeSide, feedAddedMinute, feedMinuteLabel, protocolMinute, protocolMinuteLimit } from './matchMinute.ts'

export function ymd(value?: string | number | Date | null) {
  return moscowYmd(value)
}
