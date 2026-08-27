import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '../api/client'

export type Role = 'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE' | 'ADMIN'

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

const ACCESS_KEY = 'sl_access'
const REFRESH_KEY = 'sl_refresh'
const USER_KEY = 'sl_user'

function forgetSharedCredentials() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

function readSessionUser(): User | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  forgetSharedCredentials()

  const accessToken = ref<string | null>(sessionStorage.getItem(ACCESS_KEY))
  const refreshToken = ref<string | null>(sessionStorage.getItem(REFRESH_KEY))
  const user = ref<User | null>(readSessionUser())

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
    return approvedRoles.value.includes(candidate) || role.value === candidate
  }

  function persist() {
    if (accessToken.value) sessionStorage.setItem(ACCESS_KEY, accessToken.value)
    else sessionStorage.removeItem(ACCESS_KEY)
    if (refreshToken.value) sessionStorage.setItem(REFRESH_KEY, refreshToken.value)
    else sessionStorage.removeItem(REFRESH_KEY)
    if (user.value) sessionStorage.setItem(USER_KEY, JSON.stringify(user.value))
    else sessionStorage.removeItem(USER_KEY)
  }

  async function register(payload: {
    email: string
    password: string
    firstName: string
    lastName: string
    role: Role
    photoUrl?: string
  }) {
    await api.post('/auth/register', payload)
    return login(payload.email, payload.password)
  }

  async function login(email: string, password: string) {
    const { data } = await api.post('/auth/login', { email, password })
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    persist()
  }

  async function refresh() {
    const { data } = await api.post('/auth/refresh', { refreshToken: refreshToken.value })
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    persist()
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
    const { data } = await api.get('/auth/me')
    user.value = data
    persist()
    return data
  }

  const canManageLeague = computed(() => hasRole('ADMIN'))
  const canManageTeam = computed(() => hasRole('CAPTAIN') || hasRole('ADMIN'))
  const canOfficiate = computed(() => hasRole('REFEREE') || hasRole('ADMIN'))
  const canAccessMyTeam = computed(() =>
    isAuthenticated.value
    && (hasRole('PLAYER') || hasRole('CAPTAIN') || hasRole('ADMIN') || hasRole('REFEREE'))
  )

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    role,
    approvedRoles,
    hasRole,
    canManageLeague,
    canManageTeam,
    canOfficiate,
    canAccessMyTeam,
    register,
    login,
    refresh,
    refreshMe,
    logout,
    logoutLocal,
  }
})
