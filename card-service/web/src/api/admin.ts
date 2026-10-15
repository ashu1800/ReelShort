import { compactParams, ensureFileBlob, http } from './http'
import type {
  Admin,
  AuditLog,
  AuditLogListQuery,
  BatchActionPayload,
  BatchActionResult,
  Card,
  CardDetail,
  CardEnvelope,
  CardExportFormat,
  CardListQuery,
  CardType,
  CardTypeEnvelope,
  CardTypePayload,
  ChangePasswordPayload,
  ChangePasswordResult,
  ExtendCardPayload,
  GenerateCardsPayload,
  GenerateCardsResult,
  Log,
  LogListQuery,
  LoginPayload,
  LoginResult,
  OkResult,
  PagedList,
  Session,
  SessionListQuery,
  Settings,
  SettingsEnvelope,
  SettingsPatch,
  Stats,
  UpdateCardPayload,
} from '../types/api'

/**
 * 管理后台接口调用（契约 3.1 / 3.3 / 3.4 / 3.5）。
 * 成功响应即资源本身（不是 `data` 包络），失败统一由拦截器抛 ApiError。
 */

async function getJson<T>(url: string, params?: object): Promise<T> {
  const response = await http.get<T>(url, params === undefined ? undefined : { params: compactParams(params) })
  return response.data
}

async function postJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.post<T>(url, body)
  return response.data
}

async function putJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.put<T>(url, body)
  return response.data
}

async function patchJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.patch<T>(url, body)
  return response.data
}

async function deleteJson<T>(url: string): Promise<T> {
  const response = await http.delete<T>(url)
  return response.data
}

/* ------------------------------- 鉴权 3.1 ------------------------------- */

export async function login(payload: LoginPayload): Promise<LoginResult> {
  return postJson<LoginResult>('/auth/login', payload)
}

export async function logout(): Promise<OkResult> {
  return postJson<OkResult>('/auth/logout')
}

export async function fetchMe(): Promise<Admin> {
  const result = await getJson<{ admin: Admin }>('/auth/me')
  return result.admin
}

export async function changePassword(payload: ChangePasswordPayload): Promise<ChangePasswordResult> {
  return putJson<ChangePasswordResult>('/auth/password', payload)
}

/* ------------------------------- 卡密 3.3 ------------------------------- */

export async function fetchCards(query: CardListQuery): Promise<PagedList<Card>> {
  return getJson<PagedList<Card>>('/cards', query)
}

/** 新增单张卡密（`POST /api/admin/cards`）。 */
export async function createCards(payload: GenerateCardsPayload): Promise<GenerateCardsResult> {
  return postJson<GenerateCardsResult>('/cards', payload)
}

/** 批量生成卡密（`POST /api/admin/cards/batch`，count ≤ 10000）。 */
export async function createCardsBatch(payload: GenerateCardsPayload): Promise<GenerateCardsResult> {
  return postJson<GenerateCardsResult>('/cards/batch', payload)
}

export async function fetchCardDetail(cardId: number): Promise<CardDetail> {
  return getJson<CardDetail>(`/cards/${cardId}`)
}

export async function updateCard(cardId: number, payload: UpdateCardPayload): Promise<Card> {
  const result = await patchJson<CardEnvelope>(`/cards/${cardId}`, payload)
  return result.card
}

export async function disableCard(cardId: number): Promise<Card> {
  const result = await postJson<CardEnvelope>(`/cards/${cardId}/disable`)
  return result.card
}

export async function enableCard(cardId: number): Promise<Card> {
  const result = await postJson<CardEnvelope>(`/cards/${cardId}/enable`)
  return result.card
}

export async function unbindCard(cardId: number): Promise<Card> {
  const result = await postJson<CardEnvelope>(`/cards/${cardId}/unbind`)
  return result.card
}

export async function resetCardRebinds(cardId: number): Promise<Card> {
  const result = await postJson<CardEnvelope>(`/cards/${cardId}/reset_rebinds`)
  return result.card
}

export async function extendCard(cardId: number, payload: ExtendCardPayload): Promise<Card> {
  const result = await postJson<CardEnvelope>(`/cards/${cardId}/extend`, payload)
  return result.card
}

export async function deleteCard(cardId: number): Promise<OkResult> {
  return deleteJson<OkResult>(`/cards/${cardId}`)
}

export async function batchCardAction(payload: BatchActionPayload): Promise<BatchActionResult> {
  return postJson<BatchActionResult>('/cards/batch_action', payload)
}

/** 导出当前筛选结果（契约：文件下载，CSV 带 BOM，上限 10 万行）。 */
export async function exportCards(
  query: CardListQuery & { format: CardExportFormat },
): Promise<Blob> {
  const response = await http.get<Blob>('/cards/export', {
    params: compactParams(query),
    responseType: 'blob',
  })
  return ensureFileBlob(response.data, '导出失败，请稍后重试')
}

/* ----------------------------- 卡密类型 3.4 ----------------------------- */

export async function fetchCardTypes(): Promise<CardType[]> {
  const result = await getJson<{ data: CardType[] }>('/card_types')
  return result.data
}

export async function createCardType(payload: CardTypePayload): Promise<CardType> {
  const result = await postJson<CardTypeEnvelope>('/card_types', payload)
  return result.type
}

export async function updateCardType(typeId: number, payload: Partial<CardTypePayload>): Promise<CardType> {
  const result = await patchJson<CardTypeEnvelope>(`/card_types/${typeId}`, payload)
  return result.type
}

export async function deleteCardType(typeId: number): Promise<OkResult> {
  return deleteJson<OkResult>(`/card_types/${typeId}`)
}

/* --------------------- 会话 / 日志 / 统计 / 设置 3.5 --------------------- */

export async function fetchSessions(query: SessionListQuery): Promise<PagedList<Session>> {
  return getJson<PagedList<Session>>('/sessions', query)
}

export async function kickSession(sessionId: number): Promise<OkResult> {
  return postJson<OkResult>(`/sessions/${sessionId}/kick`)
}

export async function fetchLogs(query: LogListQuery): Promise<PagedList<Log>> {
  return getJson<PagedList<Log>>('/logs', query)
}

export async function fetchAuditLogs(query: AuditLogListQuery): Promise<PagedList<AuditLog>> {
  return getJson<PagedList<AuditLog>>('/audit_logs', query)
}

export async function fetchStats(): Promise<Stats> {
  return getJson<Stats>('/stats')
}

export async function fetchSettings(): Promise<Settings> {
  const result = await getJson<SettingsEnvelope>('/settings')
  return result.settings
}

export async function updateSettings(patch: SettingsPatch): Promise<Settings> {
  const result = await putJson<SettingsEnvelope>('/settings', patch)
  return result.settings
}
