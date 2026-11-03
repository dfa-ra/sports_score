import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '../api/client'
import {
  ACCESS_STORAGE_KEY,
  REFRESH_STORAGE_KEY,
  USER_STORAGE_KEY,
  accessTokenStillValid,
  loadRefreshToken,
  loadStoredValue,
  refreshCookieAssignment,
  writeSessionValues,
} from '../lib/refreshSession'

export type Role = 'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE' | 'ANALYST' | 'ADMIN'

export interface RoleAssignment {
  role: Role
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  photoUrl?: string | null
}

export interface User {
  id: string
  email: string
  role: Role
  enabled: boolean
  firstName?: string | null
  lastName?: string | null
  photoUrl?: string | null
  roles?: RoleAssignment[]
}

const RESTORE_TIMEOUT_MS = 12_000

function browserLocal(): Storage | null {
  try {
    return localStorage
  } catch {
    return null
  }
}

function browserSession(): Storage | null {
  try {
    return sessionStorage
  } catch {
    return null
  }
}

function cookieHeader(): string {
  try {
    return typeof document === 'undefined' ? '' : document.cookie
  } catch {
    return ''
  }
}

function writeRefreshCookie(token: string | null) {
  if (typeof document === 'undefined') return
  try {
    const secure = typeof location !== 'undefined' && location.protocol === 'https:'
    document.cookie = refreshCookieAssignment(token, secure)
  } catch {
    // Cookie write can fail in locked-down webviews. localStorage is the other copy.
  }
}

