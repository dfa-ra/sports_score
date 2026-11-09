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

/** Stoppage beyond periodLength × periodCount. The API uses the same five minutes. */
export const ADDED_TIME_ALLOWANCE_MINUTES = 5

export function protocolMinuteLimit(periodLengthSeconds?: number | null, periodCount?: number | null) {
  const seconds = periodLengthSeconds && periodLengthSeconds > 0 ? periodLengthSeconds : 15 * 60
  const periods = periodCount && periodCount > 0 ? periodCount : 2
  return Math.floor((periods * seconds) / 60) + ADDED_TIME_ALLOWANCE_MINUTES
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

const feedTypes = new Set([
  'GOAL',
  'YELLOW_CARD',
  'RED_CARD',
  'SUBSTITUTION',
  'OWN_GOAL',
])

type FeedEvent = TimedEvent & {
  createdAt?: string | null
  addedMinute?: number | string | null
  metadata?: { ownGoal?: boolean; addedMinute?: number | string | null } | null
}

/** Extra stoppage minute only when the payload already carries it. The event DTO does not. */
export function feedAddedMinute(event: FeedEvent): number | null {
  const raw = event.addedMinute ?? event.metadata?.addedMinute
  if (raw == null || raw === '') return null
  const value = Number(raw)
  if (!Number.isFinite(value) || value <= 0) return null
  return Math.trunc(value)
}

/**
 * Ordinary minutes stay on the protocol clock, including `0'`.
 * Stoppage, when the payload already has `addedMinute` and the match has a
 * period length, is the end of that half: `(periodLengthSeconds / 60) × period`.
 * A missing length is not guessed; the protocol minute is left as it is.
 */
export function feedMinuteLabel(event: FeedEvent, periodLengthSeconds?: number | null) {
  const minute = protocolMinute(event, periodLengthSeconds)
  if (minute == null) return ''
  const added = feedAddedMinute(event)
  if (added == null) return `${minute}'`
  const length = periodLengthSeconds && periodLengthSeconds > 0 ? periodLengthSeconds : null
  if (length == null) return `${minute}+${added}'`
  const period = Math.max(1, Number(event.period) || 1)
  const base = Math.floor(length / 60) * period
  return `${base}+${added}'`
}

/** Overview order: displayed minute, then added time, then when the row was created, then id. */
export function compareFeedEvents(a: FeedEvent, b: FeedEvent, periodLengthSeconds?: number | null) {
  const byMinute = (protocolMinute(a, periodLengthSeconds) ?? -1) - (protocolMinute(b, periodLengthSeconds) ?? -1)
  if (byMinute !== 0) return byMinute
  const byAdded = (feedAddedMinute(a) ?? 0) - (feedAddedMinute(b) ?? 0)
  if (byAdded !== 0) return byAdded
  const byCreated = String(a.createdAt ?? a.timestamp ?? '').localeCompare(String(b.createdAt ?? b.timestamp ?? ''))
  if (byCreated !== 0) return byCreated
  return String(a.id ?? '').localeCompare(String(b.id ?? ''))
}

/**
 * Public overview feed. Whistles are not rows. The plate score is that half
 * only. The chip is the running match score, with an own goal on the side
 * that received the point.
 */
function halfHasStarted<T extends FeedEvent>(
  period: number,
  events: T[],
  matchPeriod?: number | null,
) {
  const live = events.filter((event) => !event.voided)
  if (live.some((event) => event.eventType === 'PERIOD_START' && (event.period || 1) === period)) return true
  const current = Number(matchPeriod) || 0
  if (current > period) return true
  return live.some((event) => (event.period || 1) > period)
}

export function buildMirrorFeed<T extends FeedEvent>(
  events: T[],
  match: { homeTeamId?: string | null; periodLengthSeconds?: number | null; period?: number | null },
) {
  const length = match.periodLengthSeconds
  const rows = events
    .filter((event) => !event.voided && !!event.eventType && feedTypes.has(event.eventType))
    .sort((a, b) => compareFeedEvents(a, b, length))
  let home = 0
  let away = 0
  const byPeriod = new Map<number, { home: number; away: number; items: Array<T & { home: boolean; scoreline: string | null }> }>()
  for (const ev of rows) {
    const scoring = ev.eventType === 'GOAL' || ev.eventType === 'OWN_GOAL'
    const forHome = eventOnHomeSide(ev, match.homeTeamId)
    if (scoring) {
      if (forHome) home += 1
      else away += 1
    }
    const period = ev.period || 1
    const block = byPeriod.get(period) ?? { home: 0, away: 0, items: [] }
    if (scoring) {
      if (forHome) block.home += 1
      else block.away += 1
    }
    block.items.push({
      ...ev,
      home: forHome,
      scoreline: scoring ? `${home}-${away}` : null,
    })
    byPeriod.set(period, block)
  }
  const periods = new Set<number>(byPeriod.keys())
  const seen = events.filter((event) => !event.voided)
  const furthest = Math.max(
    0,
    Number(match.period) || 0,
    ...seen.map((event) => event.period || 1),
  )
  for (let period = 1; period <= furthest; period += 1) {
    if (halfHasStarted(period, events, match.period)) periods.add(period)
  }
  const blocks = [...periods]
    .sort((a, b) => a - b)
    .map((period) => {
      const block = byPeriod.get(period) ?? { home: 0, away: 0, items: [] }
      return {
        period,
        score: `${block.home}-${block.away}`,
        items: block.items,
      }
    })
  return {
    blocks,
    total: `${home}-${away}`,
  }
}
