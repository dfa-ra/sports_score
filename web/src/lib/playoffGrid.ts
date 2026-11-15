import { formatLabel, joinDateRange, joinWhenStatus } from './format.ts'
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
  bye?: boolean | null
  forfeit?: string | null
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
  return slots.filter((slot) => !isThirdPlaceSlot(slot) && !slot.bye)
}

export function thirdPlaceSlots(slots: PlayoffSlot[]): PlayoffSlot[] {
  return slots.filter((slot) => isThirdPlaceSlot(slot))
}

/** Last column keeps the final only. Earlier rounds keep every main pair. */
export function columnSlots(slots: PlayoffSlot[], lastRound: boolean): PlayoffSlot[] {
  const main = linkSlots(slots)
  return lastRound ? main.slice(0, 1) : main
}

/** Phone switcher step. The 3rd-place match is its own step, not a final tab. */
export type BracketPanel = {
  key: string
  title: string
  tab: string
  slots: PlayoffSlot[]
  roundIndex: number
  thirdPlace: boolean
  champion: boolean
}

/**
 * Public and admin rounds, with the 3rd-place match inserted after the
 * semifinals and before the final. Rounds without that slot stay unchanged.
 */
export function bracketPanels(rounds: PlayoffRound[]): BracketPanel[] {
  const panels: BracketPanel[] = []
  rounds.forEach((round, roundIndex) => {
    const slots = slotsOf(round)
    const bronze = thirdPlaceSlots(slots)
    const main = linkSlots(slots)
    const last = roundIndex === rounds.length - 1
    if (last && bronze.length > 0) {
      panels.push({
        key: `third-${round.round ?? roundIndex}`,
        title: 'Матч за 3-е место',
        tab: 'За 3-е',
        slots: bronze,
        roundIndex,
        thirdPlace: true,
        champion: false,
      })
    }
    panels.push({
      key: `round-${round.round ?? roundIndex}`,
      title: round.title || '',
      tab: segmentLabel(round.title),
      slots: main,
      roundIndex,
      thirdPlace: false,
      champion: last,
    })
  })
  return panels
}

export function defaultPanelIndex(panels: BracketPanel[]): number {
  return defaultRoundIndex(panels.map((panel) => ({ slots: panel.slots })))
}

