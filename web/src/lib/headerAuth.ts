import type { Role } from '../stores/auth'

/** Roles that add a burger row. FAN never does. */
export type HeaderNavRole = Extract<Role, 'PLAYER' | 'CAPTAIN' | 'REFEREE' | 'ANALYST' | 'ADMIN'>

export interface HeaderLink {
  to: string
  label: string
}

/**
 * Burger rows that depend on role.
 * «Моя команда» is the roster screen, not a join or create form, so it is
 * limited to a player or captain (the only roles that can be on a team).
 * «Судья» is the referee role only: an admin opens the pad from the match.
 * «Аналитик» is the analyst role only, not canAnalyze.
 */
export function headerExtraLinks(hasRole: (role: HeaderNavRole) => boolean): HeaderLink[] {
  const links: HeaderLink[] = []
  if (hasRole('PLAYER') || hasRole('CAPTAIN')) links.push({ to: '/my-team', label: 'Моя команда' })
  if (hasRole('ADMIN')) links.push({ to: '/admin', label: 'Админ' })
  if (hasRole('REFEREE')) links.push({ to: '/referee', label: 'Судья' })
  if (hasRole('ANALYST')) links.push({ to: '/analyst', label: 'Аналитик' })
  return links
}

export type HeaderAuthSlot = 'pending' | 'account' | 'login'

/** Hide both the login pill and the avatar while the stored session is unresolved. */
export function headerAuthSlot(sessionReady: boolean, authenticated: boolean): HeaderAuthSlot {
  if (!sessionReady) return 'pending'
  return authenticated ? 'account' : 'login'
}
