import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  changePassword as changePasswordRequest,
  fetchMe,
  login as loginRequest,
  logout as logoutRequest,
} from '../api/admin'
import type { Admin } from '../types/api'

/**
 * 管理员会话（Cookie 由后端下发，前端只缓存 `/auth/me` 的管理员信息）。
 * `must_change_password = true` 时路由守卫强制停留在改密页。
 */
export const useAuthStore = defineStore('auth', () => {
  const admin = ref<Admin | null>(null)
  const probing = ref(false)
  const submitting = ref(false)
  let probe: Promise<Admin | null> | null = null

  const isAuthenticated = computed(() => admin.value !== null)
  const mustChangePassword = computed(() => admin.value?.must_change_password === true)
  const displayName = computed(() => admin.value?.display_name || admin.value?.username || '')

  /** 拉取当前会话（幂等，并发调用复用同一个请求）。 */
  async function loadSession(force = false): Promise<Admin | null> {
    if (!force && admin.value !== null) return admin.value
    if (!force && probe !== null) return probe

    probing.value = true
    const pending: Promise<Admin | null> = (async () => {
      try {
        const result = await fetchMe()
        admin.value = result
        return result
      } catch {
        admin.value = null
        return null
      } finally {
        probing.value = false
        probe = null
      }
    })()
    probe = pending
    return pending
  }

  async function login(username: string, password: string): Promise<Admin> {
    submitting.value = true
    try {
      const result = await loginRequest({ username, password })
      admin.value = result.admin
      probe = null
      return result.admin
    } finally {
      submitting.value = false
    }
  }

  async function changePassword(oldPassword: string, newPassword: string): Promise<string> {
    submitting.value = true
    try {
      const result = await changePasswordRequest({ old_password: oldPassword, new_password: newPassword })
      reset()
      return result.message
    } finally {
      submitting.value = false
    }
  }

  /** 退出登录：后端吊销会话，前端无论如何都清空本地状态。 */
  async function logout(): Promise<void> {
    submitting.value = true
    try {
      await logoutRequest()
    } catch {
      // 会话已失效时同样继续清理本地状态
    } finally {
      submitting.value = false
      reset()
    }
  }

  function reset(): void {
    admin.value = null
    probe = null
  }

  /** 收到 403 password_change_required 时兜底标记，避免守卫反复放行。 */
  function markMustChangePassword(): void {
    if (admin.value !== null && !admin.value.must_change_password) {
      admin.value = { ...admin.value, must_change_password: true }
    }
  }

  return {
    admin,
    probing,
    submitting,
    isAuthenticated,
    mustChangePassword,
    displayName,
    loadSession,
    login,
    changePassword,
    logout,
    reset,
    markMustChangePassword,
  }
})
