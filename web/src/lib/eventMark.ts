/**
 * Public mark for one match event.
 *
 * The backend enum is GOAL, ASSIST, YELLOW_CARD, RED_CARD, FOUL, TIMEOUT,
 * SUBSTITUTION, POINT, PERIOD_START, PERIOD_END, OTHER. Own goals, a second
 * yellow, and penalties are the same rows with a flag, or a type the feed
 * already understands. An unknown type still gets a label and an icon.
 */
export type MarkSource = {
  eventType?: string | null
  metadata?: Record<string, unknown> | null
}

export type EventMark = {
  kind: string
  label: string
}

const FALLBACK: EventMark = { kind: 'other', label: 'Событие' }

const MARKS: Record<string, EventMark> = {
  GOAL: { kind: 'goal', label: 'Гол' },
  ASSIST: { kind: 'assist', label: 'Передача' },
  YELLOW_CARD: { kind: 'yellow_card', label: 'Жёлтая' },
  SECOND_YELLOW: { kind: 'second_yellow', label: 'Вторая жёлтая' },
  RED_CARD: { kind: 'red_card', label: 'Красная' },
  OWN_GOAL: { kind: 'own_goal', label: 'Автогол' },
  SUBSTITUTION: { kind: 'substitution', label: 'Замена' },
  POINT: { kind: 'point', label: 'Очко' },
  FOUL: { kind: 'foul', label: 'Фол' },
  TIMEOUT: { kind: 'timeout', label: 'Тайм-аут' },
  PERIOD_START: { kind: 'period', label: 'Начало тайма' },
  PERIOD_END: { kind: 'period', label: 'Конец тайма' },
  PENALTY_SCORED: { kind: 'penalty_scored', label: 'Пенальти' },
  PENALTY_GOAL: { kind: 'penalty_scored', label: 'Пенальти' },
  PENALTY_MISSED: { kind: 'penalty_missed', label: 'Пенальти мимо' },
  OTHER: FALLBACK,
}

function flagged(meta: Record<string, unknown> | null | undefined, key: string) {
  return meta?.[key] === true
}

export function eventMark(ev?: MarkSource | null): EventMark {
  const type = ev?.eventType || ''
  const meta = ev?.metadata
  if (type === 'OWN_GOAL' || (type === 'GOAL' && flagged(meta, 'ownGoal'))) return MARKS.OWN_GOAL
  if (type === 'PENALTY_MISSED' || flagged(meta, 'penaltyMissed')) return MARKS.PENALTY_MISSED
  if (
    type === 'PENALTY_SCORED'
    || type === 'PENALTY_GOAL'
    || (type === 'GOAL' && (flagged(meta, 'penalty') || flagged(meta, 'penaltyScored')))
  ) return MARKS.PENALTY_SCORED
  if (type === 'SECOND_YELLOW' || flagged(meta, 'secondYellow')) return MARKS.SECOND_YELLOW
  return MARKS[type] ?? FALLBACK
}

export type CardTally = {
  homeYellow: number
  awayYellow: number
  homeRed: number
  awayRed: number
}

/** Yellow and red shown on the statistics and protocol tabs. A second yellow counts as both. */
export function cardTally(
  events: Array<MarkSource & { voided?: boolean | null; teamId?: string | null }> | null | undefined,
  homeTeamId?: string | null,
): CardTally {
  const tally: CardTally = { homeYellow: 0, awayYellow: 0, homeRed: 0, awayRed: 0 }
  if (!homeTeamId || !events) return tally
  for (const ev of events) {
    if (!ev || ev.voided || !ev.teamId) continue
    const home = ev.teamId === homeTeamId
    const type = ev.eventType
    const second = type === 'SECOND_YELLOW' || flagged(ev.metadata, 'secondYellow')
    if (type === 'YELLOW_CARD' || second) {
      if (home) tally.homeYellow += 1
      else tally.awayYellow += 1
    }
    if (type === 'RED_CARD' || second) {
      if (home) tally.homeRed += 1
      else tally.awayRed += 1
    }
  }
  return tally
}
