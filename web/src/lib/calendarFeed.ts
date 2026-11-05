import { matchInstant, unplayedCardScore, type MatchDateSource } from './match.ts'
import { moscowClock, moscowWeekdayShort, moscowYmd, shiftYmd } from './moscowTime.ts'

export type CalendarMatch = MatchDateSource & {
  id: string
  tournamentId?: string | null
  tournamentName?: string | null
  homeTeamId?: string | null
  awayTeamId?: string | null
  homeTeamName?: string | null
  awayTeamName?: string | null
  homeScore?: number | null
  awayScore?: number | null
  minute?: number | null
  stageLabel?: string | null
  penaltyHome?: number | null
  penaltyAway?: number | null
}

export type CalendarFilter =
  | { kind: 'all' }
  | { kind: 'favorites' }
  | { kind: 'undated' }
  | { kind: 'tournament'; tournamentId: string }

/** Moscow calendar day, or empty when the match must not sit on a day. */
export function calendarDayKey(match?: MatchDateSource | null): string {
  const instant = matchInstant(match)
  if (!instant) return ''
  return moscowYmd(instant)
}

export function cardScoreText(match: {
  status?: string | null
  homeScore?: number | null
  awayScore?: number | null
}): string {
  const pending = unplayedCardScore(match.status)
  if (pending) return pending
  return `${Number(match.homeScore ?? 0)} : ${Number(match.awayScore ?? 0)}`
}

export function cardScoreParts(match: {
  status?: string | null
  homeScore?: number | null
  awayScore?: number | null
}): { dash: boolean; home: string; away: string } {
  const pending = unplayedCardScore(match.status)
  if (pending) return { dash: true, home: '–', away: '–' }
  return {
    dash: false,
    home: String(Number(match.homeScore ?? 0)),
    away: String(Number(match.awayScore ?? 0)),
  }
}

/** Directory name, then the name already on the match. Never invent a crest. */
export function sideName(directoryName?: string | null, apiName?: string | null): string {
  const known = (directoryName || '').trim()
  if (known && known !== 'Команда') return known
  const fromMatch = (apiName || '').trim()
  if (fromMatch) return fromMatch
  return known || 'Команда'
}

export function rowClock(match?: MatchDateSource & { minute?: number | null } | null): string {
  if (!match) return ''
  if (match.status === 'LIVE' || match.status === 'PAUSED') {
    if (match.minute != null && Number(match.minute) > 0) return `${match.minute}'`
    return match.status === 'PAUSED' ? 'Пауза' : 'Live'
  }
  const instant = matchInstant(match)
  if (!instant) return 'Дата уточняется'
  return moscowClock(instant) || 'Дата уточняется'
}

export function calendarStageLabel(
  match?: { stageLabel?: string | null } | null,
  tournament?: { competitionStage?: string | null } | null,
): string {
  const stage = String(match?.stageLabel || '').trim()
  if (stage) return stage
  if (tournament?.competitionStage === 'PLAYOFF') return 'Плей-офф'
  return 'Групповой этап'
}

export function weekKeys(anchorYmd: string): string[] {
  const match = anchorYmd.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return []
  const utc = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])))
  const jsDay = utc.getUTCDay()
  const mondayOffset = jsDay === 0 ? -6 : 1 - jsDay
  const monday = shiftYmd(anchorYmd, mondayOffset)
  if (!monday) return []
  return Array.from({ length: 7 }, (_, index) => shiftYmd(monday, index))
}

export function dayTitle(ymd: string): string {
  const match = ymd.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return ''
  const noon = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 9, 0, 0))
  const text = new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Europe/Moscow',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(noon)
  return text.charAt(0).toLocaleUpperCase('ru') + text.slice(1)
}

export function calendarCounts(matches: { tournamentId?: string | null; status?: string | null }[]) {
  const tournaments = new Set<string>()
  let live = 0
  let paused = 0
  for (const match of matches) {
    if (match.tournamentId) tournaments.add(match.tournamentId)
    if (match.status === 'LIVE') live += 1
    else if (match.status === 'PAUSED') paused += 1
  }
  return { matches: matches.length, tournaments: tournaments.size, live, paused }
}

