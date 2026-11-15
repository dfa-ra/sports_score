import { penaltyPair, unplayedCardScore } from './match.ts'

/** Metrics already returned on the player card. Missing is not the same as zero. */
export const PLAYER_STAT_FIELDS = [
  { key: 'appearances', label: 'Игры' },
  { key: 'goals', label: 'Голы' },
  { key: 'assists', label: 'Ассисты' },
  { key: 'yellowCards', label: 'Жёлтые' },
  { key: 'redCards', label: 'Красные' },
  { key: 'cleanSheets', label: 'Сухие' },
] as const

export function showsPlayerHistory(roles: readonly string[], hasPlayerId: boolean) {
  if (roles.includes('PLAYER') || roles.includes('CAPTAIN')) return true
  return hasPlayerId && roles.includes('ADMIN')
}

export function playerStatRows(statistics: unknown) {
  if (!statistics || typeof statistics !== 'object') return []
  const raw = statistics as Record<string, unknown>
  return PLAYER_STAT_FIELDS.flatMap((item) => {
    const value = raw[item.key]
    if (value === undefined || value === null || value === '') return []
    return [{ key: item.key, label: item.label, value }]
  })
}

function ruCount(value: number, one: string, few: string, many: string) {
  const abs = Math.abs(Math.trunc(value)) % 100
  const last = abs % 10
  if (abs > 10 && abs < 20) return many
  if (last > 1 && last < 5) return few
  if (last === 1) return one
  return many
}

/** Record from the team statistics source. An absent payload is not printed as zeros. */
export function teamRecordLine(stats: { wins?: unknown; draws?: unknown; losses?: unknown } | null | undefined) {
  if (!stats) return ''
  const wins = Number(stats.wins)
  const draws = Number(stats.draws)
  const losses = Number(stats.losses)
  if (![wins, draws, losses].every((item) => Number.isFinite(item))) return ''
  return [
    `${wins} ${ruCount(wins, 'победа', 'победы', 'побед')}`,
    `${draws} ${ruCount(draws, 'ничья', 'ничьи', 'ничьих')}`,
    `${losses} ${ruCount(losses, 'поражение', 'поражения', 'поражений')}`,
  ].join(' · ')
}

export function myTeamMode(canManage: boolean) {
  return canManage ? 'Вы капитан' : 'Вы игрок · просмотр'
}

type CalendarMatchScore = {
  status?: string | null
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
}

/**
 * Regulation score and, when the match was drawn on penalties, the shootout
 * clause. An unplayed match stays a dash, including a stored 0:0. A finished
 * 0:0 stays visible. Penalties do not change a regulation draw into a win.
 */
export function calendarScoreParts(match: CalendarMatchScore) {
  const dash = unplayedCardScore(match.status)
  if (dash) return { score: dash, penalty: null as string | null }
  const home = Number(match.homeScore ?? 0)
  const away = Number(match.awayScore ?? 0)
  const score = `${home} : ${away}`
  const pens = penaltyPair(match)
  if (!pens) return { score, penalty: null as string | null }
  return { score, penalty: `пен. ${pens.home} : ${pens.away}` }
}

/** One line for surfaces that still print the shootout beside the score. */
export function calendarScore(match: CalendarMatchScore) {
  const parts = calendarScoreParts(match)
  if (!parts.penalty) return parts.score
  return `${parts.score} · ${parts.penalty}`
}

/** A second click on the confirmed answer, or any click while one is in flight, does not send. */
export function shouldSendRsvp(current: string, next: string, pending: boolean) {
  if (pending) return false
  return current !== next
}

export type PageChunk<T> = {
  content?: T[] | null
  totalElements?: number | null
  last?: boolean | null
}

/** Follow page indexes until the reported total is in hand. Rows repeat by id are dropped. */
export async function collectPages<T extends { id?: string | null }>(
  load: (page: number) => Promise<PageChunk<T>>,
  pageSize: number,
): Promise<T[]> {
  const rows: T[] = []
  const seen = new Set<string>()
  for (let page = 0; page < 40; page += 1) {
    const data = await load(page)
    const content = Array.isArray(data.content) ? data.content : []
    for (const row of content) {
      const id = row?.id ? String(row.id) : ''
      if (id && seen.has(id)) continue
      if (id) seen.add(id)
      rows.push(row)
    }
    const total = Number(data.totalElements)
    if (data.last) break
    if (!content.length) break
    if (Number.isFinite(total) && rows.length >= total) break
    if (content.length < pageSize) break
  }
  return rows
}
