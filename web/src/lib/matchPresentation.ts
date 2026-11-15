import { outcomeMark } from './format.ts'
import { matchOutcome } from './match.ts'

export type FormRow = {
  id?: string
  status?: string | null
  homeTeamId?: string | null
  awayTeamId?: string | null
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
  startedAt?: string | null
  scheduledAt?: string | null
  forfeit?: string | null
}

const FORM_LIMIT = 5

function sameId(left?: string | null, right?: string | null) {
  return !!left && !!right && left.toLowerCase() === right.toLowerCase()
}

function formInstant(row: FormRow) {
  const ms = Date.parse(row.startedAt || row.scheduledAt || '')
  return Number.isFinite(ms) ? ms : 0
}

/**
 * A slower form response must not paint over a newer one, and must not paint
 * onto a different match. Null means "leave the letters already on screen".
 */
export function keepFormPayload<T>(
  ticket: number,
  latest: number,
  matchId: string,
  currentMatchId: string,
  rows: T,
): T | null {
  if (ticket !== latest || matchId !== currentMatchId) return null
  return rows
}

/**
 * Last finished matches for this team, newest first.
 * The match on screen is not part of its own form. A draw is only a finished
 * match with equal scores. Live and unplayed rows are dropped, even at 0:0.
 */
export function prepareFormRows(
  rows: FormRow[] | null | undefined,
  teamId?: string | null,
  currentMatchId?: string | null,
  limit = FORM_LIMIT,
): FormRow[] {
  if (!teamId || !Array.isArray(rows)) return []
  const mine = rows.filter((row) => {
    if (!row || row.status !== 'FINISHED') return false
    if (sameId(row.id, currentMatchId)) return false
    return sameId(row.homeTeamId, teamId) || sameId(row.awayTeamId, teamId)
  })
  mine.sort((a, b) => {
    const byTime = formInstant(b) - formInstant(a)
    if (byTime) return byTime
    return String(b.id || '').localeCompare(String(a.id || ''))
  })
  return mine.slice(0, limit)
}

/**
 * An empty list means this team has no finished matches.
 * A body that is not a list, or a list for another team, must not be painted
 * as «нет матчей».
 */
function isFormRow(row: unknown): row is FormRow {
  return !!row && typeof row === 'object'
}

export function classifyFormPayload(
  rows: unknown,
  teamId?: string | null,
  currentMatchId?: string | null,
): { ok: true; rows: FormRow[] } | { ok: false } {
  if (!teamId || !Array.isArray(rows)) return { ok: false }
  const list = rows.filter(isFormRow)
  if (rows.length === 0) return { ok: true, rows: [] }
  const belongs = list.some((row) => sameId(row.homeTeamId, teamId) || sameId(row.awayTeamId, teamId))
  if (!belongs) return { ok: false }
  return { ok: true, rows: prepareFormRows(list, teamId, currentMatchId) }
}

export type FormEntry = { letter: string; technical: boolean }

function technicalLoss(row: FormRow, teamId?: string | null) {
  if (row.forfeit === 'both') return true
  if (row.forfeit === 'home') return sameId(row.homeTeamId, teamId)
  if (row.forfeit === 'away') return sameId(row.awayTeamId, teamId)
  return false
}

/** Letters plus the ТП mark on a forfeit loss. Shorter than five stays shorter. */
export function formEntries(rows: FormRow[] | null | undefined, teamId?: string | null, currentMatchId?: string | null): FormEntry[] {
  const marks: FormEntry[] = []
  for (const row of prepareFormRows(rows, teamId, currentMatchId)) {
    const outcome = matchOutcome({
      status: row.status ?? undefined,
      homeTeamId: row.homeTeamId ?? undefined,
      awayTeamId: row.awayTeamId ?? undefined,
      homeScore: row.homeScore ?? undefined,
      awayScore: row.awayScore ?? undefined,
      penaltyHome: row.penaltyHome,
      penaltyAway: row.penaltyAway,
      forfeit: row.forfeit,
    }, teamId)
    const letter = outcome ? outcomeMark[outcome] : ''
    if (letter) marks.push({ letter, technical: letter === 'П' && technicalLoss(row, teamId) })
  }
  return marks
}

/** Letters the form endpoint actually returned. Shorter than five stays shorter. */
export function formMarks(rows: FormRow[] | null | undefined, teamId?: string | null, currentMatchId?: string | null): string[] {
  return formEntries(rows, teamId, currentMatchId).map((entry) => entry.letter)
}

export function aboutStage(
  match?: { stageLabel?: string | null } | null,
  tournament?: { competitionStage?: string | null } | null,
): string {
  const stage = String(match?.stageLabel || '').trim()
  if (stage) return stage
  if (tournament?.competitionStage === 'PLAYOFF') return 'Плей-офф'
  return 'Групповой этап'
}

export function venueLabel(venue?: string | null): string {
  const text = typeof venue === 'string' ? venue.trim() : ''
  return text || 'Место уточняется'
}

/** Referee list is names only. Email is not a public line. */
export function refereeNames(rows: { firstName?: string | null; lastName?: string | null; name?: string | null; email?: string | null }[]): string[] {
  const names: string[] = []
  for (const row of rows) {
    const legal = `${row.lastName || ''} ${row.firstName || ''}`.replace(/\s+/g, ' ').trim()
    const name = legal || (row.name || '').trim()
    if (name) names.push(name)
  }
  return names
}

export type AnalystSide = {
  shots?: number | null
  shotsOnTarget?: number | null
  saves?: number | null
  corners?: number | null
  fouls?: number | null
  freeKicks?: number | null
  kickIns?: number | null
  woodwork?: number | null
}

export type AnalystPayload = {
  home?: AnalystSide | null
  away?: AnalystSide | null
  homePossessionPercent?: number | null
  awayPossessionPercent?: number | null
}

export type AnalystRow = { label: string; hint?: string; home: string; away: string }

function intText(value: number | null | undefined) {
  return String(Number(value ?? 0))
}

/** A successful payload is numbers. Null possession is only that field. */
export function analystRows(stats: AnalystPayload): { rows: AnalystRow[]; possession: { kind: 'empty' } | { kind: 'nums'; home: number; away: number } } {
  const home = stats.home ?? {}
  const away = stats.away ?? {}
  const rows: AnalystRow[] = [
    {
      label: 'Удары',
      hint: 'в створ',
      home: `${intText(home.shots)} / ${intText(home.shotsOnTarget)}`,
      away: `${intText(away.shots)} / ${intText(away.shotsOnTarget)}`,
    },
    { label: 'Сейвы', home: intText(home.saves), away: intText(away.saves) },
    { label: 'Угловые', home: intText(home.corners), away: intText(away.corners) },
    { label: 'Фолы', home: intText(home.fouls), away: intText(away.fouls) },
    { label: 'Штрафные', home: intText(home.freeKicks), away: intText(away.freeKicks) },
    { label: 'Ауты', home: intText(home.kickIns), away: intText(away.kickIns) },
    { label: 'Каркас', home: intText(home.woodwork), away: intText(away.woodwork) },
  ]
  const homePct = stats.homePossessionPercent
  const awayPct = stats.awayPossessionPercent
  const possession = homePct == null && awayPct == null
    ? { kind: 'empty' as const }
    : { kind: 'nums' as const, home: Number(homePct ?? 0), away: Number(awayPct ?? 0) }
  return { rows, possession }
}

/** Rank highlight comes only from advancingPlaces. An empty list highlights nothing. */
export function advancingRank(index: number, places?: number[] | null): boolean {
  if (!places?.length) return false
  return places.includes(index + 1)
}
