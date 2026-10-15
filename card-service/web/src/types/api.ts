/**
 * 数据对象类型定义，严格对齐 `card-service/docs/API.md` 第 3.2 / 3.4 / 3.5 节。
 * 契约冻结：不要在这里臆造后端未定义的字段。
 */

/** 卡密存储态（契约 `state`）。 */
export type CardState = 'unused' | 'active' | 'disabled'

/** 卡密展示态（契约 `display_state`）。 */
export type CardDisplayState = 'unused' | 'active' | 'expired' | 'disabled'

/** 卡密列表筛选的 `state` 取值，比存储态多一个 `online`。 */
export type CardStateFilter = CardDisplayState | 'online'

/** 卡密列表排序（契约 `sort`）。 */
export type CardSort = 'created_at_desc' | 'created_at_asc' | 'expires_at_asc'

/** 会话关闭原因（契约 `close_reason`，在线会话为空字符串）。 */
export type CloseReason =
  | 'logout'
  | 'kicked'
  | 'relogin'
  | 'timeout'
  | 'expired'
  | 'disabled'
  | 'admin_kick'
  | 'rebind'

/** 操作日志动作（契约 `action`）。 */
export type LogAction =
  | 'verify'
  | 'status'
  | 'rebind'
  | 'login'
  | 'heartbeat'
  | 'logout'
  | 'admin_create'
  | 'admin_disable'
  | 'admin_enable'
  | 'admin_unbind'
  | 'admin_extend'
  | 'admin_reset_rebinds'
  | 'admin_delete'
  | 'admin_kick'
  | 'admin_edit'

/** 操作日志结果（契约 `result`）。 */
export type LogResult = 'ok' | 'fail'

/** 批量操作动作（契约 3.3 `batch_action`）。 */
export type BatchAction = 'disable' | 'enable' | 'delete' | 'reset_rebinds' | 'extend'

/** 激活时机（契约 `settings.activate_on`）。 */
export type ActivateOn = 'verify' | 'login'

/** 导出格式（契约 3.3 `format`）。 */
export type CardExportFormat = 'csv' | 'txt'

/** Card（契约 3.2）。时间字段为空时返回空字符串。 */
export interface Card {
  id: number
  code: string
  type_id: number
  type_name: string
  duration_minutes: number
  state: CardState
  display_state: CardDisplayState
  max_rebinds: number
  rebind_count: number
  rebind_remaining: number
  bound_machine: string
  bound_at: string
  activated_at: string
  expires_at: string
  permanent: boolean
  remaining_seconds: number
  online: boolean
  online_machine: string
  online_at: string
  batch_no: string
  remark: string
  created_by: string
  created_at: string
  updated_at: string
}

/** Session（契约 3.2）。 */
export interface Session {
  id: number
  card_id: number
  card_code: string
  machine_code: string
  ip: string
  user_agent: string
  created_at: string
  last_heartbeat_at: string
  expires_at: string
  closed_at: string
  close_reason: CloseReason | ''
  active: boolean
}

/** Log（契约 3.2）。 */
export interface Log {
  id: number
  card_id: number
  code_masked: string
  action: LogAction
  result: LogResult
  message: string
  machine_code: string
  ip: string
  created_at: string
}

/** CardType（契约 3.4）。`duration_minutes = 0` 表示永久。 */
export interface CardType {
  id: number
  name: string
  duration_minutes: number
  duration_text: string
  max_rebinds: number
  enabled: boolean
  sort_order: number
  card_count: number
  created_at: string
}

/** Admin（契约 3.1）。 */
export interface Admin {
  id: number
  username: string
  display_name: string
  must_change_password: boolean
  last_login_at: string
  created_at: string
}

/** 审计日志行（契约 3.5 `GET /api/admin/audit_logs`）。 */
export interface AuditLog {
  id: number
  username: string
  action: string
  target_type: string
  target_id: string
  detail: string
  ip: string
  created_at: string
}

/** 分页信息（契约 3.2）。 */
export interface Pagination {
  page: number
  page_size: number
  total_items: number
  total_pages: number
}

/** 分页响应（契约 3.2）。 */
export interface PagedList<T> {
  data: T[]
  pagination: Pagination
}

/** 卡密统计（契约 3.5 `stats.cards`）。 */
export interface CardStats {
  total: number
  unused: number
  active: number
  expired: number
  disabled: number
  online: number
}

/** 今日数据（契约 3.5 `stats.today`）。 */
export interface TodayStats {
  activated: number
  verified: number
  logged_in: number
  rebinds: number
}