export function teamCountOf(rounds: PlayoffRound[]): number {
  const slots = slotsOf(rounds[0]).filter((slot) => !slot.bye && !isThirdPlaceSlot(slot))
  return Math.max(1, slots.length) * 2
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
 * Minimum width of the public grid. The champion is a card above the tree,
 * so a 16-team board is a 220px opening round, three 150px rounds and three 22px links.
 */
export function gridMinWidth(roundCount: number, compact: boolean) {
  if (roundCount <= 1) return 238
  const col = compact ? 150 : 238
  const first = compact ? 220 : col
  const link = compact ? 22 : 30
  return first + (roundCount - 1) * col + (roundCount - 1) * link
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
  if (isThirdPlaceSlot(slot)) return home ? 'Проигравший 1/2 №1' : 'Проигравший 1/2 №2'
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
  if (first.month === last.month) return `${first.day}\u2013${last.day} ${MONTHS[first.month - 1]}`
  return joinDateRange(
    `${first.day} ${MONTHS[first.month - 1]}`,
    `${last.day} ${MONTHS[last.month - 1]}`,
  )
}

export function roundNote(slots: PlayoffSlot[]): { live: boolean; text: string } {
  if (slots.some((slot) => isLiveStatus(slot.matchStatus))) return { live: true, text: '' }
  const span = formatRoundSpan(slots.map((slot) => slotInstant(slot)))
  const finished = slots.length > 0 && slots.every((slot) => slot.matchStatus === 'FINISHED')
  if (finished && span) return { live: false, text: joinWhenStatus(span, 'завершён') }
  if (finished) return { live: false, text: 'завершён' }
  return { live: false, text: span }
}

export type ChampionCard = {
  teamId: string
  teamName: string
  matchId: string | null
  score: string
  penalties: string | null
}

/**
 * Champion only when the final is finished and a side is ahead on the score or on penalties.
 * A draw without penalties, a live match, and a missing team are not a champion.
 * Scores stay as returned. Null scores are not turned into 0.
 */
export function championCard(slot?: PlayoffSlot | null): ChampionCard | null {
  if (!slot) return null
  const side = winnerSide(slot)
  const teamId = advancedTeamId(slot)
  if (!side || !teamId) return null
  if (slot.homeScore == null || slot.awayScore == null) return null
  const teamName = (side === 'home' ? slot.homeTeamName : slot.awayTeamName) || ''
  const name = teamName.replace(/\s+/g, ' ').trim()
  if (!name) return null
  const drawn = slot.homeScore === slot.awayScore
  const pens = drawn && slot.penaltyHome != null && slot.penaltyAway != null
    ? `пен. ${slot.penaltyHome} : ${slot.penaltyAway}`
    : null
  return {
    teamId,
    teamName: name,
    matchId: slot.matchId || null,
    score: `${slot.homeScore} : ${slot.awayScore}`,
    penalties: pens,
  }
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
  // A lone final is one column. The phone uses a fluid track so it stays inside the viewport.
  if (roundCount <= 1) return stack ? 'minmax(0, 1fr)' : 'minmax(0, 238px)'
  // 16-team tracks prefer 214px and can shrink inside the page. The opening round stays wider.
  const col = compact ? 'minmax(150px, 214px)' : squeeze ? 'minmax(190px, 238px)' : '238px'
  const first = compact ? 'minmax(220px, 268px)' : col
  const link = compact ? '22px' : squeeze ? '24px' : '30px'
  const parts: string[] = []
  for (let i = 0; i < roundCount; i += 1) {
    if (i > 0) parts.push(link)
    parts.push(i === 0 ? first : col)
  }
  return parts.join(' ')
}

export function gridRows(firstSlots: number, compact: boolean, extraRow = false) {
  const row = compact ? '112px' : '142px'
  const tracks = `auto repeat(${Math.max(1, firstSlots)}, ${row})`
  return extraRow ? `${tracks} auto` : tracks
}

/**
 * 1/4, 1/2, final — the champion is not a column.
 * Links sit between those columns.
 */
export function bronzeBoardColumns(roundCount: number, compact: boolean, squeeze: boolean) {
  const col = compact ? 'minmax(150px, 214px)' : squeeze ? 'minmax(190px, 238px)' : '238px'
  const link = compact ? '22px' : squeeze ? '24px' : '30px'
  const parts: string[] = []
  for (let i = 0; i < roundCount; i += 1) {
    if (i > 0) parts.push(link)
    parts.push(col)
  }
  return parts.join(' ')
}

export function bronzeRoundColumn(roundIndex: number) {
  return String(roundIndex * 2 + 1)
}

/** Match rows, keeping the last one free for the 3rd-place card. */
export function bronzeMatchRows(firstSlots: number, semiCount: number) {
  return Math.max(firstSlots, semiCount + 1)
}

export function bronzeEarlyCell(slotIndex: number, slotCount: number, matchRows: number) {
  const span = Math.max(1, Math.floor(matchRows / Math.max(1, slotCount)))
  return { gridRow: `${slotIndex * span + 2} / span ${span}` }
}

/** Semifinals use every match row except the last, which holds the 3rd-place card. */
export function bronzeSemiCell(slotIndex: number, semiCount: number, matchRows: number) {
  const available = Math.max(1, matchRows - 1)
  const count = Math.max(1, semiCount)
  const base = Math.floor(available / count)
  const extra = available % count
  let start = 0
  for (let i = 0; i < slotIndex; i += 1) start += base + (i < extra ? 1 : 0)
  const span = Math.max(1, base + (slotIndex < extra ? 1 : 0))
  return { gridRow: `${start + 2} / span ${span}` }
}

export function bronzeThirdRow(matchRows: number) {
  return String(matchRows + 1)
}

/** Final on top, champion directly under it in the same column. */
export function bronzeFinalCell(matchRows: number) {
  const top = Math.max(1, Math.floor(matchRows / 2))
  return { gridRow: `2 / span ${top}` }
}

export function bronzeChampionCell(matchRows: number) {
  const top = Math.max(1, Math.floor(matchRows / 2))
  return { gridRow: `${2 + top} / span ${Math.max(1, matchRows - top)}` }
}

export function bronzeLinkCell(matchRows: number) {
  return { gridRow: `2 / span ${matchRows}` }
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

/** One connector column between two rounds, as tall as the first round's slots. */
export function connectorSpan(sourceRound: number, rowCount: number) {
  return {
    gridColumn: String(sourceRound * 2 + 2),
    gridRow: `2 / span ${Math.max(1, rowCount)}`,
  }
}

export type ConnectorEnd = {
  slotIndex: number
  /** 0–100 down the connector column. */
  y: number
  kind?: string
  id?: string | null
}

export type ConnectorSeg = {
  d: string
  kind: string
  ids: Array<string | null | undefined>
}

function linkY(y: number) {
  const clamped = Math.max(0, Math.min(100, y))
  return Math.round(clamped * 100) / 100
}

function joinKind(kinds: string[]) {
  if (kinds.length > 1 && kinds.every((kind) => kind === 'on')) return 'on'
  if (kinds.some((kind) => kind === 'pend')) return 'pend'
  if (kinds.length === 1) return kinds[0]
  return 'plain'
}

/**
 * Lines from the previous round into each next match.
 * Slot i is fed by slots i*2 and i*2+1. A missing feeder (a hidden bye) is skipped,
 * and the card that does exist still runs to the next match. The exit ends at x=100,
 * the right edge of the column, so the line meets the next card.
 */
export function bracketConnectorPaths(sources: ConnectorEnd[], targets: ConnectorEnd[]): ConnectorSeg[] {
  const byIndex = new Map(sources.map((item) => [item.slotIndex, item]))
  const segs: ConnectorSeg[] = []
  const ordered = [...targets].sort((a, b) => a.slotIndex - b.slotIndex)
  for (const target of ordered) {
    const incoming = [byIndex.get(target.slotIndex * 2), byIndex.get(target.slotIndex * 2 + 1)]
      .filter((item): item is ConnectorEnd => !!item)
    if (!incoming.length) continue
    const yTarget = linkY(target.y)
    for (const source of incoming) {
      segs.push({
        d: `M0 ${linkY(source.y)} H50 V${yTarget}`,
        kind: source.kind || 'plain',
        ids: [source.id],
      })
    }
    segs.push({
      d: `M50 ${yTarget} H100`,
      kind: joinKind(incoming.map((item) => item.kind || 'plain')),
      ids: incoming.map((item) => item.id),
    })
  }
  return segs
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
    return joinDateRange(text(from, !sameYear), text(to, true))
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
