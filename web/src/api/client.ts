import axios from 'axios'
import { isUnauthorized, useAuthStore } from '../stores/auth'

/** Cold reads must fail instead of sitting on a skeleton. Uploads opt out below. */
export const READ_TIMEOUT_MS = 12_000

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: READ_TIMEOUT_MS,
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.timeout = 0
  }
  return config
})

api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const auth = useAuthStore()
    const original = error.config
    const url = String(original?.url || '')
    if (!original || url.includes('/auth/refresh') || url.includes('/auth/logout')) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401 && auth.refreshToken && !original._retry) {
      original._retry = true
      try {
        await auth.refresh()
        original.headers = original.headers || {}
        original.headers.Authorization = `Bearer ${auth.accessToken}`
        return api(original)
      } catch (refreshError) {
        if (isUnauthorized(refreshError)) auth.logoutLocal()
        return Promise.reject(isUnauthorized(refreshError) ? error : refreshError)
      }
    }
    return Promise.reject(error)
  },
)

export default api
