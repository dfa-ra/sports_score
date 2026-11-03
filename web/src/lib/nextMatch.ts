export type NextMatchRow = {
  id?: string
  tournamentId?: string
  status?: string
  scheduledAt?: string | null
}

function kickoffMs(value?: string | null) {
  if (!value) return Number.POSITIVE_INFINITY
  const ms = new Date(value).getTime()
  return Number.isNaN(ms) ? Number.POSITIVE_INFINITY : ms
}

/**
 * Nearest match of one tournament: a live or paused match, otherwise the soonest
 * scheduled kickoff that is not before now. A finished match is not nearest.
 */
export function pickNextMatch<T extends NextMatchRow>(
  rows: T[],
  nowMs: number,
  tournamentId?: string | null,
): T | null {
  const mine = rows.filter((row) => {
    if (!row?.id) return false
    if (tournamentId && row.tournamentId !== tournamentId) return false
    return true
  })
  const inPlay = mine.filter((row) => row.status === 'LIVE' || row.status === 'PAUSED')
  const pool = inPlay.length
    ? inPlay
    : mine.filter((row) => row.status === 'SCHEDULED' && kickoffMs(row.scheduledAt) >= nowMs)
  pool.sort((a, b) => {
    const byTime = kickoffMs(a.scheduledAt) - kickoffMs(b.scheduledAt)
    if (byTime !== 0) return byTime
    return String(a.id).localeCompare(String(b.id))
  })
  return pool[0] ?? null
}
