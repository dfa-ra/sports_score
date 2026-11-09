import { pageContent } from './q2Tournaments.ts'

/**
 * Public team list: GET /api/v1/teams.
 * - `q` is an optional case-insensitive substring of `name` (not shortName).
 *   The server trims `q` and does not collapse inner whitespace.
 * - `page` is 0-based. `size` defaults to 20. Spring `max-page-size` is 500.
 *   Unlike GET /players, this endpoint has no 12-row cap.
 * - `sort` defaults to `name,asc`. `includeDisbanded` defaults to false.
 * - `totalElements` is the full match count, `page` in the body is 0-based.
 */
export const TEAM_PAGE_SIZE = 20

export type TeamRow = {
  id: string
  name: string
  shortName: string | null
  logoUrl: string | null
}

export type TeamListState = {
  items: TeamRow[]
  page: number
  total: number | null
  status: 'loading' | 'ready' | 'empty' | 'error'
  moreError: boolean
  pendingMore: boolean
  query: string
  lastCount: number
}

export function initialTeamList(): TeamListState {
  return {
    items: [],
    page: 0,
    total: null,
    status: 'loading',
    moreError: false,
    pendingMore: false,
    query: '',
    lastCount: 0,
  }
}

/** Trim and collapse inner whitespace so spaces do not hide a real name. */
export function teamSearchQuery(raw: string): string {
  return raw.replace(/\s+/g, ' ').trim()
}

export function teamListParams(query: string, page: number): Record<string, string | number> {
  const params: Record<string, string | number> = {
    page: Math.max(0, page),
    size: TEAM_PAGE_SIZE,
    sort: 'name,asc',
  }
  const q = teamSearchQuery(query)
  if (q) params.q = q
  return params
}

export function readTeamPage(data: unknown): { rows: TeamRow[]; total: number | null } {
  const rows: TeamRow[] = []
  for (const row of pageContent(data)) {
    if (!row || typeof row !== 'object') continue
    const item = row as { id?: unknown; name?: unknown; shortName?: unknown; logoUrl?: unknown }
    const id = typeof item.id === 'string' ? item.id : ''
    const name = typeof item.name === 'string' ? item.name : ''
    if (!id || !name) continue
    rows.push({
      id,
      name,
      shortName: typeof item.shortName === 'string' ? item.shortName : null,
      logoUrl: typeof item.logoUrl === 'string' ? item.logoUrl : null,
    })
  }
  let total: number | null = null
  if (data && typeof data === 'object' && 'totalElements' in data) {
    const value = Number((data as { totalElements?: unknown }).totalElements)
    if (Number.isFinite(value)) total = value
  }
  return { rows, total }
}

function dedupe(rows: TeamRow[]): TeamRow[] {
  const seen = new Set<string>()
  const next: TeamRow[] = []
  for (const row of rows) {
    if (seen.has(row.id)) continue
    seen.add(row.id)
    next.push(row)
  }
  return next
}

export function appendTeams(current: TeamRow[], incoming: TeamRow[]): TeamRow[] {
  return dedupe([...current, ...incoming])
}

export type TeamListEvent =
  | { type: 'start'; query: string; more: boolean }
  | { type: 'success'; query: string; page: number; rows: TeamRow[]; total: number | null }
  | { type: 'failure'; query: string; more: boolean }

export function reduceTeamList(state: TeamListState, event: TeamListEvent): TeamListState {
  const query = teamSearchQuery(event.query)
  if (event.type === 'start') {
    if (event.more) {
      return { ...state, query, moreError: false, pendingMore: true }
    }
    return {
      ...state,
      query,
      page: 0,
      items: [],
      total: null,
      lastCount: 0,
      status: 'loading',
      moreError: false,
      pendingMore: false,
    }
  }
  if (query !== state.query) return state
  if (event.type === 'failure') {
    if (event.more) return { ...state, pendingMore: false, moreError: true }
    return {
      ...state,
      items: [],
      page: 0,
      total: null,
      lastCount: 0,
      status: 'error',
      moreError: false,
      pendingMore: false,
    }
  }
  const replace = event.page === 0
  const items = replace ? dedupe(event.rows) : appendTeams(state.items, event.rows)
  const exhausted = !replace && event.rows.length === 0
  const total = exhausted ? items.length : event.total
  return {
    ...state,
    items,
    page: event.page,
    total,
    lastCount: event.rows.length,
    pendingMore: false,
    moreError: false,
    status: items.length ? 'ready' : 'empty',
  }
}

export function canShowMore(state: TeamListState): boolean {
  if (state.pendingMore || state.moreError || state.status === 'loading' || state.status === 'error') return false
  if (!state.items.length) return false
  if (state.total != null && Number.isFinite(state.total)) return state.items.length < state.total
  return state.lastCount >= TEAM_PAGE_SIZE
}

export function nextTeamPage(state: TeamListState, more: boolean): number {
  return more ? state.page + 1 : 0
}
