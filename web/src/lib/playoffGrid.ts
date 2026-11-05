import { formatLabel } from './format.ts'
import { DATE_PENDING, matchInstant } from './matchDate.ts'
import { moscowClock, moscowDayMonth, moscowParts, type InstantInput } from './moscowTime.ts'
import { isThirdPlaceSlot, shortSourceRound, winnerSlotLabel } from './playoffLabel.ts'

export type PlayoffSlot = {
  id?: string
  round?: number
  slotIndex?: number
  roundTitle?: string | null
  kind?: string | null
  matchId?: string | null
  homeTeamId?: string | null
  homeTeamName?: string | null
  homeGroupName?: string | null
  homePlace?: number | null
  awayTeamId?: string | null
  awayTeamName?: string | null
  awayGroupName?: string | null
  awayPlace?: number | null
  homeWaitingLabel?: string | null
  awayWaitingLabel?: string | null
  scheduledAt?: string | null
  startedAt?: string | null
  venue?: string | null
  matchStatus?: string | null
  homeScore?: number | null
  awayScore?: number | null
  penaltyHome?: number | null
  penaltyAway?: number | null
  minute?: number | null
}

export type PlayoffRound = {
  round?: number
  title?: string | null
  slots?: PlayoffSlot[] | null
}

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'нояб', 'дек']
const PLAYOFF_FORMATS = new Set(['CUP', 'GROUPS_PLAYOFF', 'DOUBLE_ELIMINATION', 'KNOCKOUT'])

export function slotsOf(round?: PlayoffRound | null): PlayoffSlot[] {
  return round?.slots ?? []
}

export { isThirdPlaceSlot }

/** Bracket lines follow the main pairs. The 3rd-place slot is not a feeder. */
export function linkSlots(slots: PlayoffSlot[]): PlayoffSlot[] {
  return slots.filter((slot) => !isThirdPlaceSlot(slot))
}

export function thirdPlaceSlots(slots: PlayoffSlot[]): PlayoffSlot[] {
  return slots.filter((slot) => isThirdPlaceSlot(slot))
}

/** Last column keeps the final only. Earlier rounds keep every main pair. */
export function columnSlots(slots: PlayoffSlot[], lastRound: boolean): PlayoffSlot[] {
  const main = linkSlots(slots)
  return lastRound ? main.slice(0, 1) : main
}

export function teamCountOf(rounds: PlayoffRound[]): number {
  return slotsOf(rounds[0]).length * 2
}

export function bracketIsEmpty(rounds?: PlayoffRound[] | null): boolean {
  if (!rounds || rounds.length === 0) return true
  return rounds.every((round) => slotsOf(round).every((slot) =>
    !slot.homeTeamId && !slot.awayTeamId && !slot.homeWaitingLabel && !slot.awayWaitingLabel,
  ))
}

export function roundsHaveLive(rounds?: PlayoffRound[] | null): boolean {
  return (rounds ?? []).some((round) => slotsOf(round).some((slot) => isLiveStatus(slot.matchStatus)))
}

export function isLiveStatus(status?: string | null) {
  return status === 'LIVE' || status === 'PAUSED'
}

/**
 * Minimum width of the public grid. The numbers are the min tracks in `gridColumns`:
 * a 16-team board is four 150px rounds, four 22px links and a 168px champion.
 */
export function gridMinWidth(roundCount: number, compact: boolean) {
  if (roundCount <= 1) return 238 + 160
  const col = compact ? 150 : 238
  const link = compact ? 22 : 30
  const champion = compact ? 168 : 160
  return roundCount * col + roundCount * link + champion
}

/**
 * CSS-pixel width of the page. Browser zoom and pinch zoom shrink
 * `visualViewport` (a 1280px window at 125% is about 1024) and can leave
 * `innerWidth` at the unscaled window. A scale of 1 keeps the layout width,
 * so a scrollbar does not pretend to be zoom.
 */
