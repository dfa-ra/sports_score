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

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('sl_access'))
  const refreshToken = ref<string | null>(localStorage.getItem('sl_refresh'))
  const user = ref<User | null>(localStorage.getItem('sl_user') ? JSON.parse(localStorage.getItem('sl_user')!) : null)

  const isAuthenticated = computed(() => !!accessToken.value)
  const role = computed(() => user.value?.role ?? null)

  function persist() {
    if (accessToken.value) localStorage.setItem('sl_access', accessToken.value)
    else localStorage.removeItem('sl_access')
    if (refreshToken.value) localStorage.setItem('sl_refresh', refreshToken.value)
    else localStorage.removeItem('sl_refresh')
    if (user.value) localStorage.setItem('sl_user', JSON.stringify(user.value))
    else localStorage.removeItem('sl_user')
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
