import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import {
  API_ERROR_MESSAGES,
  DEFAULT_ERROR_CODE,
  DEFAULT_ERROR_MESSAGE,
  extractApiError,
  requiresAdminCsrfHeader,
} from '../utils/envelope.mjs'
import { emitAuthFailure } from './auth-events'

/**
 * 契约 3.1 节要求：写操作必须带 `X-Requested-With: card-admin`，否则 403。
 * 这三个接口不参与「401 清理登录态并跳登录页」的全局流程，由调用方自行处理。
 */
const SILENT_AUTH_PATHS = ['/auth/login', '/auth/me', '/auth/logout']

/** HTTP 状态码 → 契约错误码兜底（响应体不是契约包络时使用）。 */
const STATUS_FALLBACK_CODES: Record<number, string> = {
  400: 'validation_error',
  401: 'unauthorized',
  403: 'forbidden',
  404: 'not_found',
  409: 'conflict',
  422: 'validation_error',
  429: 'rate_limited',
  500: 'internal_error',
  502: 'internal_error',
  503: 'internal_error',
  504: 'internal_error',
}

/** 网络层失败（无响应）时的文案。 */
export const NETWORK_ERROR_MESSAGE = '无法连接服务器，请检查网络或后端服务状态'

/** 带契约错误码的异常，视图层据此做分支提示。 */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly url: string

  constructor(message: string, code: string, status: number, url: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.url = url
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** 统一的错误文案提取（视图层 catch 使用）。 */
export function errorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (isApiError(error)) return error.message
  if (error instanceof Error && error.message !== '') return error.message
  return fallback
}

/** 统一的错误码提取。 */
export function errorCode(error: unknown): string {
  return isApiError(error) ? error.code : DEFAULT_ERROR_CODE
}

export const http: AxiosInstance = axios.create({
  baseURL: '/api/admin',
  withCredentials: true,
  timeout: 30000,
  headers: { Accept: 'application/json' },
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (requiresAdminCsrfHeader(config.method)) {
    config.headers.set('X-Requested-With', 'card-admin')
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiError(error)),
)

function isSilentAuthPath(url: string): boolean {
  return SILENT_AUTH_PATHS.some((path) => url.endsWith(path))
}

function toApiError(error: unknown): ApiError {
  if (isApiError(error)) return error

  if (!axios.isAxiosError(error)) {
    return new ApiError(
      error instanceof Error && error.message !== '' ? error.message : '请求失败，请稍后重试',
      DEFAULT_ERROR_CODE,
      0,
      '',
    )
  }

  const status = error.response?.status ?? 0
  const url = error.config?.url ?? ''

  if (status === 0) {
    return new ApiError(NETWORK_ERROR_MESSAGE, 'network_error', 0, url)
  }

  const fallbackCode = STATUS_FALLBACK_CODES[status] ?? DEFAULT_ERROR_CODE
  const payload: unknown = error.response?.data
  const raw: unknown = typeof payload === 'string' ? safeJsonParse(payload) : payload
  const { code, message } = extractApiError(
    raw instanceof Blob ? undefined : raw,
    fallbackCode,
  )

  const apiError = new ApiError(message, code, status, url)
  if (!isSilentAuthPath(url)) {
    if (status === 401) {
      emitAuthFailure('unauthorized')
    } else if (status === 403 && code === 'password_change_required') {
      emitAuthFailure('password_change_required')
    }
  }
  return apiError
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

/**
 * 丢弃空值查询参数（axios 不会丢弃空字符串，会把 `keyword=` 传给后端）。
 */
export function compactParams<T extends object>(params: T): Record<string, string | number | boolean> {
  const result: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      result[key] = value
    }
  }
  return result
}

/**
 * 下载响应在失败时可能是 JSON 错误体：解析出来转成 ApiError，避免把 JSON 存成文件。
 */
export async function ensureFileBlob(blob: Blob, fallbackMessage = '导出失败，请稍后重试'): Promise<Blob> {
  if (!blob.type.includes('json')) return blob
  const parsed = await safeBlobJson(blob)
  const { code, message } = extractApiError(parsed)
  throw new ApiError(message === DEFAULT_ERROR_MESSAGE ? fallbackMessage : message, code, 200, '')
}

async function safeBlobJson(blob: Blob): Promise<unknown> {
  try {
    return JSON.parse(await blob.text())
  } catch {
    return undefined
  }
}

/** 兜底文案（供视图层在无后端 message 时使用）。 */
export const FALLBACK_ERROR_MESSAGES = API_ERROR_MESSAGES
