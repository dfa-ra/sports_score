export const statusLabel: Record<string, string> = {
  SCHEDULED: 'Скоро',
  LIVE: 'Live',
  PAUSED: 'Пауза',
  FINISHED: 'Финал',
  CANCELLED: 'Отменён',
  DRAFT: 'Черновик',
  REGISTRATION: 'Набор',
  ACTIVE: 'Идёт',
  PENDING: 'На рассмотрении',
  APPROVED: 'В сетке',
  REJECTED: 'Отказ',
  WITHDRAWN: 'Снялись',
}

export const roleLabel: Record<string, string> = {
  FAN: 'Зритель',
  PLAYER: 'Игрок',
  CAPTAIN: 'Капитан',
  REFEREE: 'Судья',
  ADMIN: 'Админ',
}

export const eventLabel: Record<string, string> = {
  GOAL: 'Гол',
  ASSIST: 'Голевая',
  YELLOW_CARD: 'Жёлтая',
  RED_CARD: 'Красная',
  SUBSTITUTION: 'Замена',
  POINT: 'Очко',
  FOUL: 'Фол',
  PERIOD_START: 'Начало тайма',
  PERIOD_END: 'Конец тайма',
  OTHER: 'Событие',
}

export function labelOf(map: Record<string, string>, value?: string | null, fallback = '—') {
  if (!value) return fallback
  return map[value] ?? value
}

export function formatWhen(value?: string | number | Date | null) {
  if (!value) return 'Когда-нибудь'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Когда-нибудь'
  return date.toLocaleString('ru-RU', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatClock(totalSeconds?: number | null) {
  const safe = Math.max(0, totalSeconds ?? 0)
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export function periodNoun(sportCode?: string | null) {
  if (sportCode === 'BASKETBALL') return 'четверть'
  if (sportCode === 'VOLLEYBALL') return 'партия'
  return 'тайм'
}

export function periodLabel(period?: number | null, sportCode?: string | null, periodCount?: number | null) {
  if (!period) return 'Ещё не свистнули'
  if (periodCount && period > periodCount) return 'Доп. время'
  const noun = periodNoun(sportCode)
  if (noun === 'четверть') return `${period}-я четверть`
  if (noun === 'партия') return `${period}-я партия`
  return `${period}-й тайм`
}

export function playerTag(name?: string | null, jersey?: number | null) {
  if (!name && jersey == null) return ''
  if (jersey == null) return name || ''
  return `#${jersey} ${name || ''}`.trim()
}

export function eventDetail(ev: {
  eventType?: string
  playerName?: string | null
  playerJersey?: number | null
  secondaryPlayerName?: string | null
  secondaryPlayerJersey?: number | null
}) {
  const main = playerTag(ev.playerName, ev.playerJersey)
  const second = playerTag(ev.secondaryPlayerName, ev.secondaryPlayerJersey)
  if (ev.eventType === 'GOAL' && second) return `${main} · пас ${second}`
  if (ev.eventType === 'SUBSTITUTION' && second) return `${main} → ${second}`
  return main
}

export function initials(name?: string | null) {
  if (!name) return 'SL'
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((part) => part[0]?.toUpperCase() ?? '').join('') || 'SL'
}

export function passwordHint(password: string) {
  if (!password) return 'Минимум 8 символов. Можно без спецэффектов — главное, чтобы помнили.'
  if (password.length < 8) return 'Ещё чуть-чуть: пароль любит длину больше, чем остроумие.'
  if (password.length < 12) return 'Уже можно. Если добавите ещё пару знаков — судья кивнёт.'
  return 'Крепко. Этот пароль не забьёт автогол.'
}
