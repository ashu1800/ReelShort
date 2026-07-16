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

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'BLACKLISTED'

export type AdminUserSummary = {
  id: string
  username: string
  phoneE164: string | null
  status: UserStatus
  pointBalance: number
  frozenPoints: number
  availablePoints: number
  createdAt: string
}

export type AdminUserDetail = AdminUserSummary & {
  phoneCountryCode: string | null
  phoneNumber: string | null
  walletNetwork: string | null
  walletAddress: string | null
  walletUpdatedAt: string | null
  watchRecordCount: number
  pointRecordCount: number
  withdrawalRecordCount: number
  pointTransferRecordCount: number
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

export type PointTransfer = {
  id: string
  direction: 'IN' | 'OUT'
  senderAccount: string
  recipientAccount: string
  pointAmount: number
  createdAt: string
}

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type WithdrawalRequest = {
  id: string
  userId: string
  userAccount: string | null
  pointAmount: number
  usdtAmount: string
  usdtPerPoint: string
  cnyPerPoint: string | null
  cnyPerUsd: string | null
  minimumUsd: string | null
  network: string
  walletAddress: string
  status: WithdrawalStatus
  txHash: string | null
  adminNote: string | null
  createdAt: string
  reviewedAt: string | null
}

export type ContentCacheStatus = {
  bookCount: number
  episodeCacheCount: number
  videoCacheCount: number
  shelves: Array<{
    shelfType: string
    locale: string
    itemCount: number
    refreshedAt: string | null
    lastError: string | null
    health: 'HEALTHY' | 'STALE' | 'EMPTY' | 'ERROR' | 'MISSING'
    healthMessage: string
  }>
  recentRefreshRuns: Array<{
    triggerSource: 'ADMIN' | 'SCHEDULED'
    shelfType: string
    locale: string
    status: 'SUCCESS' | 'FAILED'
    startedAt: string
    finishedAt: string
    durationMillis: number
    itemCount: number
    errorMessage: string | null
  }>
}

export type ContentShelfRefreshResult = {
  shelfType: string
  locale: string
  status: 'SUCCESS' | 'FAILED'
  itemCount: number
  errorMessage: string | null
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

export type VipOrderStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'

export type VipOrder = {
  id: string
  userId: string
  orderNo: string
  usdtAmount: string
  txHash: string | null
  status: VipOrderStatus
  paymentMethod: string
  confirmedBy: string | null
  createdAt: string
  confirmedAt: string | null
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

export type RuntimeDependencyStatus = {
  name: string
  status: 'UP' | 'DOWN'
  detail: string
}

export type ContentProviderDiagnosticEvent = {
  eventType: string
  observedAt: string
  context: Record<string, string>
}

export type ContentProviderDiagnostics = {
  totalEvents: number
  counters: Record<string, number>
  recentEvents: ContentProviderDiagnosticEvent[]
}

export type SystemRuntimeResponse = {
  status: 'UP' | 'DEGRADED'
  checkedAt: string
  application: {
    service: string
    version: string
    javaVersion: string
    uptimeSeconds: number
  }
  memory: {
    usedBytes: number
    maxBytes: number
  }
  dependencies: RuntimeDependencyStatus[]
  contentProviderDiagnostics: ContentProviderDiagnostics | null
}

export type SystemLogResponse = {
  files: string[]
  selectedFile: string | null
  requestedLines: number
  lineCount: number
  truncated: boolean
  updatedAt: string | null
  lines: string[]
}

export type SystemAlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
export type SystemAlertSeverity = 'WARNING' | 'CRITICAL'

export type SystemAlert = {
  id: string
  alertKey: string
  severity: SystemAlertSeverity
  status: SystemAlertStatus
  title: string
  detail: string
  firstSeenAt: string
  lastSeenAt: string
  acknowledgedAt: string | null
  acknowledgedBy: string | null
  resolvedAt: string | null
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

export async function fetchUserPointTransfers(userId: string) {
  const response = await http.get<ApiResponse<PointTransfer[]>>(`/users/${userId}/point-transfers`)
  return response.data.data
}

export async function fetchUserWithdrawals(userId: string) {
  const response = await http.get<ApiResponse<WithdrawalRequest[]>>(`/users/${userId}/withdrawals`)
  return response.data.data
}

export async function fetchContentCacheStatus() {
  const response = await http.get<ApiResponse<ContentCacheStatus>>('/content/cache')
  return response.data.data
}

export async function refreshContentShelf(shelfType: string, locale: string) {
  const response = await http.post<ApiResponse<unknown[]>>(`/content/cache/shelves/${shelfType}/refresh`, null, {
    params: { locale },
  })
  return response.data.data
}

export async function refreshContentShelfLocales(shelfType: string) {
  const response = await http.post<ApiResponse<ContentShelfRefreshResult[]>>(
    `/content/cache/shelves/${shelfType}/refresh-locales`,
  )
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

export async function fetchSystemRuntime() {
  const response = await http.get<ApiResponse<SystemRuntimeResponse>>('/system/runtime')
  return response.data.data
}

export async function fetchSystemLogs(file?: string, lines = 200) {
  const response = await http.get<ApiResponse<SystemLogResponse>>('/system/logs', {
    params: { file, lines },
  })
  return response.data.data
}

export async function fetchSystemAlerts(status?: SystemAlertStatus) {
  const response = await http.get<ApiResponse<SystemAlert[]>>('/system/alerts', {
    params: { status },
  })
  return response.data.data
}

export async function evaluateSystemAlerts() {
  const response = await http.post<ApiResponse<SystemAlert[]>>('/system/alerts/evaluate')
  return response.data.data
}

export async function acknowledgeSystemAlert(alertId: string) {
  const response = await http.post<ApiResponse<SystemAlert>>(`/system/alerts/${alertId}/acknowledge`)
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

export async function fetchWithdrawals() {
  const response = await http.get<ApiResponse<WithdrawalRequest[]>>('/withdrawals')
  return response.data.data
}

export async function approveWithdrawal(withdrawalId: string, txHash: string, note: string) {
  const response = await http.post<ApiResponse<WithdrawalRequest>>(`/withdrawals/${withdrawalId}/approve`, {
    txHash,
    note,
  })
  return response.data.data
}

export async function rejectWithdrawal(withdrawalId: string, reason: string) {
  const response = await http.post<ApiResponse<WithdrawalRequest>>(`/withdrawals/${withdrawalId}/reject`, {
    reason,
  })
  return response.data.data
}

export async function batchPreviewWithdrawals(withdrawalIds: string[], hotWalletPrivateKey: string) {
  const response = await http.post<ApiResponse<BatchWithdrawalPreview>>('/withdrawals/batch-preview', {
    withdrawalIds,
    hotWalletPrivateKey,
  })
  return response.data.data
}

export async function batchApproveWithdrawals(
  withdrawalIds: string[],
  hotWalletPrivateKey: string,
  totpCode: string,
) {
  const response = await http.post<ApiResponse<BatchWithdrawalResult>>('/withdrawals/batch-approve', {
    withdrawalIds,
    hotWalletPrivateKey,
    totpCode,
  })
  return response.data.data
}

export type BatchWithdrawalPreview = {
  hotWalletAddress: string
  hotWalletUsdtBalance: string
  hotWalletTrxBalance: string
  totalUsdt: string
  itemCount: number
  items: { withdrawalId: string; userAccount: string; usdtAmount: string; walletAddress: string }[]
}

export type BatchWithdrawalResult = {
  succeeded: number
  stoppedAtIndex: number
  errorMessage: string | null
  items: { withdrawalId: string; status: string; txHash: string | null; errorMessage: string | null }[]
}

export async function get2faStatus() {
  const response = await http.get<ApiResponse<{ enabled: boolean }>>('/2fa/status')
  return response.data.data
}

export async function setup2fa() {
  const response = await http.post<ApiResponse<{ secret: string; otpauthUri: string }>>('/2fa/setup')
  return response.data.data
}

export async function enable2fa(secret: string, code: string) {
  const response = await http.post<ApiResponse<{ enabled: boolean }>>('/2fa/enable', { secret, code })
  return response.data.data
}

export async function rebind2fa(oldCode: string, newSecret: string, newCode: string) {
  const response = await http.post<ApiResponse<{ secret: string; otpauthUri: string | null }>>('/2fa/rebind', {
    oldCode,
    newSecret,
    newCode,
  })
  return response.data.data
}

export async function fetchPaymentEvents(filters: PaymentEventFilters = {}) {
  const response = await http.get<ApiResponse<PaymentEvent[]>>('/payments/events', {
    params: filters,
  })
  return response.data.data
}

export async function fetchVipOrders() {
  const response = await http.get<ApiResponse<VipOrder[]>>('/vip/orders')
  return response.data.data
}

export async function confirmVipOrder(orderId: string, txHash: string) {
  const response = await http.post<ApiResponse<VipOrder>>(`/vip/orders/${orderId}/confirm`, { txHash })
  return response.data.data
}

export async function rejectVipOrder(orderId: string) {
  const response = await http.post<ApiResponse<VipOrder>>(`/vip/orders/${orderId}/reject`)
  return response.data.data
}
