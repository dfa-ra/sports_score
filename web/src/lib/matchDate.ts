export type MatchDateSource = {
  status?: string | null
  scheduledAt?: string | number | Date | null
  startedAt?: string | number | Date | null
}

function usableInstant(value?: string | number | Date | null) {
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : value
}

/**
 * One public match date.
 * LIVE, PAUSED, and FINISHED use the actual start (`startedAt`).
 * CANCELLED uses that start only if play began.
 * SCHEDULED, and a cancellation that never started, stay on `scheduledAt`.
 * A started status with no start timestamp falls back to `scheduledAt`.
 */
export function matchInstant(match?: MatchDateSource | null): string | number | Date | null {
  if (!match) return null
  const scheduled = usableInstant(match.scheduledAt)
  const started = usableInstant(match.startedAt)
  const status = match.status ?? ''
  const inPlay = status === 'LIVE' || status === 'PAUSED' || status === 'FINISHED'
  if (inPlay || (status === 'CANCELLED' && started)) return started ?? scheduled
  return scheduled
}