function readUser(raw: string | null): User | null {
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function isUnauthorized(error: unknown): boolean {
  const status = (error as { response?: { status?: number } } | null)?.response?.status
  return status === 401
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const user = ref<User | null>(null)
  // Stays false while a stored token still has to be checked or refreshed.
  // A visitor with nothing stored is ready immediately, so «Войти» is not delayed.
  const sessionReady = ref(false)
  let refreshInFlight: Promise<void> | null = null
  let restoreInFlight: Promise<void> | null = null

  const isAuthenticated = computed(() => !!accessToken.value)
  const role = computed(() => user.value?.role ?? null)
  const approvedRoles = computed<Role[]>(() => {
    const fromAssignments = (user.value?.roles ?? [])
      .filter((item) => item.status === 'APPROVED')
      .map((item) => item.role)
    if (fromAssignments.length) return fromAssignments
    return user.value?.role ? [user.value.role] : []
  })

  function hasRole(candidate: Role) {
    if ((user.value?.roles ?? []).length) {
      return approvedRoles.value.includes(candidate)
    }
    return role.value === candidate
  }

  function persist() {
    writeSessionValues(browserLocal(), browserSession(), {
      access: accessToken.value,
      refresh: refreshToken.value,
      user: user.value ? JSON.stringify(user.value) : null,
    })
    writeRefreshCookie(refreshToken.value)
  }

  function hydrate() {
    const local = browserLocal()
    const session = browserSession()
    refreshToken.value = loadRefreshToken(local, cookieHeader(), session)
    accessToken.value = loadStoredValue(ACCESS_STORAGE_KEY, local, session)
    user.value = readUser(loadStoredValue(USER_STORAGE_KEY, local, session))
    if (accessToken.value && !accessTokenStillValid(accessToken.value)) {
      accessToken.value = null
    }
    if (!accessToken.value) user.value = null
    if (refreshToken.value || accessToken.value) persist()
  }

  function applySession(data: { accessToken: string; refreshToken: string; user: User }) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    persist()
  }

  async function register(payload: {
    email: string
    password: string
    firstName: string
    lastName: string
    role?: Role
    roles?: Role[]
    photoUrl?: string
  }) {
    await api.post('/auth/register', payload)
    return login(payload.email, payload.password)
  }

  async function login(email: string, password: string) {
    const { data } = await api.post('/auth/login', { email, password })
    applySession(data)
  }

  async function loginWithGoogle(idToken: string) {
    const { data } = await api.post('/auth/google', { idToken })
    applySession(data)
  }

  async function requestPasswordReset(email: string) {
    await api.post('/auth/forgot-password', { email })
  }

  async function requestPasswordChangeEmail() {
    await api.post('/auth/change-password-email')
  }

  async function resetPassword(token: string, password: string) {
    await api.post('/auth/reset-password', { token, password })
  }

  async function refresh() {
    if (!refreshInFlight) {
      refreshInFlight = rotateRefresh().finally(() => {
        refreshInFlight = null
      })
    }
    return refreshInFlight
  }

  async function rotateRefresh() {
    const first = loadRefreshToken(browserLocal(), cookieHeader(), browserSession()) || refreshToken.value
    if (!first) {
      throw Object.assign(new Error('No refresh token'), { response: { status: 401 } })
    }
    try {
      const { data } = await api.post('/auth/refresh', { refreshToken: first }, { timeout: RESTORE_TIMEOUT_MS })
      applySession(data)
    } catch (error) {
      if (!isUnauthorized(error)) throw error
      const newer = loadRefreshToken(browserLocal(), cookieHeader(), browserSession())
      if (!newer || newer === first) throw error
      const { data } = await api.post('/auth/refresh', { refreshToken: newer }, { timeout: RESTORE_TIMEOUT_MS })
      applySession(data)
    }
  }

  async function logout() {
    try {
      if (refreshToken.value) {
        await api.post('/auth/logout', { refreshToken: refreshToken.value })
      }
    } finally {
      logoutLocal()
    }
  }

  function logoutLocal() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    persist()
  }

  async function refreshMe() {
    if (!accessToken.value) return null
    const { data } = await api.get('/auth/me', { timeout: RESTORE_TIMEOUT_MS })
    user.value = data
    persist()
    return data
  }

  function restoreSession() {
    if (!restoreInFlight) restoreInFlight = restoreSessionOnce()
    return restoreInFlight
  }

  async function restoreSessionOnce() {
    try {
      hydrate()
      if (accessToken.value) {
        try {
          await refreshMe()
          return
        } catch (error) {
          if (!isUnauthorized(error)) return
          accessToken.value = null
        }
      }
      if (!refreshToken.value) {
        user.value = null
        persist()
        return
      }
      try {
        await refresh()
      } catch (error) {
        if (isUnauthorized(error)) logoutLocal()
      }
    } finally {
      sessionReady.value = true
    }
  }

  hydrate()
  if (!accessToken.value && !refreshToken.value) sessionReady.value = true

  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (event) => {
      let area: Storage | null = null
      try {
        area = localStorage
      } catch {
        return
      }
      if (event.storageArea !== area) return
      if (event.key === REFRESH_STORAGE_KEY) {
        refreshToken.value = event.newValue
        if (!event.newValue) {
          accessToken.value = null
          user.value = null
          writeSessionValues(null, browserSession(), { access: null, refresh: null, user: null })
          writeRefreshCookie(null)
        }
        return
      }
      if (event.key === ACCESS_STORAGE_KEY) accessToken.value = event.newValue
      if (event.key === USER_STORAGE_KEY) user.value = readUser(event.newValue)
    })
  }

  const canManageLeague = computed(() => hasRole('ADMIN'))
  const canManageTeam = computed(() => hasRole('CAPTAIN') || hasRole('ADMIN'))
  const canOfficiate = computed(() => hasRole('REFEREE') || hasRole('ADMIN'))
  const canAnalyze = computed(() => hasRole('ANALYST') || hasRole('ADMIN'))
  const canAccessMyTeam = computed(() =>
    isAuthenticated.value
    && (hasRole('PLAYER') || hasRole('CAPTAIN') || hasRole('ADMIN') || hasRole('REFEREE'))
  )

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    sessionReady,
    role,
    approvedRoles,
    hasRole,
    canManageLeague,
    canManageTeam,
    canOfficiate,
    canAnalyze,
    canAccessMyTeam,
    register,
    login,
    loginWithGoogle,
    requestPasswordReset,
    requestPasswordChangeEmail,
    resetPassword,
    refresh,
    refreshMe,
    restoreSession,
    logout,
    logoutLocal,
  }
})
