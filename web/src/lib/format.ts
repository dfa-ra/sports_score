import { DATE_PENDING, matchInstant, type MatchDateSource } from './matchDate.ts'
import { ageOnMoscowDate, civilDateParts, formatCivilDate, formatMoscowWhen, moscowParts } from './moscowTime.ts'
import { registeredName } from './playerTitle.ts'

export const statusLabel: Record<string, string> = {
  SCHEDULED: 'Скоро',
  LIVE: 'Live',
  PAUSED: 'Пауза',
  FINISHED: 'Завершён',
  CANCELLED: 'Отменён',
  DRAFT: 'Черновик',
  REGISTRATION: 'Набор',
  ACTIVE: 'Идёт',
  PENDING: 'На рассмотрении',
  APPROVED: 'В сетке',
  REJECTED: 'Отказ',
  WITHDRAWN: 'Снялись',
}

export const sportLabel: Record<string, string> = {
  FOOTBALL: 'Футбол',
  FUTSAL: 'Футзал',
  BASKETBALL: 'Баскетбол',
  VOLLEYBALL: 'Волейбол',
  HOCKEY: 'Хоккей',
}

export const formatLabel: Record<string, string> = {
  ROUND_ROBIN: 'Круговой турнир',
  CUP: 'Кубок / плей-офф',
  GROUPS_PLAYOFF: 'Группы + плей-офф',
  SWISS: 'Швейцарская система',
  DOUBLE_ELIMINATION: 'Double elimination',
}

export function isTournamentOpenForApply(status?: string | null) {
  return status === 'REGISTRATION' || status === 'DRAFT'
}

export function tournamentDatesError(start?: string | null, end?: string | null) {
  if (!start || !end) return ''
  if (end < start) return 'Дата финиша не может быть раньше даты старта.'
  return ''
}

/**
 * One range separator for every screen: a non-breaking space, an en dash, then a space.
 * «5 нояб. – 20 дек.» The dash is U+2013, never a hyphen.
 */
export function joinDateRange(start?: string | null, end?: string | null) {
  const from = String(start ?? '').trim()
  const to = String(end ?? '').trim()
  if (!from) return to
  if (!to || from === to) return from
  return `${from}\u00A0\u2013 ${to}`
}

/** Time or date, then a middle dot, then a status. One space on each side of the dot. */
export function joinWhenStatus(when?: string | null, status?: string | null) {
  const left = String(when ?? '').trim()
  const right = String(status ?? '').trim()
  if (left && right) return `${left} \u00B7 ${right}`
  return left || right
}

export function formatDateRange(start?: string | null, end?: string | null) {
  return joinDateRange(formatIsoDate(start), formatIsoDate(end))
}

function formatIsoDate(value?: string | null) {
  return formatCivilDate(value)
}

export function labelOfSport(code?: string | null, fallback?: string | null) {
  if (!code) return fallback || 'Спорт'
  return sportLabel[code] || fallback || code
}

export const roleLabel: Record<string, string> = {
  FAN: 'Зритель',
  PLAYER: 'Игрок',
  CAPTAIN: 'Капитан',
  REFEREE: 'Судья',
  ANALYST: 'Аналитик',
  ADMIN: 'Админ',
}

export const eventLabel: Record<string, string> = {
  GOAL: 'Гол',
  ASSIST: 'Передача',
  YELLOW_CARD: 'Жёлтая',
  RED_CARD: 'Красная',
  SUBSTITUTION: 'Замена',
  POINT: 'Очко',
  FOUL: 'Фол',
  TIMEOUT: 'Тайм-аут',
  PERIOD_START: 'Начало тайма',
  PERIOD_END: 'Конец тайма',
  OTHER: 'Событие',
}

export function labelOf(map: Record<string, string>, value?: string | null, fallback = '—') {
  if (!value) return fallback
  return map[value] ?? value
}

export function formatWhen(value?: string | number | Date | null) {
  if (!value) return '—'
  return formatMoscowWhen(value) || '—'
}

/** Feed and list time. The instant comes from the shared match-date helper. */
export function matchListWhen(match?: MatchDateSource | null) {
  const instant = matchInstant(match)
  if (!instant) return DATE_PENDING
  const text = formatWhen(instant)
  return !text || text === '—' ? DATE_PENDING : text
}

export function ageLine(value?: string | number | Date | null) {
  const born = civilDateParts(value)
  const age = ageOnMoscowDate(value)
  if (!born || age == null) return ''
  const day = String(born.day).padStart(2, '0')
  const month = String(born.month).padStart(2, '0')
  return `Возр: ${age} (${day}.${month}.${born.year})`
}

export function formatMatchDay(value?: string | number | Date | null) {
  if (!value) return '—'
  const parts = moscowParts(value)
  if (!parts) return '—'
  const day = String(parts.day).padStart(2, '0')
  const month = String(parts.month).padStart(2, '0')
  const year = String(parts.year).slice(-2)
  return `${day}.${month}.${year}`
}

export const outcomeMark: Record<string, string> = {
  WIN: 'В',
  DRAW: 'Н',
  LOSS: 'П',
}

export function formatClock(totalSeconds?: number | null) {
  const safe = Math.max(0, totalSeconds ?? 0)
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export function periodNoun(sportCode?: string | null) {
  void sportCode
  return 'тайм'
}

export function periodLabel(period?: number | null, sportCode?: string | null, periodCount?: number | null) {
  if (!period) return 'Матч не начат'
  if (periodCount && period > periodCount) return 'Доп. время'
  return `${period}-й ${periodNoun(sportCode)}`
}

export function playerTag(name?: string | null, jersey?: number | null) {
  if (!name && jersey == null) return ''
  if (jersey == null) return name || ''
  return `#${jersey} ${name || ''}`.trim()
}

export function isOwnGoal(ev?: {
  eventType?: string | null
  metadata?: { ownGoal?: boolean } | null
} | null) {
  if (!ev) return false
  if (ev.eventType === 'OWN_GOAL') return true
  return ev.eventType === 'GOAL' && ev.metadata?.ownGoal === true
}

export function eventDetail(ev: {
  eventType?: string
  playerName?: string | null
  playerFirstName?: string | null
  playerLastName?: string | null
  firstName?: string | null
  lastName?: string | null
  playerJersey?: number | null
  secondaryPlayerName?: string | null
  secondaryPlayerFirstName?: string | null
  secondaryPlayerLastName?: string | null
  secondaryPlayerJersey?: number | null
  metadata?: { ownGoal?: boolean } | null
}) {
  const main = playerTag(registeredName(ev), ev.playerJersey)
  if (isOwnGoal(ev)) return main ? `${main} · Автогол` : 'Автогол'
  const second = playerTag(registeredName(ev, 'secondary'), ev.secondaryPlayerJersey)
  if (ev.eventType === 'GOAL' && second) return `${main} · передача ${second}`
  if (ev.eventType === 'SUBSTITUTION' && second) return `${main} → ${second}`
  return main
}

export function initials(name?: string | null) {
  if (!name) return 'SL'
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((part) => part[0]?.toUpperCase() ?? '').join('') || 'SL'
}

export function passwordHint(password: string) {
  if (password.length >= 8) return ''
  return 'Минимум 8 символов'
}
