import { moscowDateTimeLabel } from './moscowTime.ts'

export type MatchDateSource = {
  status?: string | null
  scheduledAt?: string | number | Date | null
  startedAt?: string | number | Date | null
}

/** Shown when neither a planned time nor an actual start exists. */
export const DATE_PENDING = 'Дата уточняется'

const PLAYED = new Set(['LIVE', 'PAUSED', 'FINISHED', 'CANCELLED'])

function usableInstant(value?: string | number | Date | null) {
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : value
}

/**
 * One public match instant for the games feed, the playoff bracket, and the match card.
 * Planned time (`scheduledAt`) wins whenever it is set.
 * If it is missing and the match has started or been played, use the actual start.
 * A cancellation counts only when that start exists; a match that never started stays empty.
 */
export function matchInstant(match?: MatchDateSource | null): string | number | Date | null {
  if (!match) return null
  const scheduled = usableInstant(match.scheduledAt)
  if (scheduled) return scheduled
  const status = match.status ?? ''
  if (!PLAYED.has(status)) return null
  return usableInstant(match.startedAt)
}

/** Card label above the score. Pending copy replaces a dash when the helper has no instant. */
export function matchCardWhen(match?: MatchDateSource | null): string {
  const instant = matchInstant(match)
  if (!instant) return DATE_PENDING
  return moscowDateTimeLabel(instant) || DATE_PENDING
}
