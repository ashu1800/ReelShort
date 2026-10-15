/**
 * 契约枚举值 → 简体中文标签映射（API.md 3.2 / 3.4 节）。
 * 纯函数模块，浏览器与 node --test 共用。
 */

/** 未知枚举值时的占位文案。 */
export const UNKNOWN_LABEL = '未知'

/** `display_state`（展示态）→ 中文。 @type {Record<string, string>} */
export const DISPLAY_STATE_LABELS = {
  unused: '未激活',
  active: '正常',
  expired: '已过期',
  disabled: '已禁用',
}

/** `state`（存储态）→ 中文。 @type {Record<string, string>} */
export const STORAGE_STATE_LABELS = {
  unused: '未激活',
  active: '已激活',
  disabled: '已禁用',
}

/** `display_state` → Element Plus Tag 语义类型（颜色只是辅助，文案必须同时呈现）。 @type {Record<string, string>} */
export const DISPLAY_STATE_TAG_TYPES = {
  unused: 'info',
  active: 'success',
  expired: 'warning',
  disabled: 'danger',
}

/** 卡密列表筛选用的 `state` 取值（契约 3.3：含 `online`）。 */
export const CARD_STATE_FILTERS = ['unused', 'active', 'expired', 'disabled', 'online']

/** 卡密列表筛选项文案（`online` 不等于任何 `display_state`）。 @type {Record<string, string>} */
export const CARD_STATE_FILTER_LABELS = {
  unused: '未激活',
  active: '正常',
  expired: '已过期',
  disabled: '已禁用',
  online: '在线',
}

/** `action` → 中文。 @type {Record<string, string>} */
export const ACTION_LABELS = {
  verify: '校验卡密',
  status: '查询状态',
  rebind: '换绑',
  login: '上线',
  heartbeat: '心跳',
  logout: '下线',
  admin_create: '后台生成',
  admin_disable: '后台禁用',
  admin_enable: '后台启用',
  admin_unbind: '后台解绑',
  admin_extend: '后台延期',
  admin_reset_rebinds: '重置换绑次数',
  admin_delete: '后台删除',
  admin_kick: '强制下线',
  admin_edit: '后台编辑',
}

/** `result` → 中文。 @type {Record<string, string>} */
export const RESULT_LABELS = {
  ok: '成功',
  fail: '失败',
}

/** `close_reason` → 中文。 @type {Record<string, string>} */
export const CLOSE_REASON_LABELS = {
  logout: '客户端下线',
  kicked: '被其他机器顶号',
  relogin: '重复登录',
  timeout: '心跳超时',
  expired: '卡密过期',
  disabled: '卡密禁用',
  admin_kick: '后台强制下线',
  rebind: '换绑后下线',
}

/** `activate_on` → 中文。 @type {Record<string, string>} */
export const ACTIVATE_ON_LABELS = {
  verify: '校验卡密时激活',
  login: '上线时激活',
}

/** 审计日志 `action` → 中文（契约 3.5 `GET /api/admin/audit_logs`）。 @type {Record<string, string>} */
export const AUDIT_ACTION_LABELS = {
  admin_login: '管理员登录',
  admin_logout: '管理员退出',
  admin_change_password: '修改密码',
  admin_reset_password: '重置密码（命令行）',
  card_batch_create: '批量生成卡密',
  card_update: '编辑卡密',
  card_disable: '禁用卡密',
  card_enable: '启用卡密',
  card_unbind: '解绑机器码',
  card_reset_rebinds: '重置换绑次数',
  card_extend: '卡密延期',
  card_delete: '删除卡密',
  card_batch_disable: '批量禁用卡密',
  card_batch_enable: '批量启用卡密',
  card_batch_delete: '批量删除卡密',
  card_batch_reset_rebinds: '批量重置换绑次数',
  card_batch_extend: '批量延期',
  card_type_create: '新增卡密类型',
  card_type_update: '修改卡密类型',
  card_type_delete: '删除卡密类型',
  session_kick: '强制下线会话',
  settings_update: '修改系统设置',
}

/** 审计日志 `target_type` → 中文。 @type {Record<string, string>} */
export const AUDIT_TARGET_LABELS = {
  admin: '管理员',
  card: '卡密',
  card_type: '卡密类型',
  session: '会话',
  settings: '系统设置',
}

/**
 * 未知值回退：能原样展示后端新枚举值，便于运维排障。
 * @param {string} value
 * @returns {string}
 */
function fallbackLabel(value) {
  return typeof value === 'string' && value.trim() !== '' ? value : UNKNOWN_LABEL
}

/**
 * @param {string} state 契约 `display_state`
 * @returns {string}
 */
export function displayStateLabel(state) {
  return DISPLAY_STATE_LABELS[state] ?? fallbackLabel(state)
}

/**
 * @param {string} state 契约 `display_state`
 * @returns {string} Element Plus Tag 类型
 */
export function displayStateTagType(state) {
  return DISPLAY_STATE_TAG_TYPES[state] ?? 'info'
}

/**
 * @param {string} state 卡密列表筛选值（含 `online`）
 * @returns {string}
 */
export function cardStateFilterLabel(state) {
  return CARD_STATE_FILTER_LABELS[state] ?? fallbackLabel(state)
}

/**
 * @param {string} state 契约 `state`
 * @returns {string}
 */
export function storageStateLabel(state) {
  return STORAGE_STATE_LABELS[state] ?? fallbackLabel(state)
}

/**
 * @param {string} action 契约 `action`
 * @returns {string}
 */
export function actionLabel(action) {
  return ACTION_LABELS[action] ?? fallbackLabel(action)
}

/**
 * @param {string} result 契约 `result`（ok / fail）
 * @returns {string}
 */
export function resultLabel(result) {
  return RESULT_LABELS[result] ?? fallbackLabel(result)
}

/**
 * @param {string} result
 * @returns {string} Element Plus Tag 类型
 */
export function resultTagType(result) {
  if (result === 'ok') return 'success'
  if (result === 'fail') return 'danger'
  return 'info'
}

/**
 * @param {string} reason 契约 `close_reason`，会话仍在线的空字符串返回破折号
 * @returns {string}
 */
export function closeReasonLabel(reason) {
  if (typeof reason !== 'string' || reason.trim() === '') return '—'
  return CLOSE_REASON_LABELS[reason] ?? fallbackLabel(reason)
}

/**
 * @param {boolean} active 契约 `Session.active`
 * @returns {string}
 */
export function sessionStateLabel(active) {
  return active ? '在线' : '已关闭'
}

/**
 * @param {string} value 契约 `settings.activate_on`
 * @returns {string}
 */
export function activateOnLabel(value) {
  return ACTIVATE_ON_LABELS[value] ?? fallbackLabel(value)
}

/**
 * @param {string} action 审计日志 `action`
 * @returns {string}
 */
export function auditActionLabel(action) {
  return AUDIT_ACTION_LABELS[action] ?? fallbackLabel(action)
}

/** 审计目标类型为空时的占位。 */
export const EMPTY_TARGET_LABEL = '—'

/**
 * @param {string} targetType 审计日志 `target_type`
 * @returns {string}
 */
export function auditTargetLabel(targetType) {
  if (typeof targetType !== 'string' || targetType.trim() === '') return EMPTY_TARGET_LABEL
  return AUDIT_TARGET_LABELS[targetType] ?? fallbackLabel(targetType)
}
