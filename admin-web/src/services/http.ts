import axios from 'axios'
import { useSessionStore } from '../stores/session'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/admin',
  timeout: 120000,
})

http.interceptors.request.use((config) => {
  const session = useSessionStore()
  if (session.token) {
    config.headers.Authorization = `Bearer ${session.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const session = useSessionStore()
      session.clearSession()
      window.dispatchEvent(new CustomEvent('admin-session-expired'))
    }
    return Promise.reject(error)
  },
)

export function backendErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message
    if (typeof message === 'string' && message.trim().length > 0) {
      return message.trim()
    }
  }
  return fallback
}

export function isRequestTimeout(error: unknown) {
  return axios.isAxiosError(error) && (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT')
}
