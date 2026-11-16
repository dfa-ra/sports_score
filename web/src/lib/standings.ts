export type StandingFormMark = {
  result: string
  technical?: boolean
}

/** Four circles in the standings form column. The API may send up to five. */
export const STANDING_FORM_SLOTS = 4

export type StandingFormSlot = {
  result: string
  technical: boolean
} | null

/**
 * Last played results for the standings form.
 * The API lists the oldest on the left and the newest on the right.
 * Keep that order, keep only the last four, and pad missing matches
 * with empty slots on the left. An empty slot is not a result.
 */
export function standingFormSlots(
  form: Array<StandingFormMark | string | null | undefined> | null | undefined,
  slots = STANDING_FORM_SLOTS,
): StandingFormSlot[] {
  const played: StandingFormSlot[] = []
  for (const mark of form ?? []) {
    if (mark == null || mark === '') continue
    if (typeof mark === 'string') {
      played.push({ result: mark, technical: false })
      continue
    }
    played.push({
      result: typeof mark.result === 'string' ? mark.result : '',
      technical: mark.technical === true,
    })
  }
  const recent = played.slice(-slots)
  return [...Array.from({ length: Math.max(0, slots - recent.length) }, () => null), ...recent]
}

type NewestFirstMark =
  | StandingFormMark
  | { letter: string; technical?: boolean }
  | string
  | null
  | undefined

/**
 * Match pages list the newest result first. The form column does the opposite:
 * oldest on the left, newest on the right, still four slots.
 */
function isLetterMark(mark: object): mark is { letter: string; technical?: boolean } {
  return 'letter' in mark && typeof (mark as { letter?: unknown }).letter === 'string'
}

export function standingFormFromNewestFirst(
  form: Array<NewestFirstMark> | null | undefined,
): StandingFormSlot[] {
  const oldest: Array<StandingFormMark | string | null | undefined> = []
  for (const mark of [...(form ?? [])].reverse()) {
    if (typeof mark === 'string' || mark == null) {
      oldest.push(mark)
      continue
    }
    if (isLetterMark(mark)) {
      oldest.push({ result: mark.letter, technical: mark.technical === true })
      continue
    }
    oldest.push({
      result: typeof mark.result === 'string' ? mark.result : '',
      technical: mark.technical === true,
    })
  }
  return standingFormSlots(oldest)
}

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
  form?: StandingFormMark[]
}

export type StandingGroup = {
  id: string | null
  name: string | null
  sortOrder: number
  rows: StandingRow[]
  mutualForfeit?: boolean
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
    mutualForfeit: item?.mutualForfeit === true,
  }
}
