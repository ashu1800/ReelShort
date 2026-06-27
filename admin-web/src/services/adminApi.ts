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

export type AdminUserDetail = AdminUserSummary & {
  watchRecordCount: number
  pointRecordCount: number
}

export type WatchRecord = {
  id: string
  bookId: string
  bookTitle: string
  filteredTitle: string
  episodeNum: number
  chapterId: string
  positionSeconds: number
  durationSeconds: number
  progressPercent: number
  awardedStages: number[]
  awardedPoints: number
  updatedAt: string
}

export type PointRecord = {
  id: string
  amount: number
  balanceAfter: number
  source: string
  bookId: string | null
  episodeNum: number | null
  stage: number | null
  reason: string | null
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

export type SystemConfig = {
  key: string
  value: string
  description: string
  updatedAt: string | null
}

export type RechargeOrderStatus = 'CREATED' | 'PAID' | 'CANCELLED' | 'FAILED' | 'REFUNDED'

export type RechargeOrder = {
  id: string
  userId: string
  orderNo: string
  amountCents: number
  pointAmount: number
  status: RechargeOrderStatus
  paymentChannel: string | null
  createdAt: string
  updatedAt: string
}

export type PaymentEventStatus = 'PROCESSED' | 'REJECTED'

export type PaymentEvent = {
  providerEventId: string
  orderNo: string
  paymentChannel: string
  amountCents: number
  status: PaymentEventStatus
  failureReason: string | null
  createdAt: string
  processedAt: string
}

export type PaymentEventFilters = {
  status?: PaymentEventStatus
  orderNo?: string
  paymentChannel?: string
}

export type AdminDashboardSummary = {
  users: {
    total: number
    disabled: number
  }
  orders: {
    total: number
    created: number
    paid: number
    totalAmountCents: number
  }
  payments: {
    total: number
    processed: number
    rejected: number
  }
  content: {
    bookCount: number
    episodeCacheCount: number
    shelfCount: number
  }
  auditLogs: {
    latest: AdminAuditLog[]
  }
}

export async function login(username: string, password: string) {
  const response = await http.post<ApiResponse<AdminLoginResponse>>('/auth/login', {
    username,
    password,
  })
  return response.data.data
}

export async function logout() {
  const response = await http.post<ApiResponse<string>>('/auth/logout')
  return response.data.data
}

export async function fetchUsers() {
  const response = await http.get<ApiResponse<AdminUserSummary[]>>('/users')
  return response.data.data
}

export async function fetchDashboardSummary() {
  const response = await http.get<ApiResponse<AdminDashboardSummary>>('/dashboard/summary')
  return response.data.data
}

export async function fetchUserDetail(userId: string) {
  const response = await http.get<ApiResponse<AdminUserDetail>>(`/users/${userId}`)
  return response.data.data
}

export async function updateUserStatus(userId: string, status: AdminUserSummary['status']) {
  const response = await http.post<ApiResponse<AdminUserDetail>>(`/users/${userId}/status`, {
    status,
  })
  return response.data.data
}

export async function adjustUserPoints(userId: string, amount: number, reason: string) {
  const response = await http.post<ApiResponse<AdminUserDetail>>(`/users/${userId}/points/adjust`, {
    amount,
    reason,
  })
  return response.data.data
}

export async function fetchUserWatchRecords(userId: string) {
  const response = await http.get<ApiResponse<WatchRecord[]>>(`/users/${userId}/watch-records`)
  return response.data.data
}

export async function fetchUserPointRecords(userId: string) {
  const response = await http.get<ApiResponse<PointRecord[]>>(`/users/${userId}/point-records`)
  return response.data.data
}

export async function fetchContentCacheStatus() {
  const response = await http.get<ApiResponse<ContentCacheStatus>>('/content/cache')
  return response.data.data
}

export async function refreshContentShelf(shelfType: string) {
  const response = await http.post<ApiResponse<unknown[]>>(`/content/cache/shelves/${shelfType}/refresh`)
  return response.data.data
}

export async function fetchAuditLogs() {
  const response = await http.get<ApiResponse<AdminAuditLog[]>>('/audit-logs')
  return response.data.data
}

export async function fetchSystemConfigs() {
  const response = await http.get<ApiResponse<SystemConfig[]>>('/system/configs')
  return response.data.data
}

export async function updateSystemConfig(configKey: string, value: string) {
  const response = await http.post<ApiResponse<SystemConfig>>(
    `/system/configs/${configKey}`,
    { value },
  )
  return response.data.data
}

export async function fetchOrders() {
  const response = await http.get<ApiResponse<RechargeOrder[]>>('/orders')
  return response.data.data
}

export async function fetchPaymentEvents(filters: PaymentEventFilters = {}) {
  const response = await http.get<ApiResponse<PaymentEvent[]>>('/payments/events', {
    params: filters,
  })
  return response.data.data
}