export function cssViewportWidth(layoutWidth: number, visualWidth?: number | null, scale?: number | null) {
  if (scale != null && scale > 1 && visualWidth != null && Number.isFinite(visualWidth) && visualWidth > 0) {
    return visualWidth
  }
  return layoutWidth
}

/**
 * Round tabs: 16 teams at a viewport of 1180px or less, and also when the bracket
 * container itself is narrower than the five-column grid (a padded page must not
 * keep those columns). 4 and 8 teams stay on the 720px viewport cutoff.
 * A final-only bracket has no switcher.
 */
export function useRoundTabs(teamCount: number, viewportWidth: number, containerWidth?: number | null) {
  if (teamCount <= 2) return false
  if (teamCount > 8) {
    if (viewportWidth <= 1180) return true
    const rounds = Math.round(Math.log2(teamCount))
    const needed = gridMinWidth(Math.max(1, rounds), true)
    return !!containerWidth && containerWidth > 0 && containerWidth < needed
  }
  return viewportWidth <= 720
}

/** Two teams are just the final. At phone width the champion sits under that match instead of beside it. */
export function stackChampion(roundCount: number, viewportWidth: number) {
  return roundCount <= 1 && viewportWidth <= 720
}

export function defaultRoundIndex(rounds: PlayoffRound[]): number {
  const live = rounds.findIndex((round) => slotsOf(round).some((slot) => isLiveStatus(slot.matchStatus)))
  if (live >= 0) return live
  const open = rounds.findIndex((round) => slotsOf(round).some((slot) =>
    slot.matchStatus !== 'FINISHED' && slot.matchStatus !== 'CANCELLED',
  ))
  if (open >= 0) return open
  return Math.max(0, rounds.length - 1)
}

export function segmentLabel(title?: string | null) {
  const text = String(title || '').trim()
  if (/^финал$/i.test(text)) return 'Финал'
  return text.replace(/\s*финала\s*$/i, '').trim() || text
}

export function winnerSide(slot: PlayoffSlot): 'home' | 'away' | null {
  if (slot.matchStatus !== 'FINISHED') return null
  if (slot.homeScore == null || slot.awayScore == null) return null
  if (slot.homeScore > slot.awayScore) return 'home'
  if (slot.awayScore > slot.homeScore) return 'away'
  if (slot.penaltyHome == null || slot.penaltyAway == null) return null
  if (slot.penaltyHome > slot.penaltyAway) return 'home'
  if (slot.penaltyAway > slot.penaltyHome) return 'away'
  return null
}

export function sideKind(slot: PlayoffSlot, home: boolean): 'win' | 'lose' | 'open' | 'empty' {
  const teamId = home ? slot.homeTeamId : slot.awayTeamId
  if (!teamId) return 'empty'
  if (isLiveStatus(slot.matchStatus)) return 'open'
  const winner = winnerSide(slot)
  if (!winner) return 'open'
  return winner === (home ? 'home' : 'away') ? 'win' : 'lose'
}

export function scoreView(slot: PlayoffSlot, home: boolean): { text: string; pens: number | null; dash: boolean } {
  const show = isLiveStatus(slot.matchStatus) || slot.matchStatus === 'FINISHED'
  if (!show) return { text: '–', pens: null, dash: true }
  const value = home ? slot.homeScore : slot.awayScore
  const drawn = slot.homeScore === slot.awayScore
  const hasPens = drawn && slot.penaltyHome != null && slot.penaltyAway != null
  const pens = home ? slot.penaltyHome : slot.penaltyAway
  return {
    text: String(value ?? 0),
    pens: hasPens ? Number(pens ?? 0) : null,
    dash: false,
  }
}

export function pairWhen(value?: InstantInput): string {
  const day = moscowDayMonth(value)
  const clock = moscowClock(value)
  if (!day || !clock) return ''
  return `${day} · ${clock}`
}

