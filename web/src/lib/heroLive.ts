import { matchStateLabel } from './match.ts'

/** «LIVE сейчас»: matches in play first, then paused. Order inside each group stays as given. */
export function orderHeroLive<T extends { status?: string }>(matches: readonly T[]) {
  const rank = (status?: string) => (status === 'LIVE' ? 0 : status === 'PAUSED' ? 1 : 2)
  return matches
    .map((match, index) => ({ match, index }))
    .sort((left, right) => rank(left.match.status) - rank(right.match.status) || left.index - right.index)
    .map((item) => item.match)
}

export function heroLivePreview<T extends { status?: string }>(matches: readonly T[], limit = 3) {
  return orderHeroLive(matches).slice(0, limit)
}

/** Trailing clock on a hero live row. A pause is the status word, not a running minute. */
export function heroLiveTail(match: { status?: string; minute?: number | null }) {
  if (match.status === 'PAUSED') return matchStateLabel(match.status)
  return match.minute == null ? 'Live' : `${match.minute}′`
}
