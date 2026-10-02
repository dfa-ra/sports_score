import api from '../api/client'

export type RefereeUser = {
  id: string
  email: string
  firstName?: string | null
  lastName?: string | null
}

const PAGE_SIZE = 500
const MAX_PAGES = 20

export function refereeTitle(user: RefereeUser) {
  const name = [user.lastName, user.firstName].filter(Boolean).join(' ')
  return name ? `${name} · ${user.email}` : user.email
}

/** Every user with the referee role. Admin screens only. Walks pages so a short page size cannot drop anyone. */
export async function loadAllReferees(query = ''): Promise<RefereeUser[]> {
  const q = query.trim()
  const all: RefereeUser[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages && page < MAX_PAGES) {
    const params: Record<string, string | number | string[]> = {
      size: PAGE_SIZE,
      page,
      sort: ['lastName,asc', 'firstName,asc', 'email,asc'],
    }
    if (q) params.q = q
    const { data } = await api.get('/admin/referees', {
      params,
      paramsSerializer: { indexes: null },
    })
    const content = (data.content ?? []) as RefereeUser[]
    all.push(...content)
    totalPages = Number(data.totalPages ?? 0)
    if (!content.length) break
    page += 1
  }
  return all
}