/** Planned kickoff, or the actual start once a dateless match has been played. */
export function slotInstant(slot?: PlayoffSlot | null) {
  if (!slot) return null
  return matchInstant({
    status: slot.matchStatus,
    scheduledAt: slot.scheduledAt,
    startedAt: slot.startedAt,
  })
}

export function footerView(slot: PlayoffSlot): { kind: 'live' | 'soon' | 'done' | 'open'; label: string; right: string } {
  const when = pairWhen(slotInstant(slot))
  const rightTime = when || DATE_PENDING
  if (slot.matchStatus === 'LIVE') {
    const label = slot.minute == null ? 'Live' : `Live ${slot.minute}′`
    return { kind: 'live', label, right: slot.venue || rightTime }
  }
  if (slot.matchStatus === 'PAUSED') {
    return { kind: 'live', label: 'Пауза', right: slot.venue || rightTime }
  }
  if (
    slot.matchStatus === 'FINISHED'
    && slot.homeScore === slot.awayScore
    && slot.penaltyHome != null
    && slot.penaltyAway != null
  ) {
    return { kind: 'done', label: `По пен. ${slot.penaltyHome}:${slot.penaltyAway}`, right: rightTime }
  }
  if (slot.matchStatus === 'FINISHED') return { kind: 'done', label: 'Завершён', right: rightTime }
  if (slot.matchStatus === 'CANCELLED') return { kind: 'done', label: 'Отменён', right: rightTime }
  if (!when) return { kind: 'open', label: '', right: DATE_PENDING }
  return { kind: 'soon', label: 'Скоро', right: when }
}

export function emptySideLabel(slot: PlayoffSlot, home: boolean, previousTitle?: string | null) {
  const fromApi = home ? slot.homeWaitingLabel : slot.awayWaitingLabel
  if (fromApi) return fromApi
  if (!previousTitle) return '—'
  const feeder = Number(slot.slotIndex ?? 0) * 2 + (home ? 1 : 2)
  return winnerSlotLabel(previousTitle, feeder)
}

export function sideName(slot: PlayoffSlot, home: boolean, previousTitle?: string | null) {
  if (sideKind(slot, home) === 'empty') return emptySideLabel(slot, home, previousTitle)
  return (home ? slot.homeTeamName : slot.awayTeamName) || 'Команда'
}

export function seedChip(group?: string | null, place?: number | null) {
  if (!group || !place) return ''
  const trimmed = group.trim()
  const letter = trimmed.match(/(?:^|\s)([A-Za-zА-Яа-яЁё])$/)
  const token = letter ? letter[1].toUpperCase() : trimmed
  return `${token}${place}`
}

export function pairAria(slot: PlayoffSlot, previousTitle?: string | null) {
  const footer = footerView(slot)
  const home = sideName(slot, true, previousTitle)
  const away = sideName(slot, false, previousTitle)
  const homeScore = scoreView(slot, true)
  const awayScore = scoreView(slot, false)
  const score = homeScore.dash ? 'счёт не начат' : `${homeScore.text}:${awayScore.text}`
  const note = [footer.label, footer.right].filter(Boolean).join(', ')
  return `${home} — ${away}, ${score}, ${note}`
}

export function advancedTeamId(slot?: PlayoffSlot | null): string | null {
  if (!slot) return null
  const side = winnerSide(slot)
  if (side === 'home') return slot.homeTeamId || null
  if (side === 'away') return slot.awayTeamId || null
  return null
}

export function branchKind(slot?: PlayoffSlot | null): 'on' | 'pend' | 'plain' {
  if (!slot || !slot.matchStatus || slot.matchStatus === 'SCHEDULED' || slot.matchStatus === 'CANCELLED' || isLiveStatus(slot.matchStatus)) {
    return 'pend'
  }
  if (winnerSide(slot)) return 'on'
  return 'plain'
}

type DayPoint = { key: number; day: number; month: number }

