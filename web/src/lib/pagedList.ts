/** One page of a Spring `PageResponse`. Missing totals are not proof of the end. */
export type PageBody<T> = {
  content?: T[] | null
  page?: number | null
  size?: number | null
  totalElements?: number | null
  totalPages?: number | null
}

export const DEFAULT_PAGE_CAP = 30

/**
 * The server said this page is the last one.
 * `totalPages === 0` with an empty page is an empty collection.
 * A short page without `totalPages` is not treated as the end.
 */
export function pageBodyComplete(body: PageBody<unknown> | null | undefined, fetchedOnThisPage: number): boolean {
  if (!body) return false
  const totalPages = Number(body.totalPages)
  const page = Number(body.page)
  if (!Number.isFinite(totalPages) || !Number.isFinite(page)) return false
  if (totalPages <= 0) return fetchedOnThisPage === 0
  return page + 1 >= totalPages
}

function rowId(row: unknown): string {
  if (!row || typeof row !== 'object' || !('id' in row)) return ''
  const id = (row as { id?: unknown }).id
  return id == null ? '' : String(id)
}

export async function collectPaged<T>(
  load: (page: number) => Promise<PageBody<T>>,
  cap = DEFAULT_PAGE_CAP,
): Promise<{ rows: T[]; complete: boolean }> {
  const rows: T[] = []
  const seen = new Set<string>()
  for (let page = 0; page < cap; page += 1) {
    const body = await load(page)
    const batch = body?.content ?? []
    let fresh = 0
    for (const row of batch) {
      const id = rowId(row)
      if (id) {
        if (seen.has(id)) continue
        seen.add(id)
      }
      rows.push(row)
      fresh += 1
    }
    if (pageBodyComplete(body, batch.length)) return { rows, complete: true }
    if (!batch.length || fresh === 0) return { rows, complete: false }
  }
  return { rows, complete: false }
}

/** A kickoff the range filter can see. Null and blank are not a calendar day. */
export function hasScheduledKickoff(row: { scheduledAt?: string | number | Date | null }): boolean {
  return row.scheduledAt != null && String(row.scheduledAt).trim() !== ''
}

/**
 * `sort=scheduledAt,desc` puts null kickoffs first on PostgreSQL.
 * Seeing one dated kickoff means the null prefix has ended.
 * Hitting the page cap before that, and before `totalPages`, is incomplete.
 */
export function nullKickoffScanStep(input: {
  page: number
  cap: number
  body: PageBody<unknown> | null | undefined
  batch: { scheduledAt?: string | number | Date | null }[]
}): 'done-complete' | 'done-incomplete' | 'continue' {
  if (pageBodyComplete(input.body, input.batch.length)) return 'done-complete'
  if (input.batch.some(hasScheduledKickoff)) return 'done-complete'
  if (input.page + 1 >= input.cap) return 'done-incomplete'
  if (!input.batch.length) return 'done-incomplete'
  return 'continue'
}
