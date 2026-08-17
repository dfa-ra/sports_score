import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

api.interceptors.response.use(
  (r) => r,
  async (error) => {
    const auth = useAuthStore()
    const original = error.config
    if (error.response?.status === 401 && auth.refreshToken && !original._retry) {
      original._retry = true
      try {
        await auth.refresh()
        original.headers.Authorization = `Bearer ${auth.accessToken}`
        return api(original)
      } catch {
        auth.logoutLocal()
      }
    }
    return Promise.reject(error)
  },
)

export default api
