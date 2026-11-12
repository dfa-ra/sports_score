export type ForfeitMatch = {
  forfeit?: string | null
  forfeitReason?: string | null
  homeTeamId?: string | null
  awayTeamId?: string | null
  homeScore?: number | null
  awayScore?: number | null
  status?: string | null
  bracketRound?: number | null
}

export const PLAYOFF_MUTUAL_HINT =
  'В плей-офф выберите одну команду: дальше должен пройти победитель'

export const STATS_NOTE = 'Голы техпоражения не учитываются в статистике'

export const FORM_LEGEND =
  'ТП — техническое поражение. Матч считается сыгранным (колонка И). Голы 5:0 идут в разницу мячей (колонка Г), но не в статистику бомбардиров.'

export const MUTUAL_LEGEND =
  'Обоюдное ТП: счёт 0:0, обеим 0 очков, разница мячей не меняется.'

export function isForfeit(match?: { forfeit?: string | null } | null) {
  const side = match?.forfeit
  return side === 'home' || side === 'away' || side === 'both'
}

export function isPlayoffMatch(match?: { bracketRound?: number | null } | null) {
  return match?.bracketRound != null
}

/** Public caption under the losing side. «Другое» stays silent. */
export function forfeitReasonCaption(match: ForfeitMatch | null | undefined, side: 'home' | 'away') {
  if (!isForfeit(match)) return ''
  const loses = match?.forfeit === 'both' || match?.forfeit === side
  if (!loses) return ''
  if (match?.forfeitReason === 'no_show') return 'неявка'
  if (match?.forfeitReason === 'incomplete') return 'неполный состав'
  return ''
}

export function matchForfeitLine(match: ForfeitMatch | null | undefined, homeName: string, awayName: string) {
  if (!isForfeit(match)) return ''
  const reason = match?.forfeitReason
  if (match?.forfeit === 'both') {
    if (reason === 'no_show') return 'Обоюдное техническое поражение · неявка обеих команд'
    if (reason === 'incomplete') return 'Обоюдное техническое поражение · неполный состав обеих команд'
    return 'Обоюдное техническое поражение'
  }
  const loser = match?.forfeit === 'home' ? homeName : awayName
  if (reason === 'no_show') return `Техническое поражение · неявка команды ${loser}`
  if (reason === 'incomplete') return `Техническое поражение · неполный состав команды ${loser}`
  return 'Техническое поражение'
}

export function eventsEmptyCopy(match?: { forfeit?: string | null } | null) {
  if (match?.forfeit === 'both') return 'Матч не состоялся. Засчитано обоюдное техническое поражение 0:0.'
  return 'Матч не состоялся. Засчитано техническое поражение 5:0.'
}

export function awardPreview(side: 'home' | 'away' | 'both', homeName: string, awayName: string) {
  if (side === 'both') return 'Итог: 0:0 · обоим поражение, 0 очков'
  if (side === 'home') return `Итог: ${awayName} 5:0 ${homeName}`
  return `Итог: ${homeName} 5:0 ${awayName}`
}

export function adminPlate(match: ForfeitMatch | null | undefined, homeName: string, awayName: string) {
  if (!isForfeit(match)) return ''
  if (match?.forfeit === 'both') return 'Засчитано обоюдное ТП 0:0'
  const loser = match?.forfeit === 'home' ? homeName : awayName
  if (match?.forfeitReason === 'no_show') return `Засчитано ТП 5:0  ·  неявка ${loser}`
  if (match?.forfeitReason === 'incomplete') return `Засчитано ТП 5:0  ·  неполный состав ${loser}`
  return 'Засчитано ТП 5:0'
}

export function canAwardForfeit(match?: { status?: string | null; forfeit?: string | null } | null) {
  if (isForfeit(match)) return false
  return match?.status === 'SCHEDULED' || match?.status === 'FINISHED'
}
