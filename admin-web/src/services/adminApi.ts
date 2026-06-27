import { http } from './http'

export type ApiResponse<T> = {
  code: number
  message: string
  data: T
  requestId: string
  timestamp: string
}

export type AdminLoginResponse = {
  username: string
  token: string
  tokenType: 'Bearer'
}

export type AdminUserSummary = {
  id: string
  username: string
  status: 'ACTIVE' | 'DISABLED'
  pointBalance: number
  createdAt: string
}

export type ContentCacheStatus = {
  bookCount: number
  episodeCacheCount: number
  shelves: Array<{
    shelfType: string
    itemCount: number
    refreshedAt: string | null
    lastError: string | null
  }>
}

export type AdminAuditLog = {
  id: string
  adminUsername: string
  action: string
  targetType: string
  targetId: string
  summary: string
  createdAt: string
}

export async function login(username: string, password: string) {
  const response = await http.post<ApiResponse<AdminLoginResponse>>('/auth/login', {
    username,
    password,
  })
  return response.data.data
}

export async function fetchUsers() {
  const response = await http.get<ApiResponse<AdminUserSummary[]>>('/users')
  return response.data.data
}

export async function fetchContentCacheStatus() {
  const response = await http.get<ApiResponse<ContentCacheStatus>>('/content/cache')
  return response.data.data
}

export async function fetchAuditLogs() {
  const response = await http.get<ApiResponse<AdminAuditLog[]>>('/audit-logs')
  return response.data.data
}