function dayPoint(value: InstantInput): DayPoint | null {
  const parts = moscowParts(value)
  if (!parts) return null
  return { key: parts.year * 10000 + parts.month * 100 + parts.day, day: parts.day, month: parts.month }
}

export function formatRoundSpan(values: InstantInput[]): string {
  const days = values.map(dayPoint).filter((day): day is DayPoint => !!day).sort((a, b) => a.key - b.key)
  if (!days.length) return ''
  const first = days[0]
  const last = days[days.length - 1]
  if (first.key === last.key) return `${first.day} ${MONTHS[first.month - 1]}`
  if (first.month === last.month) return `${first.day}–${last.day} ${MONTHS[first.month - 1]}`
  return `${first.day} ${MONTHS[first.month - 1]} – ${last.day} ${MONTHS[last.month - 1]}`
}

export function roundNote(slots: PlayoffSlot[]): { live: boolean; text: string } {
  if (slots.some((slot) => isLiveStatus(slot.matchStatus))) return { live: true, text: '' }
  const span = formatRoundSpan(slots.map((slot) => slotInstant(slot)))
  const finished = slots.length > 0 && slots.every((slot) => slot.matchStatus === 'FINISHED')
  if (finished && span) return { live: false, text: `${span} · завершён` }
  if (finished) return { live: false, text: 'завершён' }
  return { live: false, text: span }
}

export function championWhen(slot?: PlayoffSlot | null) {
  const parts = moscowParts(slotInstant(slot))
  if (!parts) return ''
  return `${parts.day} ${MONTHS[parts.month - 1]}.`
}

export function arrivalLine(feeder: PlayoffSlot | undefined, sourceTitle?: string | null) {
  if (!feeder) return null
  const winner = winnerSide(feeder)
  if (!winner) return null
  const name = winner === 'home' ? feeder.homeTeamName : feeder.awayTeamName
  const other = winner === 'home' ? feeder.awayTeamName : feeder.homeTeamName
  if (!name) return null
  let score = `${feeder.homeScore}:${feeder.awayScore}`
  if (feeder.homeScore === feeder.awayScore && feeder.penaltyHome != null && feeder.penaltyAway != null) {
    score += ` (пен. ${feeder.penaltyHome}:${feeder.penaltyAway})`
  }
  const short = shortSourceRound(sourceTitle)
  const roundBit = !short || short === 'финала' ? 'финал' : short
  return { name, detail: `${roundBit}: ${score}${other ? `, ${other}` : ''}` }
}

export function gridColumns(roundCount: number, compact: boolean, squeeze: boolean, stack = false) {
  // Side by side, 238px + a 160px champion is about 398px and scrolls a 375px phone.
  // Stacking uses one fluid column so the board stays inside the viewport.
  if (roundCount <= 1) return stack ? 'minmax(0, 1fr)' : 'minmax(0, 238px) minmax(160px, 1fr)'
  // 16-team tracks prefer 214px but can shrink so the champion column stays readable inside the 1120px page.
  const col = compact ? 'minmax(150px, 214px)' : squeeze ? 'minmax(190px, 238px)' : '238px'
  const link = compact ? '22px' : squeeze ? '24px' : '30px'
  const parts: string[] = []
  for (let i = 0; i < roundCount; i += 1) parts.push(col, link)
  parts.push(compact ? 'minmax(168px, 1fr)' : 'minmax(0, 1fr)')
  return parts.join(' ')
}

export function gridRows(firstSlots: number, compact: boolean) {
  const row = compact ? '112px' : '142px'
  return `auto repeat(${Math.max(1, firstSlots)}, ${row})`
}

export function matchCell(roundIndex: number, slotIndex: number, roundCount: number) {
  const span = 2 ** roundIndex
  const column = roundCount <= 1 ? 1 : roundIndex * 2 + 1
  return {
    gridColumn: String(column),
    gridRow: `${slotIndex * span + 2} / span ${span}`,
  }
}