/** 趋势点（契约 3.5 `stats.trend`）。 */
export interface TrendPoint {
  date: string
  activated: number
}

/** 统计响应（契约 3.5 `GET /api/admin/stats`）。 */
export interface Stats {
  cards: CardStats
  today: TodayStats
  trend: TrendPoint[]
  server_time: string
}

/** 系统设置（契约 3.5 settings 表，键与类型固定）。 */
export interface Settings {
  heartbeat_interval_seconds: number
  session_timeout_seconds: number
  allow_takeover: boolean
  default_max_rebinds: number
  activate_on: ActivateOn
  log_heartbeat_success: boolean
  rate_limit_ip_per_minute: number
  rate_limit_card_per_minute: number
  rate_limit_session_per_minute: number
  admin_session_hours: number
}

/** `PUT /api/admin/settings` 请求体：设置子集。 */
export type SettingsPatch = Partial<Settings>

/** `GET /api/admin/cards` 查询参数（契约 3.3）。 */
export interface CardListQuery {
  keyword?: string
  state?: CardStateFilter
  type_id?: number
  batch_no?: string
  created_from?: string
  created_to?: string
  page?: number
  page_size?: number
  sort?: CardSort
}

/** `POST /api/admin/cards` 与 `/cards/batch` 请求体（契约 3.3）。 */
export interface GenerateCardsPayload {
  type_id: number
  count?: number
  prefix?: string
  length?: number
  max_rebinds?: number | null
  batch_no?: string
  remark?: string
}

/** 卡密生成结果（契约 3.3）。 */
export interface GenerateCardsResult {
  codes: string[]
  batch_no: string
  count: number
}

/** `GET /api/admin/cards/{id}` 响应（契约 3.3）。 */
export interface CardDetail {
  card: Card
  sessions: Session[]
  logs: Log[]
}

/** `PATCH /api/admin/cards/{id}` 请求体（契约 3.3，`expires_at: ""` 表示永久）。 */
export interface UpdateCardPayload {
  remark?: string
  max_rebinds?: number
  expires_at?: string
}

/** `POST /api/admin/cards/{id}/extend` 请求体（契约 3.3）。 */
export interface ExtendCardPayload {
  days?: number
  minutes?: number
}

/** `POST /api/admin/cards/batch_action` 请求体（契约 3.3）。 */
export interface BatchActionPayload {
  ids: number[]
  action: BatchAction
  days?: number
}

/** 批量操作结果（契约 3.3）。 */
export interface BatchActionResult {
  affected: number
}

/** `POST /api/admin/card_types` 请求体（契约 3.4）。 */
export interface CardTypePayload {
  name: string
  duration_minutes: number
  max_rebinds?: number
  enabled?: boolean
  sort_order?: number
}

/** `GET /api/admin/sessions` 查询参数（契约 3.5）。 */
export interface SessionListQuery {
  active?: boolean
  card_code?: string
  page?: number
  page_size?: number
}

/** `GET /api/admin/logs` 查询参数（契约 3.5）。 */
export interface LogListQuery {
  card_code?: string
  action?: LogAction
  result?: LogResult
  from?: string
  to?: string
  page?: number
  page_size?: number
}

/** `GET /api/admin/audit_logs` 查询参数（契约 3.5）。 */
export interface AuditLogListQuery {
  admin?: string
  action?: string
  from?: string
  to?: string
  page?: number
  page_size?: number
}

/** `POST /api/admin/auth/login` 请求体（契约 3.1）。 */
export interface LoginPayload {
  username: string
  password: string
}

/** 登录响应（契约 3.1）。 */
export interface LoginResult {
  admin: Admin
  expires_at: string
}

/** `PUT /api/admin/auth/password` 请求体（契约 3.1）。 */
export interface ChangePasswordPayload {
  old_password: string
  new_password: string
}

/** 通用 `{ok: true}` 响应（契约 3.1 / 3.3 / 3.4 / 3.5）。 */
export interface OkResult {
  ok: boolean
}

/** 改密响应（契约 3.1）。 */
export interface ChangePasswordResult {
  ok: boolean
  message: string
}

/** `{"card": Card}` 形态响应（契约 3.3）。 */
export interface CardEnvelope {
  card: Card
}

/** `{"type": CardType}` 形态响应（契约 3.4）。 */
export interface CardTypeEnvelope {
  type: CardType
}

/** `{"settings": Settings}` 形态响应（契约 3.5）。 */
export interface SettingsEnvelope {
  settings: Settings
}
