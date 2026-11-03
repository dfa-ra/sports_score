/** Query for /table. A playoff tab is kept only while that tournament still has a bracket. */
export function tableRouteQuery(options: {
  tournamentId?: string
  tab?: string
  keepPlayoff?: boolean
  listAll?: boolean
}): Record<string, string> {
  const query: Record<string, string> = {}
  if (options.tournamentId) query.tournament = options.tournamentId
  const tab = options.tab || ''
  if (tab && tab !== 'table' && (tab !== 'playoff' || options.keepPlayoff)) {
    query.tab = tab
  }
  if (options.listAll) query.list = 'all'
  return query
}
