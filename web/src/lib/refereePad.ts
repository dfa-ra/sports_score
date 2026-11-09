import { periodLabel } from './format.ts'
import { kickoffClock, kickoffDayMonth, penaltyPair, unplayedCardScore } from './match.ts'

export type RefereeListRow = {
  status?: string | null
  scheduledAt?: string | null
  sportCode?: string | null
  period?: number | null
  periodCount?: number | null
  minute?: number | null
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
}

const GROUPS: { id: string; title: string; statuses: string[] }[] = [
  { id: 'live', title: 'В игре', statuses: ['LIVE', 'PAUSED'] },
  { id: 'upcoming', title: 'Предстоящие', statuses: ['SCHEDULED'] },
  { id: 'done', title: 'Завершённые', statuses: ['FINISHED', 'CANCELLED'] },
]

/** Same order as the previous board: status buckets, then kickoff inside a bucket. */
export function refereeListGroups<T extends RefereeListRow>(rows: readonly T[]) {
  return GROUPS.map((group) => ({
    id: group.id,
    title: group.title,
    rows: rows
      .filter((row) => group.statuses.includes(String(row.status || '')))
      .slice()
      .sort((a, b) => String(a.scheduledAt || '').localeCompare(String(b.scheduledAt || ''))),
  })).filter((group) => group.rows.length > 0)
}

/** A dash until the match starts. A real 0:0 stays. Penalties are not part of this line. */
export function refereeListScore(match: RefereeListRow) {
  const dash = unplayedCardScore(match.status)
  if (dash) return dash
  return `${Number(match.homeScore ?? 0)} : ${Number(match.awayScore ?? 0)}`
}

export function refereePenaltyCaption(match: RefereeListRow) {
  const pens = penaltyPair(match)
  if (!pens) return ''
  return `Пенальти ${pens.home} : ${pens.away}`
}

/** Only fields the row already has. No invented minute or period. */
export function refereeListWhen(match: RefereeListRow) {
  if (match.status === 'LIVE' || match.status === 'PAUSED') {
    const bits: string[] = []
    if (match.period) bits.push(periodLabel(match.period, match.sportCode, match.periodCount))
    if (typeof match.minute === 'number' && match.minute > 0) bits.push(`${match.minute}'`)
    if (!bits.length) return match.status === 'PAUSED' ? 'Пауза' : 'В игре'
    return bits.join(' · ')
  }
  if (match.status === 'CANCELLED') return 'Отменён'
  if (!match.scheduledAt) return 'Дата уточняется'
  const day = kickoffDayMonth(match.scheduledAt)
  const clock = kickoffClock(match.scheduledAt)
  if (day && clock) return `${day} · ${clock}`
  return day || clock || 'Дата уточняется'
}

type StampSource = {
  eventType?: string | null
  teamId?: string | null
  playerId?: string | null
  secondaryPlayerId?: string | null
  metadata?: { ownGoal?: boolean } | null
  voided?: boolean
  id?: string | null
}

function ownGoal(row: StampSource) {
  return row.metadata?.ownGoal === true || row.eventType === 'OWN_GOAL'
}

export function eventStamp(row: StampSource) {
  const own = ownGoal(row)
  return [
    row.eventType || '',
    row.teamId || '',
    row.playerId || '',
    own ? '' : (row.secondaryPlayerId || ''),
    own ? 'own' : '',
  ].join('|')
}

/** Timeout or a dropped connection after the request left the browser. An HTTP error is a clear failure. */
export function unclearSend(error: unknown) {
  if (!error || typeof error !== 'object') return false
  const response = (error as { response?: unknown }).response
  return response == null
}

/** A protocol row that appeared after an unclear save. Same stamp that was already there is not a new save. */
export function reconciledEvent<T extends StampSource>(
  before: readonly T[],
  after: readonly T[],
  payload: StampSource,
) {
  const wanted = eventStamp(payload)
  const known = new Set(before.map((row) => (row.id ? String(row.id) : '')))
  const fresh = after.filter((row) => {
    if (row.voided) return false
    if (eventStamp(row) !== wanted) return false
    const id = row.id ? String(row.id) : ''
    return !id || !known.has(id)
  })
  return fresh.length ? fresh[fresh.length - 1] : null
}
