/**
 * 系统设置项元数据与「保存前二次确认」所需的纯逻辑（契约 3.5 节 settings 表）。
 * 纯函数模块，浏览器与 node --test 共用。
 */

/**
 * @typedef {object} NumberSettingField
 * @property {'number'} type
 * @property {string} key
 * @property {string} label
 * @property {string} hint
 * @property {number} min
 * @property {number} max
 *
 * @typedef {object} BooleanSettingField
 * @property {'boolean'} type
 * @property {string} key
 * @property {string} label
 * @property {string} hint
 *
 * @typedef {object} SelectSettingField
 * @property {'select'} type
 * @property {string} key
 * @property {string} label
 * @property {string} hint
 * @property {ReadonlyArray<{ value: string, label: string }>} options
 *
 * @typedef {NumberSettingField | BooleanSettingField | SelectSettingField} SettingField
 *
 * @typedef {number | boolean | string} SettingValue
 * @typedef {Record<string, SettingValue>} SettingsBag
 */

/** 契约 3.5 节：10 个设置项，顺序即页面渲染顺序。 @type {ReadonlyArray<SettingField>} */
export const SETTING_FIELDS = [
  {
    type: 'number',
    key: 'heartbeat_interval_seconds',
    label: '心跳间隔（秒）',
    hint: '客户端心跳周期，默认 30，范围 10–300。',
    min: 10,
    max: 300,
  },
  {
    type: 'number',
    key: 'session_timeout_seconds',
    label: '会话超时（秒）',
    hint: '需不小于 2 倍心跳间隔，且不超过 3600，默认 180。',
    min: 1,
    max: 3600,
  },
  {
    type: 'boolean',
    key: 'allow_takeover',
    label: '允许顶号',
    hint: '开启后同一张卡在其他机器上线会顶掉旧会话。',
  },
  {
    type: 'number',
    key: 'default_max_rebinds',
    label: '默认换绑上限',
    hint: '新建卡密的默认可换绑次数，范围 0–100，默认 3。',
    min: 0,
    max: 100,
  },
  {
    type: 'select',
    key: 'activate_on',
    label: '激活时机',
    hint: '首次校验或首次上线时开始计时。',
    options: [
      { value: 'verify', label: '校验卡密时激活' },
      { value: 'login', label: '上线时激活' },
    ],
  },
  {
    type: 'boolean',
    key: 'log_heartbeat_success',
    label: '记录心跳成功日志',
    hint: '开启后成功心跳也写入操作日志，默认关闭以避免日志膨胀。',
  },
  {
    type: 'number',
    key: 'rate_limit_ip_per_minute',
    label: '单 IP 限流（次/分钟）',
    hint: '公共接口单 IP 每分钟上限，范围 10–100000，默认 300。',
    min: 10,
    max: 100000,
  },
  {
    type: 'number',
    key: 'rate_limit_card_per_minute',
    label: '单卡密限流（次/分钟）',
    hint: '单张卡密每分钟上限，范围 5–100000，默认 60。',
    min: 5,
    max: 100000,
  },
  {
    type: 'number',
    key: 'rate_limit_session_per_minute',
    label: '单会话限流（次/分钟）',
    hint: '单个会话心跳每分钟上限，范围 5–100000，默认 120。',
    min: 5,
    max: 100000,
  },
  {
    type: 'number',
    key: 'admin_session_hours',
    label: '后台会话有效期（小时）',
    hint: '管理员登录会话时长，范围 1–168，默认 12。',
    min: 1,
    max: 168,
  },
]

/** 设置 key 列表。 */
export const SETTING_KEYS = SETTING_FIELDS.map((field) => field.key)

/**
 * @param {string} key
 * @returns {SettingField | undefined}
 */
export function findSettingField(key) {
  return SETTING_FIELDS.find((field) => field.key === key)
}

/**
 * 设置值 → 二次确认弹窗可读文案。
 * @param {SettingField} field
 * @param {unknown} value
 * @returns {string}
 */
export function formatSettingValue(field, value) {
  if (field.type === 'boolean') return value === true ? '开启' : '关闭'
  if (value === null || value === undefined || value === '') return '—'
  if (field.type === 'select') {
    const option = field.options.find((item) => item.value === value)
    return option ? option.label : String(value)
  }
  return String(value)
}

/**
 * 比较当前设置与草稿，输出将要变更的键、旧值、新值（按契约顺序）。
 * @param {SettingsBag} current 后端返回的设置（由 `settingsToBag` 转换）
 * @param {SettingsBag} draft 页面草稿
 * @returns {ReadonlyArray<{ key: string, label: string, oldText: string, newText: string, value: SettingValue }>}
 */
export function diffSettings(current, draft) {
  /** @type {Array<{ key: string, label: string, oldText: string, newText: string, value: SettingValue }>} */
  const changes = []
  for (const field of SETTING_FIELDS) {
    const oldValue = current[field.key]
    const newValue = draft[field.key]
    if (newValue === undefined || oldValue === newValue) continue
    changes.push({
      key: field.key,
      label: field.label,
      oldText: formatSettingValue(field, oldValue),
      newText: formatSettingValue(field, newValue),
      value: newValue,
    })
  }
  return changes
}

/**
 * 校验设置草稿（前端先拦一遍，最终以后端为准）。
 * @param {SettingsBag} draft
 * @returns {ReadonlyArray<{ key: string, message: string }>}
 */
export function validateSettingsDraft(draft) {
  /** @type {Array<{ key: string, message: string }>} */
  const errors = []
  for (const field of SETTING_FIELDS) {
    if (field.type === 'boolean') {
      if (typeof draft[field.key] !== 'boolean') {
        errors.push({ key: field.key, message: `${field.label} 必须为布尔值` })
      }
      continue
    }
    if (field.type === 'select') {
      const value = draft[field.key]
      if (!field.options.some((option) => option.value === value)) {
        errors.push({ key: field.key, message: `${field.label} 取值不合法` })
      }
      continue
    }
    const value = draft[field.key]
    if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value)) {
      errors.push({ key: field.key, message: `${field.label} 必须为整数` })
      continue
    }
    if (value < field.min || value > field.max) {
      errors.push({ key: field.key, message: `${field.label} 需在 ${field.min}–${field.max} 之间` })
    }
  }

  const heartbeat = draft.heartbeat_interval_seconds
  const timeout = draft.session_timeout_seconds
  if (typeof heartbeat === 'number' && typeof timeout === 'number' && timeout < heartbeat * 2) {
    errors.push({
      key: 'session_timeout_seconds',
      message: `会话超时需不小于 2 倍心跳间隔（当前心跳 ${heartbeat} 秒，至少 ${heartbeat * 2} 秒）`,
    })
  }

  return errors
}
