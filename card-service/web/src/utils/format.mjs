/**
 * 展示层格式化：卡密/机器码打码、剩余时长、卡密时长、日期区间。
 * 契约时间格式为 `YYYY-MM-DD HH:MM:SS`（Asia/Shanghai），前端只做展示与截断，不做时区换算。
 * 纯函数模块，浏览器与 node --test 共用。
 */

/** 时间字段为空时的占位符（契约：时间字段为空时返回空字符串）。 */
export const EMPTY_TEXT = '—'

/** 永久卡展示文案。 */
export const PERMANENT_TEXT = '永久有效'

/**
 * 归一化卡密：去分隔符、去空白、转大写（契约 3.3 生成规则：存储为无分隔规范形）。
 * @param {unknown} code
 * @returns {string}
 */
export function normalizeCardCode(code) {
  if (typeof code !== 'string') return ''
  return code.replace(/[^0-9A-Za-z]/g, '').toUpperCase()
}

/**
 * 按每 4 位一组用 `-` 分隔（展示形）。
 * @param {string} canonical
 * @returns {string}
 */
export function groupCardCode(canonical) {
  return canonical.replace(/(.{4})(?=.)/g, '$1-')
}

/**
 * 卡密打码：保留首尾各 4 位（短卡保留 2 位），中间以 `*` 占位后按 4 位分组。
 * `ABCDEFGHIJKLMNPQ` → `ABCD-****-****-MNPQ`
 * @param {unknown} code
 * @returns {string}
 */
export function maskCardCode(code) {
  const canonical = normalizeCardCode(code)
  if (canonical === '') return ''
  if (canonical.length <= 4) return '*'.repeat(canonical.length)
  const edge = canonical.length >= 8 ? 4 : 2
  const head = canonical.slice(0, edge)
  const tail = canonical.slice(canonical.length - edge)
  const masked = `${head}${'*'.repeat(canonical.length - edge * 2)}${tail}`
  return groupCardCode(masked)
}

/**
 * 机器码打码：≤4 位全打码，≤8 位保留首尾各 2 位，其余保留首尾各 4 位。
 * @param {unknown} machineCode
 * @returns {string}
 */
export function maskMachineCode(machineCode) {
  if (typeof machineCode !== 'string') return ''
  const value = machineCode.trim().toUpperCase()
  if (value === '') return ''
  if (value.length <= 4) return '*'.repeat(value.length)
  const edge = value.length <= 8 ? 2 : 4
  return `${value.slice(0, edge)}${'*'.repeat(4)}${value.slice(value.length - edge)}`
}

/**
 * 分钟数 → 时长文案。1440 → `1 天`；90 → `1 小时 30 分钟`。
 * @param {number} minutes
 * @returns {string}
 */
export function formatMinutes(minutes) {
  const value = Number.isFinite(minutes) ? Math.max(0, Math.floor(minutes)) : 0
  if (value <= 0) return '0 分钟'
  const days = Math.floor(value / 1440)
  const hours = Math.floor((value % 1440) / 60)
  const restMinutes = value % 60
  if (days > 0) {
    if (hours === 0 && restMinutes === 0) return `${days} 天`
    if (restMinutes === 0) return `${days} 天 ${hours} 小时`
    return `${days} 天 ${hours} 小时 ${restMinutes} 分钟`
  }
  if (hours > 0) {
    return restMinutes === 0 ? `${hours} 小时` : `${hours} 小时 ${restMinutes} 分钟`
  }
  return `${restMinutes} 分钟`
}

/**
 * 卡密类型时长文案。`duration_minutes = 0` 为永久（契约 3.4）。
 * @param {number} durationMinutes
 * @param {string} [durationText] 后端 `duration_text`，为空时由分钟数推导
 * @returns {string}
 */
export function formatDurationText(durationMinutes, durationText) {
  const minutes = Number.isFinite(durationMinutes) ? Math.floor(durationMinutes) : 0
  if (minutes <= 0) return '永久'
  const text = typeof durationText === 'string' ? durationText.trim() : ''
  return text !== '' ? text : formatMinutes(minutes)
}

/**
 * 剩余时长文案：永久卡返回「永久有效」，已到期返回「已到期」。
 * @param {number} remainingSeconds 契约 `remaining_seconds`（永久卡为 0）
 * @param {boolean} [permanent] 契约 `permanent`
 * @returns {string}
 */
export function formatRemaining(remainingSeconds, permanent = false) {
  if (permanent) return PERMANENT_TEXT
  const total = Number.isFinite(remainingSeconds) ? Math.floor(remainingSeconds) : 0
  if (total <= 0) return '已到期'
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  if (minutes > 0) return `${minutes} 分钟`
  return '不足 1 分钟'
}

/**
 * 到期时间文案：永久卡固定显示「永久有效」。
 * @param {string} expiresAt
 * @param {boolean} [permanent]
 * @returns {string}
 */
export function formatExpiry(expiresAt, permanent = false) {
  if (permanent) return PERMANENT_TEXT
  return formatDateTime(expiresAt)
}

/**
 * 时间文案：空字符串返回破折号（契约：时间字段为空返回 `""`）。
 * @param {unknown} value
 * @returns {string}
 */
export function formatDateTime(value) {
  if (typeof value !== 'string') return EMPTY_TEXT
  const text = value.trim()
  return text === '' ? EMPTY_TEXT : text
}

/**
 * 整数千分位：1234 → `1,234`。
 * @param {unknown} value
 * @returns {string}
 */
export function formatInteger(value) {
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) return '0'
  const rounded = Math.trunc(numeric)
  const negative = rounded < 0
  const digits = Math.abs(rounded).toString()
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return negative ? `-${grouped}` : grouped
}

/**
 * 日期区间（`YYYY-MM-DD` 数组）→ 日志/审计接口的 `from`/`to` 时间区间。
 * 契约未规定 `from`/`to` 的具体格式，这里按统一时间格式补齐当日 00:00:00 / 23:59:59。
 * @param {readonly string[] | null | undefined} range
 * @returns {{ from?: string, to?: string }}
 */
export function dayRangeToTimestamps(range) {
  if (!Array.isArray(range) || range.length < 2) return {}
  const from = typeof range[0] === 'string' ? range[0].trim() : ''
  const to = typeof range[1] === 'string' ? range[1].trim() : ''
  /** @type {{ from?: string, to?: string }} */
  const result = {}
  if (from !== '') result.from = `${from} 00:00:00`
  if (to !== '') result.to = `${to} 23:59:59`
  return result
}

/**
 * 日期区间（`YYYY-MM-DD` 数组）→ 卡密列表的 `created_from`/`created_to`。
 * @param {readonly string[] | null | undefined} range
 * @returns {{ created_from?: string, created_to?: string }}
 */
export function dayRangeToCardCreated(range) {
  if (!Array.isArray(range) || range.length < 2) return {}
  const from = typeof range[0] === 'string' ? range[0].trim() : ''
  const to = typeof range[1] === 'string' ? range[1].trim() : ''
  /** @type {{ created_from?: string, created_to?: string }} */
  const result = {}
  if (from !== '') result.created_from = from
  if (to !== '') result.created_to = to
  return result
}
