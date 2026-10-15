import type { Settings, SettingsPatch } from '../types/api'

/** 与 `src/utils/settings-fields.mjs` 中的 JSDoc typedef 保持一致。 */
export type SettingValue = number | boolean | string
export type SettingsBag = Record<string, SettingValue>

/** 契约 `Settings` → 表单草稿（只保留三种可渲染类型，忽略未知字段）。 */
export function settingsToBag(settings: Settings): SettingsBag {
  const bag: SettingsBag = {}
  for (const [key, value] of Object.entries(settings)) {
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      bag[key] = value
    }
  }
  return bag
}

/**
 * 变更键 → 契约设置子集。
 * 调用前必须已通过 `validateSettingsDraft`（类型与取值范围校验），
 * 服务端仍是最终权威，非法值会被后端拒绝并回显错误。
 */
export function buildSettingsPatch(draft: SettingsBag, changedKeys: readonly string[]): SettingsPatch {
  const patch: SettingsBag = {}
  for (const key of changedKeys) {
    const value = draft[key]
    if (value !== undefined) patch[key] = value
  }
  return patch as SettingsPatch
}
