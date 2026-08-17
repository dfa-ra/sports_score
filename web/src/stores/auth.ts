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

  async function register(email: string, password: string) {
    await api.post('/auth/register', { email, password })
    return login(email, password)
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

  return { accessToken, refreshToken, user, isAuthenticated, role, register, login, refresh, logout, logoutLocal }
})
