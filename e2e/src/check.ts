import { type TestDetails } from '@playwright/test'

export type CheckMeta = {
  id: string
  bugs?: number[]
}

export function check(meta: CheckMeta): TestDetails {
  const bugs = meta.bugs ?? []
  return {
    tag: [`@${meta.id}`, ...bugs.map((n) => `@bug-${n}`)],
    annotation: [
      { type: 'checklist', description: meta.id },
      ...bugs.map((n) => ({ type: 'bug', description: String(n) })),
    ],
  }
}

export function title(meta: CheckMeta, text: string): string {
  const bugs = meta.bugs?.length ? ` [bug ${meta.bugs.join(',')}]` : ''
  return `[${meta.id}]${bugs} ${text}`
}