export function connectorCell(sourceRound: number, slotIndex: number) {
  const span = 2 ** (sourceRound + 1)
  return {
    gridColumn: String(sourceRound * 2 + 2),
    gridRow: `${slotIndex * span + 2} / span ${span}`,
  }
}

export function championColumn(roundCount: number) {
  return roundCount <= 1 ? 2 : roundCount * 2 + 1
}

export function championHeaderCell(roundCount: number, stack: boolean) {
  if (stack) return { gridColumn: '1', gridRow: '3' }
  return { gridColumn: String(championColumn(roundCount)), gridRow: '1' }
}

export function championCell(roundCount: number, firstSlots: number, stack: boolean) {
  if (stack) return { gridColumn: '1', gridRow: '4' }
  return {
    gridColumn: String(championColumn(roundCount)),
    gridRow: `2 / span ${firstSlots}`,
  }
}

export function headerCell(roundIndex: number, roundCount: number) {
  const column = roundCount <= 1 ? 1 : roundIndex * 2 + 1
  return { gridColumn: String(column), gridRow: '1' }
}

export function tournamentHasPlayoff(input: {
  format?: string | null
  stage?: string | null
  rounds?: PlayoffRound[] | null
  hasPlayoff?: boolean | null
}) {
  if ((input.rounds?.length ?? 0) > 0) return true
  if (input.stage === 'PLAYOFF') return true
  if (input.hasPlayoff === true) return true
  if (input.hasPlayoff === false) return false
  return PLAYOFF_FORMATS.has(String(input.format || ''))
}

export function tournamentStatusChip(status?: string | null): { text: string; kind: 'ok' | 'soon' | 'done' } | null {
  if (status === 'ACTIVE') return { text: 'Идёт', kind: 'ok' }
  if (status === 'FINISHED') return { text: 'Завершён', kind: 'done' }
  if (status === 'CANCELLED') return { text: 'Отменён', kind: 'done' }
  if (status === 'DRAFT' || status === 'REGISTRATION') return { text: 'Скоро', kind: 'soon' }
  return null
}

function teamWord(count: number) {
  const mod10 = count % 10
  const mod100 = count % 100
  if (mod10 === 1 && mod100 !== 11) return 'команда'
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return 'команды'
  return 'команд'
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function civil(value?: string | null) {
  const match = String(value || '').trim().match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return null
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
}

export function tournamentDateSpan(start?: string | null, end?: string | null) {
  const from = civil(start)
  const to = civil(end)
  if (!from && !to) return ''
  const text = (day: { year: number; month: number; day: number }, withYear: boolean) =>
    withYear ? `${pad(day.day)}.${pad(day.month)}.${day.year}` : `${pad(day.day)}.${pad(day.month)}`
  if (from && to) {
    const sameYear = from.year === to.year
    return `${text(from, !sameYear)} – ${text(to, true)}`
  }
  const only = (from || to)!
  return text(only, true)
}

export function stageLabel(stage?: string | null) {
  if (stage === 'PLAYOFF') return 'плей-офф'
  if (stage === 'GROUPS') return 'группы'
  return ''
}

export function overviewMeta(input: {
  seasonYear?: number | null
  format?: string | null
  teamsCount?: number | null
  startDate?: string | null
  endDate?: string | null
  stage?: string | null
}) {
  const parts: string[] = []
  if (input.seasonYear) parts.push(`Сезон ${input.seasonYear}`)
  if (input.format) parts.push(formatLabel[input.format] || input.format)
  if (input.teamsCount != null) parts.push(`${input.teamsCount} ${teamWord(input.teamsCount)}`)
  const dates = tournamentDateSpan(input.startDate, input.endDate)
  if (dates) parts.push(dates)
  const stage = stageLabel(input.stage)
  if (stage) parts.push(`Стадия: ${stage}`)
  return parts
}
