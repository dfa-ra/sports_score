/** Bracket slot. Not a final. */
export const THIRD_PLACE_SLOT_LABEL = 'Матч за 3-е место'

/** Short label on match cards. */
export const THIRD_PLACE_CARD_LABEL = 'Матч за 3-е'

/** Protocol and the match page. Distinct from «Финал». */
export const THIRD_PLACE_PROTOCOL_LABEL = 'За 3-е место'

export function isThirdPlaceSlot(slot?: { kind?: string | null; roundTitle?: string | null } | null) {
  if (!slot) return false
  if (slot.kind === 'THIRD') return true
  return slot.roundTitle === THIRD_PLACE_SLOT_LABEL
}

export function matchCardStage(match?: { thirdPlace?: boolean | null; stageLabel?: string | null } | null) {
  if (!match?.thirdPlace) return ''
  return THIRD_PLACE_CARD_LABEL
}

export function protocolStage(match?: { thirdPlace?: boolean | null; stageLabel?: string | null } | null) {
  if (!match) return ''
  if (match.thirdPlace) return THIRD_PLACE_PROTOCOL_LABEL
  return String(match.stageLabel || '').trim()
}

/** Source-round token for an empty playoff side: «1/2», «1/4», «финала». */
export function shortSourceRound(title?: string | null) {
  const text = String(title || '').trim()
  if (/^финал$/i.test(text)) return 'финала'
  return text.replace(/\s*финала\s*$/i, '').trim()
}

/** «Победитель 1/2 №1» — source round plus that pair's index, starting at 1. */
export function winnerSlotLabel(sourceRoundTitle: string | null | undefined, feeder: number) {
  const title = shortSourceRound(sourceRoundTitle) || 'пары'
  return `Победитель ${title} №${feeder}`
}
