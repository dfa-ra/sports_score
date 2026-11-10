import { formatLabel } from './format.ts'
import { civilDateParts } from './moscowTime.ts'
import { defaultRoundIndex, linkSlots, slotsOf, winnerSide, type PlayoffRound } from './playoffGrid.ts'
import { parseStandings } from './standings.ts'

export type PublicStatus = 'ACTIVE' | 'REGISTRATION' | 'FINISHED'
export type TourFilter = 'all' | 'active' | 'registration' | 'finished'

export type TourDraft = {
  id: string
  name: string
  status?: string | null
  format?: string | null
  competitionStage?: string | null
  startDate?: string | null
  endDate?: string | null
  seasonYear?: number | null
}

export type TourExtra = {
  teamsCount?: number | null
  stage?: string | null
  progress?: number | null
  roundTitle?: string | null
  liveCount?: number | null
  liveKnown: boolean
  winnerName?: string | null
  winnerTeamId?: string | null
}

export type TourRow = {
  id: string
  name: string
  status: PublicStatus
  startDate: string
  badge: string
  muted: boolean
  lead: string
  dates: string
  meta: string
  stage: string
  progress: number | null
  liveText: string
  applied: string
  winnerName: string
  winnerTeamId: string
}

function countLabel(count: number, one: string, few: string, many: string) {
  const abs = Math.abs(count) % 100
  const last = abs % 10
  if (abs > 10 && abs < 20) return many
  if (last === 1) return one
  if (last >= 2 && last <= 4) return few
  return many
}

const SHORT_FORMAT: Record<string, string> = {
  ROUND_ROBIN: 'Круговой',
  CUP: 'Плей-офф',
  GROUPS_PLAYOFF: 'Группы + плей-офф',
}

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'нояб', 'дек']

const GROUP_TITLE: Record<PublicStatus, string> = {
  ACTIVE: 'Идут сейчас',
  REGISTRATION: 'Регистрация',
  FINISHED: 'Завершены',
}

export function isPublicStatus(status?: string | null): status is PublicStatus {
  return status === 'ACTIVE' || status === 'REGISTRATION' || status === 'FINISHED'
}

export function shortFormat(format?: string | null) {
  if (!format) return ''
  return SHORT_FORMAT[format] || formatLabel[format] || ''
}

/** Drop round labels the list must not show. A real bracket title stays. */
export function safeRoundTitle(value?: string | null) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text || /тур\s*\d/i.test(text)) return ''
  return text
}

function monthLabel(month: number) {
  const name = MONTHS[month - 1] || ''
  if (!name) return ''
  return name === 'мая' ? name : `${name}.`
}

function dayMonth(value: string, withYear: boolean) {
  const parts = civilDateParts(value)
  if (!parts) return ''
  const base = `${parts.day} ${monthLabel(parts.month)}`
  return withYear ? `${base} ${parts.year}` : base
}

export function dateSpan(start?: string | null, end?: string | null, seasonYear?: number | null) {
  const from = start ? civilDateParts(start) : null
  const to = end ? civilDateParts(end) : null
  if (from && to && start && end) {
    if (from.year === to.year && from.month === to.month && from.day === to.day) return dayMonth(start, true)
    const yearsDiffer = from.year !== to.year
    return `${dayMonth(start, yearsDiffer)}\u00A0– ${dayMonth(end, yearsDiffer)}`
  }
  if (from && start) return `старт ${dayMonth(start, false)}`
  if (to && end) return dayMonth(end, true)
  if (seasonYear) return String(seasonYear)
  return ''
}

export function appliedLine(status: PublicStatus, teamsCount?: number | null) {
  if (status !== 'REGISTRATION' || typeof teamsCount !== 'number' || Number.isNaN(teamsCount)) return ''
  const count = Math.max(0, Math.trunc(teamsCount))
  return `Заявлено ${count} ${countLabel(count, 'команда', 'команды', 'команд')}`
}

export function liveText(count: number | null | undefined, known: boolean) {
  if (!known || typeof count !== 'number' || count <= 0) return ''
  const n = Math.trunc(count)
  return `${n} ${countLabel(n, 'матч', 'матча', 'матчей')} live`
}

export function countLive(matches: { status?: string | null; tournamentId?: string | null }[]) {
  const counts = new Map<string, number>()
  for (const row of matches) {
    if (row.status !== 'LIVE' || !row.tournamentId) continue
    counts.set(row.tournamentId, (counts.get(row.tournamentId) || 0) + 1)
  }
  return counts
}

export function matchProgress(
  matches: { status?: string | null; bracketRound?: number | null }[],
  playoff: boolean,
) {
  const open = matches.filter((match) => match.status !== 'CANCELLED')
  let counted = playoff ? open.filter((match) => match.bracketRound != null) : open
  if (playoff && !counted.length) counted = open
  if (!counted.length) return null
  const finished = counted.filter((match) => match.status === 'FINISHED').length
  return finished / counted.length
}

export function stageLine(input: {
  status: PublicStatus
  format?: string | null
  competitionStage?: string | null
  roundTitle?: string | null
}) {
  if (input.status !== 'ACTIVE') return ''
  const format = input.format || ''
  const stage = input.competitionStage || ''
  const playoff = stage === 'PLAYOFF' || format === 'CUP'
  if (playoff) return safeRoundTitle(input.roundTitle) || 'Плей-офф'
  if (format === 'ROUND_ROBIN') return ''
  if (stage === 'GROUPS' || format === 'GROUPS_PLAYOFF') return 'Групповой этап'
  return ''
}

