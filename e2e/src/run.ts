/** Один id на процесс Playwright. Можно задать E2E_RUN_ID, чтобы повторить прогон. */
export const RUN_ID = (process.env.E2E_RUN_ID || `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`).replace(/[^a-z0-9-]/gi, '')

/** Уникальный префикс этого прогона, например E2E-mabc12xyz */
export const RUN_PREFIX = `E2E-${RUN_ID}`

/** Имена, которые набор считает своими: «E2E …» (старый пул) и «E2E-…» (новый префикс). */
export function isE2EName(name: unknown): boolean {
  return /^E2E([\s-]|$)/.test(String(name || '').trim())
}

export function entityName(label: string): string {
  return `${RUN_PREFIX} ${label}`
}

export function entityShort(tag: string): string {
  const compact = `${RUN_ID.replace(/[^a-z0-9]/gi, '').slice(0, 5)}${tag}`
  return compact.slice(0, 8)
}

export function e2eEmail(label: string): boolean {
  return /^e2e[-+].+@example\.com$/i.test(String(label || ''))
}
