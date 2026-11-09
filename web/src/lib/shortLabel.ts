/**
 * Drop a shared leading phrase so a row of names stays readable.
 * «QA Толян Кубок 2» beside «QA Толян Кубок 3» becomes «Кубок 2» and «Кубок 3».
 * A name with no shared start is unchanged. The last two words stay when the
 * shared part would otherwise leave only a number.
 */
export function distinctLabels(names: readonly string[]): string[] {
  const cleaned = names.map((name) => name.trim().replace(/\s+/g, ' '))
  const parts = cleaned.map((name) => (name ? name.split(' ') : []))
  if (parts.length < 2) return cleaned
  let shared = 0
  while (parts.every((words) => words.length > shared && words[shared] === parts[0][shared])) shared += 1
  return parts.map((words, index) => {
    if (!words.length) return cleaned[index]
    const start = Math.min(shared, Math.max(0, words.length - 2))
    return words.slice(start).join(' ') || cleaned[index]
  })
}
