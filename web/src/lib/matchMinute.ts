/** Goal and card time stored as elapsed seconds from kickoff. `2:00` is `2'`. */
export function eventMinute(gameTime?: number | null) {
  return Math.floor(Math.max(0, gameTime ?? 0) / 60)
}

export type TimedEvent = {
  id?: string | null
  eventType?: string | null
  voided?: boolean
  gameTime?: number | null
  timestamp?: string | null
  period?: number | null
  teamId?: string | null
}

/** Earlier match-minute first. Tie-break by when the row was written. */
export function compareMatchEvents(a: TimedEvent, b: TimedEvent) {
  const byTime = (a.gameTime ?? 0) - (b.gameTime ?? 0)
  if (byTime !== 0) return byTime
  const byStamp = String(a.timestamp ?? '').localeCompare(String(b.timestamp ?? ''))
  if (byStamp !== 0) return byStamp
  return String(a.id ?? '').localeCompare(String(b.id ?? ''))
}

const visibleTypes = new Set(['GOAL', 'YELLOW_CARD', 'RED_CARD', 'SUBSTITUTION', 'OWN_GOAL'])

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
    if (scoring) {
      if (ev.teamId === match.homeTeamId) home += 1
      else away += 1
    }
    const period = ev.period || 1
    const block = byPeriod.get(period) ?? { items: [], score: '0-0' }
    block.items.push({
      ...ev,
      home: ev.teamId === match.homeTeamId,
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
