export type StandingRow = {
  teamId: string
  teamName: string
  played: number
  wins: number
  draws: number
  losses: number
  goalsFor: number
  goalsAgainst: number
  points: number
}

export type StandingGroup = {
  id: string | null
  name: string | null
  sortOrder: number
  rows: StandingRow[]
}

export function parseStandings(data: unknown): StandingGroup[] {
  if (Array.isArray(data)) {
    if (data.length && data[0] && typeof data[0] === 'object' && 'rows' in data[0]) {
      return (data as any[]).map(asGroup)
    }
    return [{ id: null, name: null, sortOrder: 0, rows: data as StandingRow[] }]
  }
  if (data && typeof data === 'object' && Array.isArray((data as any).tables)) {
    return ((data as any).tables as any[]).map(asGroup)
  }
  return []
}

export function standingGroupsHaveRows(tables: StandingGroup[]) {
  return tables.some((table) => (table.rows?.length || 0) > 0)
}

function asGroup(item: any): StandingGroup {
  return {
    id: item?.id ?? null,
    name: item?.name ?? null,
    sortOrder: Number(item?.sortOrder || 0),
    rows: Array.isArray(item?.rows) ? item.rows : [],
  }
}
