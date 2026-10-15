/**
 * 新密码强度与前端校验（后端另有权威校验，这里只做即时提示）。
 * 纯函数模块，浏览器与 node --test 共用。
 */

/** 后台新密码最小长度（前端约束）。 */
export const MIN_PASSWORD_LENGTH = 10

/**
 * 字符类别命中数：小写、大写、数字、符号。
 * @param {string} value
 * @returns {number}
 */
function characterClasses(value) {
  let classes = 0
  if (/[a-z]/.test(value)) classes += 1
  if (/[A-Z]/.test(value)) classes += 1
  if (/\d/.test(value)) classes += 1
  if (/[^0-9A-Za-z]/.test(value)) classes += 1
  return classes
}

/**
 * 密码强度评分。
 * @param {unknown} password
 * @returns {{ score: number, level: 'weak' | 'medium' | 'strong', label: string, hints: string[] }}
 */
export function passwordStrength(password) {
  const value = typeof password === 'string' ? password : ''
  /** @type {string[]} */
  const hints = []

  if (value === '') {
    return { score: 0, level: 'weak', label: '弱', hints: [`至少 ${MIN_PASSWORD_LENGTH} 位`] }
  }

  const classes = characterClasses(value)
  let score = 0

  if (value.length >= MIN_PASSWORD_LENGTH) score += 1
  else hints.push(`至少 ${MIN_PASSWORD_LENGTH} 位`)

  if (value.length >= 14) score += 1
  else if (value.length >= MIN_PASSWORD_LENGTH) hints.push('14 位以上更安全')

  if (classes >= 3) score += 2
  else if (classes === 2) score += 1
  else hints.push('混合大小写字母、数字与符号')

  if (/^(.)\1+$/.test(value)) {
    score = Math.min(score, 1)
    hints.push('避免重复字符')
  }

  const level = score >= 3 ? 'strong' : score === 2 ? 'medium' : 'weak'
  const label = level === 'strong' ? '强' : level === 'medium' ? '中' : '弱'
  return { score, level, label, hints }
}

/**
 * 新密码前端校验（不含确认密码一致性，那个由表单规则负责）。
 * @param {unknown} newPassword
 * @param {unknown} oldPassword
 * @returns {string[]}
 */
export function validateNewPassword(newPassword, oldPassword) {
  const value = typeof newPassword === 'string' ? newPassword : ''
  /** @type {string[]} */
  const errors = []

  if (value.length < MIN_PASSWORD_LENGTH) {
    errors.push(`新密码至少 ${MIN_PASSWORD_LENGTH} 位`)
  }
  if (typeof oldPassword === 'string' && oldPassword !== '' && value === oldPassword) {
    errors.push('新密码不能与旧密码相同')
  }
  if (value !== '' && /^\d+$/.test(value)) {
    errors.push('新密码不能全部为数字')
  }
  return errors
}