function roundsOf(data: unknown): PlayoffRound[] {
  if (!data || typeof data !== 'object' || !Array.isArray((data as { rounds?: unknown }).rounds)) return []
  return (data as { rounds: PlayoffRound[] }).rounds
}

export function playoffStageTitle(data: unknown) {
  const rounds = roundsOf(data)
  if (!rounds.length) return ''
  return safeRoundTitle(rounds[defaultRoundIndex(rounds)]?.title)
}

export function playoffChampion(data: unknown): { name: string; teamId: string } | null {
  const rounds = roundsOf(data)
  if (!rounds.length) return null
  const final = linkSlots(slotsOf(rounds[rounds.length - 1]))[0]
  if (!final) return null
  const side = winnerSide(final)
  if (!side) return null
  const name = String((side === 'home' ? final.homeTeamName : final.awayTeamName) || '').trim()
  if (!name) return null
  const teamId = String((side === 'home' ? final.homeTeamId : final.awayTeamId) || '')
  return { name, teamId }
}

export function tableChampion(data: unknown): { name: string; teamId: string } | null {
  const tables = parseStandings(data).filter((table) => (table.rows?.length || 0) > 0)
  if (tables.length !== 1) return null
  const row = tables[0].rows[0]
  const name = String(row?.teamName || '').trim()
  if (!name) return null
  return { name, teamId: String(row.teamId || '') }
}

function teamWords(count: number) {
  return `${count} ${countLabel(count, 'команда', 'команды', 'команд')}`
}

export function assembleTour(raw: TourDraft, extra: TourExtra): TourRow | null {
  if (!raw.id || !isPublicStatus(raw.status)) return null
  const status = raw.status
  const stage = extra.stage || raw.competitionStage || ''
  const format = raw.format || ''
  const teamsCount = typeof extra.teamsCount === 'number' && !Number.isNaN(extra.teamsCount)
    ? Math.max(0, Math.trunc(extra.teamsCount))
    : null
  const lead = [
    shortFormat(format),
    teamsCount == null ? '' : teamWords(teamsCount),
  ].filter(Boolean).join(' · ')
  const dates = dateSpan(raw.startDate, raw.endDate, raw.seasonYear)
  const meta = [lead, dates].filter(Boolean).join(' · ')
  const progress = status === 'ACTIVE' && typeof extra.progress === 'number' && !Number.isNaN(extra.progress)
    ? Math.max(0, Math.min(1, extra.progress))
    : null
  const winnerName = status === 'FINISHED' ? String(extra.winnerName || '').trim() : ''
  return {
    id: raw.id,
    name: String(raw.name || 'Турнир').trim() || 'Турнир',
    status,
    startDate: raw.startDate || '',
    badge: status === 'ACTIVE' ? 'Идёт' : status === 'REGISTRATION' ? 'Регистрация' : 'Завершён',
    muted: status === 'FINISHED',
    lead,
    dates,
    meta,
    stage: stageLine({ status, format, competitionStage: stage, roundTitle: extra.roundTitle }),
    progress,
    liveText: liveText(extra.liveCount, extra.liveKnown),
    applied: appliedLine(status, teamsCount),
    winnerName,
    winnerTeamId: winnerName ? String(extra.winnerTeamId || '') : '',
  }
}

export function heroCounts(rows: { status: PublicStatus }[]) {
  return {
    active: rows.filter((row) => row.status === 'ACTIVE').length,
    registration: rows.filter((row) => row.status === 'REGISTRATION').length,
    total: rows.length,
  }
}

export function filterRows<T extends { status: PublicStatus }>(rows: T[], filter: TourFilter): T[] {
  if (filter === 'all') return rows
  const status: PublicStatus = filter === 'active' ? 'ACTIVE' : filter === 'registration' ? 'REGISTRATION' : 'FINISHED'
  return rows.filter((row) => row.status === status)
}

export function compareTours(a: { startDate?: string | null; name: string }, b: { startDate?: string | null; name: string }) {
  const left = a.startDate || ''
  const right = b.startDate || ''
  if (!left && right) return 1
  if (left && !right) return -1
  if (left !== right) return left < right ? 1 : -1
  return a.name.localeCompare(b.name, 'ru')
}

export function groupRows<T extends { status: PublicStatus }>(rows: T[]) {
  return (['ACTIVE', 'REGISTRATION', 'FINISHED'] as const)
    .map((status) => ({
      status,
      title: GROUP_TITLE[status],
      rows: rows.filter((row) => row.status === status),
    }))
    .filter((group) => group.rows.length > 0)
}

export function wantsPlayoff(format?: string | null, stage?: string | null) {
  return stage === 'PLAYOFF' || format === 'CUP' || format === 'DOUBLE_ELIMINATION'
}

export function pageContent(data: unknown): unknown[] {
  if (Array.isArray(data)) return data
  if (data && typeof data === 'object' && Array.isArray((data as { content?: unknown }).content)) {
    return (data as { content: unknown[] }).content
  }
  return []
}
