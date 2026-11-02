type MinuteInput = {
  gameTime?: number | null
  period?: number | null
  eventType?: string | null
  periodLengthSeconds?: number | null
}

/**
 * Elapsed match minute. `2:00` in the first half is `2'`.
 * Kickoff (`0` on a period line) is `1'`. Six seconds into the second half
 * of a 2×15 (`15:06`) is `16'`. An exact `15:00` stays `15'` for the end of
 * the first half and is `16'` for the start of the second.
 * No stored time means no minute.
 */
export function matchMinute(input: MinuteInput = {}): number | null {
  if (input.gameTime == null || Number.isNaN(Number(input.gameTime))) return null
  const seconds = Math.max(0, Number(input.gameTime))
  const length = input.periodLengthSeconds && input.periodLengthSeconds > 0 ? input.periodLengthSeconds : 15 * 60
  const boundary = input.eventType === 'PERIOD_START' || input.eventType === 'PERIOD_END'
  if (boundary && seconds < 60) return 1
  const minute = Math.floor(seconds / 60)
  const intoPeriod = seconds % length
  if (seconds >= length && intoPeriod > 0 && intoPeriod < 60) return minute + 1
  const period = input.period == null ? null : Math.max(1, Number(input.period) || 1)
  if (period != null && period > 1 && seconds === (period - 1) * length) {
    return minute + 1
  }
  return minute
}

/** Goal and card time stored as elapsed seconds from kickoff. `2:00` is `2'`. */
export function eventMinute(gameTime?: number | null, period?: number | null, periodLengthSeconds?: number | null) {
  return matchMinute({ gameTime, period, periodLengthSeconds })
}

/**
 * Period lines use the same kickoff seconds. A whistle at 0:26 is the first
 * minute, not `0'` and not the 15:00 still on the countdown. Kickoff is `1'`.
 */
export function boundaryMinute(gameTime?: number | null, periodLengthSeconds?: number | null) {
  return matchMinute({ gameTime, eventType: 'PERIOD_END', periodLengthSeconds })
}

export function protocolMinute(
  event: { eventType?: string | null; gameTime?: number | null; period?: number | null },
  periodLengthSeconds?: number | null,
) {
  return matchMinute({
    gameTime: event.gameTime,
    period: event.period,
    eventType: event.eventType,
    periodLengthSeconds,
  })
}

export type TimedEvent = {
  id?: string | null
  eventType?: string | null
  voided?: boolean
  gameTime?: number | null
  timestamp?: string | null
  period?: number | null
  teamId?: string | null
  metadata?: { ownGoal?: boolean } | null
}

/** Earlier match-minute first. Tie-break by when the row was written. */
export function compareMatchEvents(a: TimedEvent, b: TimedEvent) {
  const byTime = (a.gameTime ?? 0) - (b.gameTime ?? 0)
  if (byTime !== 0) return byTime
  const byStamp = String(a.timestamp ?? '').localeCompare(String(b.timestamp ?? ''))
  if (byStamp !== 0) return byStamp
  return String(a.id ?? '').localeCompare(String(b.id ?? ''))
}

const visibleTypes = new Set([
  'GOAL',
  'YELLOW_CARD',
  'RED_CARD',
  'SUBSTITUTION',
  'OWN_GOAL',
  'PERIOD_START',
  'PERIOD_END',
])

/**
 * Which touchline the row belongs to. An own goal is filed with the team that
 * received the point. The event still names the player who kicked it in.
 */
export function eventOnHomeSide(
  ev: { eventType?: string | null; teamId?: string | null; metadata?: { ownGoal?: boolean } | null },
  homeTeamId?: string | null,
) {
  const byHome = ev.teamId === homeTeamId
  const ownGoal = ev.eventType === 'OWN_GOAL' || (ev.eventType === 'GOAL' && ev.metadata?.ownGoal === true)
  return ownGoal ? !byHome : byHome
}

export function buildPeriodBlocks<T extends TimedEvent>(
  events: T[],
  match: { homeTeamId?: string | null },
) {
  const chrono = events
    .filter((event) => !event.voided && !!event.eventType && visibleTypes.has(event.eventType))
    .sort(compareMatchEvents)
  let home = 0
  let away = 0
  const byPeriod = new Map<number, { items: Array<T & { home: boolean; scoreline: string | null }>; score: string }>()
  for (const ev of chrono) {
    const scoring = ev.eventType === 'GOAL' || ev.eventType === 'OWN_GOAL'
    const forHome = eventOnHomeSide(ev, match.homeTeamId)
    if (scoring) {
      if (forHome) home += 1
      else away += 1
    }
    const period = ev.period || 1
    const block = byPeriod.get(period) ?? { items: [], score: '0-0' }
    block.items.push({
      ...ev,
      home: forHome,
      scoreline: scoring ? `${home}-${away}` : null,
    })
    block.score = `${home}-${away}`
    byPeriod.set(period, block)
  }
  return [...byPeriod.entries()].map(([period, block]) => ({
    period,
    score: block.score,
    items: block.items,
  }))
}
