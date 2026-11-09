/** Tabs on /admin. The page reads `?tab=` and opens this set. */
export const ADMIN_TABS = [
  'users',
  'tournaments',
  'matches',
  'teams',
  'players',
  'referees',
  'statistics',
  'gallery',
] as const

export type AdminTab = (typeof ADMIN_TABS)[number]

/**
 * Old bookmarks. Role requests sit on the users tab. The VK album field sits
 * on the gallery tab. There is no separate screen for either.
 */
export const ADMIN_LEGACY_PATHS: Record<string, AdminTab> = {
  '/admin/users': 'users',
  '/admin/referees': 'referees',
  '/admin/role-requests': 'users',
  '/admin/gallery': 'gallery',
  '/admin/gallery/vk-album': 'gallery',
}

export function adminTabFromQuery(value: unknown): AdminTab {
  const raw = Array.isArray(value) ? value[0] : value
  const text = typeof raw === 'string' ? raw : ''
  return (ADMIN_TABS as readonly string[]).includes(text) ? (text as AdminTab) : 'users'
}

export function adminLegacyTarget(path: string): { path: '/admin'; query: { tab: AdminTab } } | null {
  const clean = path.length > 1 ? path.replace(/\/+$/, '') : path
  const tab = ADMIN_LEGACY_PATHS[clean]
  if (!tab) return null
  return { path: '/admin', query: { tab } }
}
