import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import api from '../api/client'

export type Role = 'FAN' | 'PLAYER' | 'CAPTAIN' | 'REFEREE' | 'ADMIN'

export interface User {
  id: string
  email: string
  role: Role
  enabled: boolean
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
    accountType: 'FAN' | 'PLAYER'
    firstName?: string
    lastName?: string
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

  const canManageLeague = computed(() => role.value === 'ADMIN')
  const canManageTeam = computed(() => role.value === 'CAPTAIN' || role.value === 'ADMIN')
  const canOfficiate = computed(() => role.value === 'REFEREE' || role.value === 'ADMIN')

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    role,
    canManageLeague,
    canManageTeam,
    canOfficiate,
    register,
    login,
    refresh,
    refreshMe,
    logout,
    logoutLocal,
  }
})
