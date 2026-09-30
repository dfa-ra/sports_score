export const TEN_METER_FROM_FOUL = 6
export const SHORT_HANDED_SECONDS = 120

export type FutsalEvent = {
  eventType?: string
  voided?: boolean
  period?: number | null
  teamId?: string | null
  gameTime?: number | null
  metadata?: { ownGoal?: boolean } | null
}

export type FutsalSide = {
  teamId: string
  fouls: number
  tenMeters: boolean
  timeoutAvailable: boolean
  shortHanded: boolean
  shortHandedRemainingSeconds: number | null
  shortHandedEndsAtGameTime: number | null
}

export type FutsalBoard = {
  foulScope: 'PERIOD' | 'MATCH'
  period: number | null
  teams: FutsalSide[]
}

export function buildFutsalBoard(match: {
  homeTeamId: string
  awayTeamId: string
  period?: number | null
  periodCount?: number | null
}, events: FutsalEvent[], elapsedSeconds: number): FutsalBoard {
  const periodScoped = (match.periodCount ?? 0) > 0
  const period = periodScoped ? (match.period ?? null) : null
  const active = (events ?? []).filter((event) => inScope(event, periodScoped, period))
  return {
    foulScope: periodScoped ? 'PERIOD' : 'MATCH',
    period: match.period ?? null,
    teams: [
      side(active, match.homeTeamId, match.awayTeamId, elapsedSeconds),
      side(active, match.awayTeamId, match.homeTeamId, elapsedSeconds),
    ],
  }
}

function side(events: FutsalEvent[], teamId: string, opponentId: string, elapsed: number): FutsalSide {
  const fouls = events.filter((event) => event.eventType === 'FOUL' && event.teamId === teamId).length
  const timeoutAvailable = !events.some((event) => event.eventType === 'TIMEOUT' && event.teamId === teamId)
  const penalty = shortHanded(events, teamId, opponentId, elapsed)
  return {
    teamId,
    fouls,
    tenMeters: fouls >= TEN_METER_FROM_FOUL,
    timeoutAvailable,
    ...penalty,
  }
}

function shortHanded(events: FutsalEvent[], teamId: string, opponentId: string, elapsed: number) {
  let bestEnd = -1
  for (const red of events) {
    if (red.eventType !== 'RED_CARD' || red.teamId !== teamId) continue
    const start = red.gameTime ?? 0
    let end = start + SHORT_HANDED_SECONDS
    const concedeAt = earliestConcede(events, teamId, opponentId, start, end)
    if (concedeAt != null) end = concedeAt
    if (elapsed < end && end > bestEnd) bestEnd = end
  }
  if (bestEnd < 0) {
    return {
      shortHanded: false,
      shortHandedRemainingSeconds: null,
      shortHandedEndsAtGameTime: null,
    }
  }
  return {
    shortHanded: true,
    shortHandedRemainingSeconds: bestEnd - elapsed,
    shortHandedEndsAtGameTime: bestEnd,
  }
}

function earliestConcede(events: FutsalEvent[], teamId: string, opponentId: string, start: number, end: number) {
  let found: number | null = null
  for (const event of events) {
    if (event.eventType !== 'GOAL' || event.gameTime == null) continue
    const at = event.gameTime
    if (at <= start || at >= end || !benefitsOpponent(event, teamId, opponentId)) continue
    if (found == null || at < found) found = at
  }
  return found
}

function benefitsOpponent(event: FutsalEvent, teamId: string, opponentId: string) {
  if (event.metadata?.ownGoal === true) return event.teamId === teamId
  return event.teamId === opponentId
}

function inScope(event: FutsalEvent, periodScoped: boolean, period: number | null) {
  if (event.voided) return false
  if (!periodScoped) return true
  return (event.period ?? null) === period
}
