/** Source-round token for an empty playoff side: «1/2», «1/4», «финала». */
export function shortSourceRound(title?: string | null) {
  const text = String(title || '').trim()
  if (/^финал$/i.test(text)) return 'финала'
  return text.replace(/\s*финала\s*$/i, '').trim()
}

/** «Победитель 1/2 #1» — source round plus that pair's index, starting at 1. */
export function winnerSlotLabel(sourceRoundTitle: string | null | undefined, feeder: number) {
  const title = shortSourceRound(sourceRoundTitle) || 'пары'
  return `Победитель ${title} #${feeder}`
}
