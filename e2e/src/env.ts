export const DEFAULT_DEV_URL = 'http://144.31.153.52:3000'
export const ENTITY_PREFIX = 'E2E '

export type RoleName = 'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE' | 'ADMIN'

export type RoleAccount = {
  role: RoleName
  email: string
  password: string
}

export function resolveBaseUrl(): string {
  const raw = (process.env.E2E_BASE_URL || DEFAULT_DEV_URL).trim()
  return raw.replace(/\/$/, '')
}

export function assertNotProduction(baseURL = resolveBaseUrl()): void {
  if (/itmoliga\.ru/i.test(baseURL)) {
    throw new Error(
      `E2E отказывается запускаться: baseURL указывает на прод (${baseURL}). Разрешён только dev, по умолчанию ${DEFAULT_DEV_URL}.`,
    )
  }
}

function readAccount(role: RoleName): RoleAccount | undefined {
  const email = process.env[`E2E_${role}_EMAIL`]?.trim()
  const password = process.env[`E2E_${role}_PASSWORD`]
  if (!email || !password) return undefined
  return { role, email, password }
}

export const accounts = {
  fan: readAccount('FAN'),
  player: readAccount('PLAYER'),
  captain: readAccount('CAPTAIN'),
  referee: readAccount('REFEREE'),
  admin: readAccount('ADMIN'),
}

export function hasRole(role: RoleName): boolean {
  return !!accounts[role.toLowerCase() as keyof typeof accounts]
}

export function missingRoleMessage(role: RoleName): string {
  return `Нужны секреты E2E_${role}_EMAIL и E2E_${role}_PASSWORD`
}

export function uniqueStamp(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

export function uniqueEmail(label: string): string {
  return `e2e-${label}-${uniqueStamp()}@example.com`
}

export function uniqueEntityName(label: string): string {
  return `${ENTITY_PREFIX}${label} ${uniqueStamp()}`
}

export function generatedPassword(): string {
  return `E2e!${uniqueStamp()}Aa1`
}
