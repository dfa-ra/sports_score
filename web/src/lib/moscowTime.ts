/**
 * Public dates and clocks are Europe/Moscow (UTC+3, no daylight saving).
 * Stored API timestamps stay UTC instants. A date-only `YYYY-MM-DD` is a
 * civil date (tournament start, birthday) and is not shifted.
 */
export const MOSCOW_TIME_ZONE = 'Europe/Moscow'
const MOSCOW_OFFSET_MINUTES = 3 * 60

export type InstantInput = string | number | Date | null | undefined

export type MoscowParts = {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  second: number
}

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/
const WALL_INPUT = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/

function pad(value: number) {
  return String(value).padStart(2, '0')
}

export function parseInstant(value: InstantInput): Date | null {
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function moscowParts(value: InstantInput): MoscowParts | null {
  const date = parseInstant(value)
  if (!date) return null
  const shifted = new Date(date.getTime() + MOSCOW_OFFSET_MINUTES * 60_000)
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
    second: shifted.getUTCSeconds(),
  }
}

/** Calendar day of an instant in Europe/Moscow, `YYYY-MM-DD`. */
export function moscowYmd(value: InstantInput): string {
  const parts = moscowParts(value)
  if (!parts) return ''
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}`
}

export function moscowClock(value: InstantInput): string {
  const parts = moscowParts(value)
  if (!parts) return ''
  return `${pad(parts.hour)}:${pad(parts.minute)}`
}

/** `dd.MM.yyyy` for an instant in Europe/Moscow. */
export function moscowDottedDate(value: InstantInput): string {
  const parts = moscowParts(value)
  if (!parts) return ''
  return `${pad(parts.day)}.${pad(parts.month)}.${parts.year}`
}

export function moscowDayMonth(value: InstantInput): string {
  const parts = moscowParts(value)
  if (!parts) return ''
  return `${pad(parts.day)}/${pad(parts.month)}`
}

export function moscowDateTimeLabel(value: InstantInput): string {
  const date = moscowDottedDate(value)
  const clock = moscowClock(value)
  if (!date || !clock) return ''
  return `${date} ${clock}`
}

export function moscowYear(value: InstantInput = new Date()): number | null {
  return moscowParts(value)?.year ?? null
}

/**
 * Date-only strings stay on the civil day that was stored.
 * Anything with a time is the Moscow calendar day of that instant.
 */
export function civilDateParts(value: InstantInput): { year: number; month: number; day: number } | null {
  if (typeof value === 'string') {
    const match = value.trim().match(DATE_ONLY)
    if (match) return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
  }
  const parts = moscowParts(value)
  if (!parts) return null
  return { year: parts.year, month: parts.month, day: parts.day }
}

export function formatCivilDate(value: InstantInput): string {
  const parts = civilDateParts(value)
  if (!parts) return ''
  return `${pad(parts.day)}.${pad(parts.month)}.${parts.year}`
}

export function civilYear(value: InstantInput): number | null {
  return civilDateParts(value)?.year ?? null
}

/** Add calendar days to a `YYYY-MM-DD` key. The key is already a Moscow day. */
export function shiftYmd(ymd: string, days: number): string {
  const match = ymd.match(DATE_ONLY)
  if (!match) return ''
  const utc = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + days)
  const date = new Date(utc)
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

/** Short Russian weekday of a Moscow calendar key, e.g. `ВТ`. */
export function moscowWeekdayShort(ymd: string): string {
  const match = ymd.match(DATE_ONLY)
  if (!match) return ''
  const noonMoscow = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 9, 0, 0)
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: MOSCOW_TIME_ZONE,
    weekday: 'short',
  }).format(new Date(noonMoscow)).replace('.', '').toUpperCase()
}

export function formatMoscowWhen(value: InstantInput): string {
  const date = parseInstant(value)
  if (!date) return ''
  return new Intl.DateTimeFormat('ru-RU', {
    timeZone: MOSCOW_TIME_ZONE,
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

/** `datetime-local` value for a UTC instant, as Moscow wall time. */
export function toMoscowInput(value: InstantInput): string {
  const parts = moscowParts(value)
  if (!parts) return ''
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)}T${pad(parts.hour)}:${pad(parts.minute)}`
}

/** Parse a `datetime-local` value as Moscow wall time and return a UTC instant. */
export function fromMoscowInput(value: string): string {
  const match = String(value).trim().match(WALL_INPUT)
  if (!match) return new Date(value).toISOString()
  const utc = Date.UTC(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4]) - MOSCOW_OFFSET_MINUTES / 60,
    Number(match[5]),
    Number(match[6] ?? 0),
  )
  return new Date(utc).toISOString()
}

export function ageOnMoscowDate(birth: InstantInput, now: InstantInput = new Date()): number | null {
  const born = civilDateParts(birth)
  const today = moscowParts(now)
  if (!born || !today) return null
  let age = today.year - born.year
  if (today.month < born.month || (today.month === born.month && today.day < born.day)) age -= 1
  return age
}
