const EXACT_SENTENCE = /демо-сезон,\s*чтобы таблица и статистика не были пустыми\.?/gi
const DEMO_VARIANT = /демо-сезон[^\n.]*(?:\.(?=\s|$)|(?=\n)|$)/gi

/**
 * Tournament descriptions stored before the demo blurb was removed still
 * contain it. Drop that sentence and any «Демо-сезон…» description so the
 * page does not render them. Other text stays.
 */
export function publicTournamentDescription(raw: string | null | undefined): string {
  if (raw == null) return ''
  const original = raw.trim()
  if (!original) return ''
  let text = original.replace(new RegExp(EXACT_SENTENCE.source, EXACT_SENTENCE.flags), ' ')
  if (/^демо-сезон/i.test(text.trim())) return ''
  text = text
    .replace(new RegExp(DEMO_VARIANT.source, DEMO_VARIANT.flags), ' ')
    .replace(/[^\S\n]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(/\n{2,}/g, '\n')
    .replace(/\s+([,.;:!?])/g, '$1')
    .replace(/^\s*[.,;:]+\s*/g, '')
    .trim()
  if (/^демо-сезон/i.test(text)) return ''
  return text
}
