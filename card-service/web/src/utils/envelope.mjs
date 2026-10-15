/**
 * 管理后台失败响应包络解析（契约 3.1 节）。
 * 失败形态：{"error":{"code":"conflict","message":"..."}}
 *
 * 本模块是纯函数，可被浏览器（Vite）与 node --test 同时使用。
 */

/**
 * @typedef {object} ApiErrorShape
 * @property {string} code 契约错误码，未知形态时回退为 fallbackCode
 * @property {string} message 面向运维的中文提示，非空
 */

/** 契约 3.1 节错误码 → 兜底中文文案。 @type {Record<string, string>} */
export const API_ERROR_MESSAGES = {
  invalid_credentials: '账号或密码错误',
  unauthorized: '登录状态已失效，请重新登录',
  forbidden: '没有权限执行该操作',
  password_change_required: '首次登录必须先修改密码',
  not_found: '请求的资源不存在',
  conflict: '操作冲突，请刷新后重试',
  validation_error: '提交的参数不合法',
  rate_limited: '请求过于频繁，请稍后再试',
  internal_error: '服务异常，请稍后再试',
}

/** 兜底错误码，用于响应体不是契约包络时。 */
export const DEFAULT_ERROR_CODE = 'internal_error'

/** 兜底错误文案。 */
export const DEFAULT_ERROR_MESSAGE = '请求失败，请稍后重试'

/**
 * @param {unknown} value
 * @returns {value is Record<string, unknown>}
 */
function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 把响应体解析为 {code, message}。无法识别时返回兜底值与兜底文案。
 * @param {unknown} payload 响应体（可能是契约包络、字符串或 HTML 错误页）
 * @param {string} [fallbackCode]
 * @returns {ApiErrorShape}
 */
export function extractApiError(payload, fallbackCode = DEFAULT_ERROR_CODE) {
  if (!isRecord(payload)) {
    return { code: fallbackCode, message: DEFAULT_ERROR_MESSAGE }
  }

  const envelope = isRecord(payload.error) ? payload.error : payload
  const code = typeof envelope.code === 'string' && envelope.code.trim() !== ''
    ? envelope.code.trim()
    : fallbackCode
  const rawMessage = typeof envelope.message === 'string' ? envelope.message.trim() : ''
  const message = rawMessage !== ''
    ? rawMessage
    : (API_ERROR_MESSAGES[code] ?? DEFAULT_ERROR_MESSAGE)

  return { code, message }
}

/**
 * 写操作需要携带的 CSRF 请求头（契约 3.1：缺失返回 403 forbidden）。
 * @param {string} [method]
 * @returns {boolean}
 */
export function requiresAdminCsrfHeader(method) {
  const normalized = typeof method === 'string' ? method.toUpperCase() : 'GET'
  return normalized === 'POST' || normalized === 'PUT' || normalized === 'PATCH' || normalized === 'DELETE'
}
