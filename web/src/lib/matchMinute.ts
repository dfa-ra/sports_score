/** Goal and card time stored as elapsed seconds from kickoff. `2:00` is `2'`. No stored time means no minute. */
export function eventMinute(gameTime?: number | null): number | null {
  if (gameTime == null || Number.isNaN(Number(gameTime))) return null
  return Math.floor(Math.max(0, Number(gameTime)) / 60)
}

/**
 * Period lines use the same kickoff seconds. A whistle at 0:26 is the first
 * minute, not `0'` and not the 15:00 still on the countdown.
 */
export function boundaryMinute(gameTime?: number | null) {
  const seconds = Math.max(0, gameTime ?? 0)
  if (seconds <= 0) return 0
  return Math.max(1, Math.floor(seconds / 60))
}

export function protocolMinute(event: { eventType?: string | null; gameTime?: number | null }) {
  if (event.eventType === 'PERIOD_START' || event.eventType === 'PERIOD_END') {
    return boundaryMinute(event.gameTime)
  }
  return eventMinute(event.gameTime)
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
