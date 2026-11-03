export type RosterRole = 'PLAYER' | 'CAPTAIN'

export const myTeamStub = {
  title: 'Страница для игроков и капитанов',
  homeTo: '/',
  homeLabel: 'Главная',
} as const

/**
 * The roster screen. Creating a team is admin-only, and a captain or admin
 * adds an already registered player, so this page is not an apply form.
 * A signed-in user without PLAYER or CAPTAIN sees {@link myTeamStub}.
 */
export function showsMyTeamRoster(hasRole: (role: RosterRole) => boolean) {
  return hasRole('PLAYER') || hasRole('CAPTAIN')
}