function countWord(count: number, one: string, few: string, many: string) {
  const abs = Math.abs(count) % 100
  const last = abs % 10
  if (abs > 10 && abs < 20) return many
  if (last === 1) return one
  if (last >= 2 && last <= 4) return few
  return many
}

/** Hero line only when the loaded set is proven complete. PAUSED is not «в игре». */
export function heroMeta(counts: { matches: number; tournaments: number; live: number; paused: number }, complete: boolean): string {
  if (!complete) return ''
  const parts = [
    `${counts.matches} ${countWord(counts.matches, 'матч', 'матча', 'матчей')}`,
    `${counts.tournaments} ${countWord(counts.tournaments, 'турнир', 'турнира', 'турниров')}`,
    `${counts.live} в игре`,
  ]
  if (counts.paused > 0) parts.push(`${counts.paused} на паузе`)
  return parts.join(' · ')
}

export function applyCalendarFilter<T extends {
  id?: string | null
  tournamentId?: string | null
  homeTeamId?: string | null
  awayTeamId?: string | null
}>(
  rows: T[],
  filter: CalendarFilter,
  isFavorite: (row: T) => boolean,
): T[] {
  if (filter.kind === 'favorites') return rows.filter(isFavorite)
  if (filter.kind === 'tournament') return rows.filter((row) => row.tournamentId === filter.tournamentId)
  return rows
}

export function isFavoriteMatch(
  match: { id?: string | null; homeTeamId?: string | null; awayTeamId?: string | null },
  hasMatch: (id?: string | null) => boolean,
  hasTeam: (id?: string | null) => boolean,
) {
  return hasMatch(match.id) || hasTeam(match.homeTeamId) || hasTeam(match.awayTeamId)
}

/** Dots follow the filtered set for the whole week, not the selected day. */
export function dottedDays(matches: MatchDateSource[], keys: string[]): string[] {
  const wanted = new Set(keys)
  const found = new Set<string>()
  for (const match of matches) {
    const key = calendarDayKey(match)
    if (key && wanted.has(key)) found.add(key)
  }
  return keys.filter((key) => found.has(key))
}

export function matchesOnDay<T extends MatchDateSource & { id: string }>(
  weekRows: T[],
  nullKickoffRows: T[],
  day: string,
): T[] {
  const seen = new Set<string>()
  const out: T[] = []
  for (const match of [...weekRows, ...nullKickoffRows]) {
    if (calendarDayKey(match) !== day || seen.has(match.id)) continue
    seen.add(match.id)
    out.push(match)
  }
  return out
}

export function undatedMatches<T extends MatchDateSource>(rows: T[]): T[] {
  return rows.filter((match) => !calendarDayKey(match))
}

function statusRank(status?: string | null) {
  if (status === 'LIVE') return 0
  if (status === 'PAUSED') return 1
  return 2
}

export function sortDayMatches<T extends MatchDateSource>(rows: T[]): T[] {
  return rows.slice().sort((a, b) => {
    const byStatus = statusRank(a.status) - statusRank(b.status)
    if (byStatus) return byStatus
    return String(matchInstant(a) || '').localeCompare(String(matchInstant(b) || ''))
  })
}

export function groupByTournament<T extends MatchDateSource & { tournamentId?: string | null }>(rows: T[]): { id: string; matches: T[] }[] {
  const sorted = sortDayMatches(rows)
  const order: string[] = []
  const map = new Map<string, T[]>()
  for (const match of sorted) {
    const id = match.tournamentId || 'none'
    const list = map.get(id)
    if (list) list.push(match)
    else {
      order.push(id)
      map.set(id, [match])
    }
  }
  return order.map((id) => ({ id, matches: map.get(id) || [] }))
}

export function weekdayChip(key: string): { weekday: string; date: string } {
  const parts = key.split('-')
  return {
    weekday: moscowWeekdayShort(key),
    date: parts[2] ? String(Number(parts[2])) : '',
  }
}

export function boardLink(playoff: boolean): { label: 'Сетка' | 'Таблица'; tab: 'playoff' | 'table' } {
  if (playoff) return { label: 'Сетка', tab: 'playoff' }
  return { label: 'Таблица', tab: 'table' }
}
