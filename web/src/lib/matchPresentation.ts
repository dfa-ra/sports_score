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

/** Letters the form endpoint actually returned. Shorter than five stays shorter. */
export function formMarks(rows: FormRow[], teamId?: string | null): string[] {
  if (!teamId) return []
  const marks: string[] = []
  for (const row of rows) {
    const outcome = matchOutcome({
      status: row.status ?? undefined,
      homeTeamId: row.homeTeamId ?? undefined,
      awayTeamId: row.awayTeamId ?? undefined,
      homeScore: row.homeScore ?? undefined,
      awayScore: row.awayScore ?? undefined,
      penaltyHome: row.penaltyHome,
      penaltyAway: row.penaltyAway,
    }, teamId)
    const mark = outcome ? outcomeMark[outcome] : ''
    if (mark) marks.push(mark)
  }
  return marks
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
