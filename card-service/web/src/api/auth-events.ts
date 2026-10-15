/**
 * 认证失败事件：解耦 HTTP 层与路由/状态层，避免 axios ↔ store ↔ router 的循环依赖。
 * 由 `src/main.ts` 统一注册处理逻辑（清理登录态、跳转登录页/改密页）。
 */

export type AuthFailureKind = 'unauthorized' | 'password_change_required'

type AuthFailureListener = (kind: AuthFailureKind) => void

const listeners = new Set<AuthFailureListener>()

/**
 * 注册认证失败处理，返回取消注册函数。
 */
export function onAuthFailure(listener: AuthFailureListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function emitAuthFailure(kind: AuthFailureKind): void {
  for (const listener of [...listeners]) {
    listener(kind)
  }
}
