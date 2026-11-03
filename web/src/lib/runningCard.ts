export function isInPlayStatus(status?: string | null) {
  return status === 'LIVE' || status === 'PAUSED'
}

/**
 * Match rows on a running-tournament card.
 * The hero reads `live`, which keeps LIVE and PAUSED even when scheduledAt is
 * null or already past. The card fields do not: lastMatch is finished only,
 * and nextMatch is a future kickoff. A live match dropped by that status and
 * date filter still has to show here, from the same live list the hero uses.
 * With nothing in play, the card keeps the last result and the nearest upcoming
 * match. The empty line is only when the tournament has none of those.
 */
export function runningCardMatches<T extends { id?: string; tournamentId?: string; status?: string }>(
  tournament: { id: string; lastMatch?: T | null; nextMatch?: T | null },
  live: T[],
): T[] {
  const inPlay = live.filter((match) =>
    !!match.id && match.tournamentId === tournament.id && isInPlayStatus(match.status),
  )
  if (inPlay.length) return inPlay
  const rows: T[] = []
  if (tournament.lastMatch?.id) rows.push(tournament.lastMatch)
  if (tournament.nextMatch?.id && tournament.nextMatch.id !== tournament.lastMatch?.id) rows.push(tournament.nextMatch)
  return rows
}
