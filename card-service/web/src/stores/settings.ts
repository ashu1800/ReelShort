import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchSettings, updateSettings } from '../api/admin'
import { errorMessage } from '../api/http'
import type { Settings, SettingsPatch } from '../types/api'

/** 系统设置缓存；保存成功后直接用响应刷新，避免二次请求。 */
export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<Settings | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string | null>(null)

  async function load(force = false): Promise<Settings | null> {
    if (!force && settings.value !== null) return settings.value
    loading.value = true
    error.value = null
    try {
      settings.value = await fetchSettings()
      return settings.value
    } catch (caught) {
      error.value = errorMessage(caught, '系统设置加载失败')
      settings.value = null
      return null
    } finally {
      loading.value = false
    }
  }

  async function save(patch: SettingsPatch): Promise<Settings> {
    saving.value = true
    try {
      const result = await updateSettings(patch)
      settings.value = result
      error.value = null
      return result
    } finally {
      saving.value = false
    }
  }

  function reset(): void {
    settings.value = null
    error.value = null
  }

  return { settings, loading, saving, error, load, save, reset }
})
