import type { Role } from '../stores/auth'

/** Roles that add a burger row. FAN never does. */
export type HeaderNavRole = Extract<Role, 'PLAYER' | 'CAPTAIN' | 'REFEREE' | 'ANALYST' | 'ADMIN'>

export interface HeaderLink {
  to: string
  label: string
}

/**
 * Account-menu rows that depend on role.
 * «Моя команда» is the roster screen, so only a player or captain gets it.
 * «Пульт судьи» is the referee role only. An admin who is not a referee does not get it.
 * «Админка» is the admin role only.
 * «Аналитик» is an approved analyst role only, not an admin and not canAnalyze.
 */
export function headerExtraLinks(hasRole: (role: HeaderNavRole) => boolean): HeaderLink[] {
  const links: HeaderLink[] = []
  if (hasRole('PLAYER') || hasRole('CAPTAIN')) links.push({ to: '/my-team', label: 'Моя команда' })
  if (hasRole('ADMIN')) links.push({ to: '/admin', label: 'Админка' })
  if (hasRole('REFEREE')) links.push({ to: '/referee', label: 'Пульт судьи' })
  if (hasRole('ANALYST')) links.push({ to: '/analyst', label: 'Аналитик' })
  return links
}

/** «Профиль» plus the role rows this user actually has. «Выйти» stays a button. */
export function headerAccountLinks(hasRole: (role: HeaderNavRole) => boolean): HeaderLink[] {
  return [{ to: '/profile', label: 'Профиль' }, ...headerExtraLinks(hasRole)]
}

export type HeaderAuthSlot = 'pending' | 'account' | 'login'

/** Hide both the login pill and the avatar while the stored session is unresolved. */
export function headerAuthSlot(sessionReady: boolean, authenticated: boolean): HeaderAuthSlot {
  if (!sessionReady) return 'pending'
  return authenticated ? 'account' : 'login'
}
