/**
 * 前端契约单测（node --test，纯函数，不需要浏览器）。
 * 校验展示层与后端契约（docs/API.md）的一致性：错误包络、状态标签、
 * 时长/剩余时间格式化、CSV 导出内容、系统设置校验。
 */
import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  API_ERROR_MESSAGES,
  DEFAULT_ERROR_CODE,
  DEFAULT_ERROR_MESSAGE,
  extractApiError,
  requiresAdminCsrfHeader,
} from '../src/utils/envelope.mjs'
import {
  actionLabel,
  auditActionLabel,
  auditTargetLabel,
  closeReasonLabel,
  displayStateLabel,
  displayStateTagType,
  resultLabel,
  resultTagType,
  sessionStateLabel,
  storageStateLabel,
} from '../src/utils/labels.mjs'
import {
  dayRangeToCardCreated,
  dayRangeToTimestamps,
  formatDateTime,
  formatDurationText,
  formatExpiry,
  formatInteger,
  formatMinutes,
  formatRemaining,
  groupCardCode,
  maskCardCode,
  maskMachineCode,
  normalizeCardCode,
} from '../src/utils/format.mjs'
import {
  SETTING_FIELDS,
  diffSettings,
  formatSettingValue,
  validateSettingsDraft,
} from '../src/utils/settings-fields.mjs'
import {
  buildCardsCsv,
  buildCodesCsv,
  buildCodesTxt,
  displayCardCode,
  exportFileName,
  withUtf8Bom,
} from '../src/utils/export.mjs'

/* ------------------------------ 错误包络 ------------------------------ */

test('extractApiError 解析契约错误包络', () => {
  const { code, message } = extractApiError({
    error: { code: 'conflict', message: '该类型下仍有 3 张卡密，不能删除' },
  })
  assert.equal(code, 'conflict')
  assert.equal(message, '该类型下仍有 3 张卡密，不能删除')
})

test('extractApiError 在缺少 message 时使用契约兜底文案', () => {
  const { code, message } = extractApiError({ error: { code: 'rate_limited' } })
  assert.equal(code, 'rate_limited')
  assert.equal(message, API_ERROR_MESSAGES.rate_limited)
})

test('extractApiError 对非包络载荷回退到兜底值', () => {
  for (const payload of [undefined, null, 'oops', '<html>502</html>', [1, 2, 3]]) {
    const { code, message } = extractApiError(payload, 'internal_error')
    assert.equal(code, 'internal_error')
    assert.equal(message, DEFAULT_ERROR_MESSAGE)
  }
  assert.equal(DEFAULT_ERROR_CODE, 'internal_error')
})

test('requiresAdminCsrfHeader 只对写操作放行', () => {
  for (const method of ['POST', 'put', 'Patch', 'DELETE']) {
    assert.equal(requiresAdminCsrfHeader(method), true)
  }
  for (const method of ['GET', 'HEAD', undefined]) {
    assert.equal(requiresAdminCsrfHeader(method), false)
  }
})

/* ------------------------------ 标签映射 ------------------------------ */

test('卡密状态标签与 Tag 语义类型', () => {
  assert.equal(displayStateLabel('unused'), '未激活')
  assert.equal(displayStateLabel('active'), '正常')
  assert.equal(displayStateLabel('expired'), '已过期')
  assert.equal(displayStateLabel('disabled'), '已禁用')
  assert.equal(displayStateLabel('weird_state'), 'weird_state', '未知枚举原样展示便于排障')
  assert.equal(storageStateLabel('unused'), '未激活')
  assert.equal(displayStateTagType('active'), 'success')
  assert.equal(displayStateTagType('unknown'), 'info')
})

test('动作、结果、关闭原因与会话状态标签', () => {
  assert.equal(actionLabel('verify'), '校验卡密')
  assert.equal(actionLabel('admin_kick'), '强制下线')
  assert.equal(resultLabel('ok'), '成功')
  assert.equal(resultLabel('fail'), '失败')
  assert.equal(resultTagType('fail'), 'danger')
  assert.equal(closeReasonLabel(''), '—')
  assert.equal(closeReasonLabel('kicked'), '被其他机器顶号')
  assert.equal(sessionStateLabel(true), '在线')
  assert.equal(sessionStateLabel(false), '已关闭')
})

test('审计日志标签与目标类型标签', () => {
  assert.equal(auditActionLabel('card_batch_create'), '批量生成卡密')
  assert.equal(auditActionLabel('settings_update'), '修改系统设置')
  assert.equal(auditActionLabel('brand_new_action'), 'brand_new_action')
  assert.equal(auditTargetLabel('card'), '卡密')
  assert.equal(auditTargetLabel(''), '—')
})

/* ------------------------------ 格式化 ------------------------------ */

test('卡密归一化、分组与打码', () => {
  assert.equal(normalizeCardCode('abcd-efgh-ijkl-mnpq'), 'ABCDEFGHIJKLMNPQ')
  assert.equal(groupCardCode('ABCDEFGHIJKLMNPQ'), 'ABCD-EFGH-IJKL-MNPQ')
  assert.equal(maskCardCode('ABCDEFGHIJKLMNPQ'), 'ABCD-****-****-MNPQ')
  assert.equal(maskCardCode(''), '')
  assert.equal(displayCardCode('abcdefghijklmnpq'), 'ABCD-EFGH-IJKL-MNPQ')
  assert.equal(maskMachineCode('9F2C4A1B8E7D6530'), '9F2C****6530')
})

test('时长与剩余时间格式化', () => {
  assert.equal(formatMinutes(1440), '1 天')
  assert.equal(formatMinutes(90), '1 小时 30 分钟')
  assert.equal(formatMinutes(0), '0 分钟')
  assert.equal(formatDurationText(0), '永久', 'duration_minutes=0 表示永久')
  assert.equal(formatDurationText(43200, '30 天'), '30 天')
  assert.equal(formatRemaining(0, true), '永久有效')
  assert.equal(formatRemaining(0, false), '已到期')
  assert.equal(formatRemaining(3600, false), '1 小时 0 分钟')
  assert.equal(formatExpiry('', true), '永久有效')
  assert.equal(formatExpiry('2026-11-12 20:00:00', false), '2026-11-12 20:00:00')
  assert.equal(formatDateTime(''), '—')
  assert.equal(formatInteger(1234567), '1,234,567')
})

test('日期区间 → 接口参数', () => {
  assert.deepEqual(dayRangeToTimestamps(['2026-10-01', '2026-10-02']), {
    from: '2026-10-01 00:00:00',
    to: '2026-10-02 23:59:59',
  })
  assert.deepEqual(dayRangeToTimestamps(null), {})
  assert.deepEqual(dayRangeToCardCreated(['2026-10-01', '2026-10-02']), {
    created_from: '2026-10-01',
    created_to: '2026-10-02',
  })
  assert.deepEqual(dayRangeToCardCreated([]), {})
})

/* ------------------------------ 系统设置 ------------------------------ */

test('系统设置字段与契约数量一致', () => {
  assert.equal(SETTING_FIELDS.length, 10)
  const keys = SETTING_FIELDS.map((field) => field.key)
  assert.deepEqual(keys, [
    'heartbeat_interval_seconds',
    'session_timeout_seconds',
    'allow_takeover',
    'default_max_rebinds',
    'activate_on',
    'log_heartbeat_success',
    'rate_limit_ip_per_minute',
    'rate_limit_card_per_minute',
    'rate_limit_session_per_minute',
    'admin_session_hours',
  ])
})

test('设置草稿校验：会话超时必须不小于 2 倍心跳间隔', () => {
  const draft = {
    heartbeat_interval_seconds: 30,
    session_timeout_seconds: 45,
    allow_takeover: true,
    default_max_rebinds: 3,
    activate_on: 'verify',
    log_heartbeat_success: false,
    rate_limit_ip_per_minute: 300,
    rate_limit_card_per_minute: 60,
    rate_limit_session_per_minute: 120,
    admin_session_hours: 12,
  }
  const errors = validateSettingsDraft(draft)
  assert.equal(errors.length, 1)
  assert.equal(errors[0].key, 'session_timeout_seconds')

  assert.deepEqual(validateSettingsDraft({ ...draft, session_timeout_seconds: 180 }), [])
})

test('设置草稿校验：越界与类型错误', () => {
  const base = {
    heartbeat_interval_seconds: 30,
    session_timeout_seconds: 180,
    allow_takeover: true,
    default_max_rebinds: 3,
    activate_on: 'verify',
    log_heartbeat_success: false,
    rate_limit_ip_per_minute: 300,
    rate_limit_card_per_minute: 60,
    rate_limit_session_per_minute: 120,
    admin_session_hours: 12,
  }
  assert.equal(validateSettingsDraft({ ...base, heartbeat_interval_seconds: 5 }).length, 1)
  assert.equal(validateSettingsDraft({ ...base, default_max_rebinds: 200 }).length, 1)
  assert.equal(validateSettingsDraft({ ...base, activate_on: 'nope' }).length, 1)
  assert.equal(validateSettingsDraft({ ...base, allow_takeover: 'yes' }).length, 1)
})

test('设置差异计算包含旧值与新值文案', () => {
  const current = {
    heartbeat_interval_seconds: 30,
    session_timeout_seconds: 180,
    allow_takeover: true,
    default_max_rebinds: 3,
    activate_on: 'verify',
    log_heartbeat_success: false,
    rate_limit_ip_per_minute: 300,
    rate_limit_card_per_minute: 60,
    rate_limit_session_per_minute: 120,
    admin_session_hours: 12,
  }
  const draft = { ...current, session_timeout_seconds: 240, allow_takeover: false }
  const changes = diffSettings(current, draft)
  assert.equal(changes.length, 2)
  assert.deepEqual(changes.map((change) => change.key), ['session_timeout_seconds', 'allow_takeover'])
  assert.equal(changes[0].oldText, '180')
  assert.equal(changes[1].newText, '关闭')
  assert.equal(formatSettingValue(SETTING_FIELDS[4], 'login'), '上线时激活')
})

/* ------------------------------ 导出内容 ------------------------------ */

test('批量卡密导出内容（CSV/TXT）', () => {
  const codes = ['ABCDEFGHIJKLMNPQ', 'MNPQABCDEFGHIJKL']
  const csv = buildCodesCsv(codes)
  assert.ok(csv.startsWith('卡密\r\n'), 'CSV 首行应为表头')
  assert.ok(csv.includes('ABCD-EFGH-IJKL-MNPQ'))
  const txt = buildCodesTxt(codes)
  assert.equal(txt, 'ABCD-EFGH-IJKL-MNPQ\r\nMNPQ-ABCD-EFGH-IJKL\r\n')
  assert.equal(buildCodesTxt([]), '')
  assert.ok(withUtf8Bom(csv).startsWith('\uFEFF'))
})

test('卡密列表导出 CSV 处理永久卡、逗号与引号', () => {
  const csv = buildCardsCsv([
    {
      code: 'ABCDEFGHIJKLMNPQ',
      type_name: '月卡',
      display_state: 'active',
      bound_machine: 'AA,BB',
      activated_at: '2026-10-13 10:00:00',
      expires_at: '',
      permanent: true,
      rebind_count: 1,
      max_rebinds: 3,
      batch_no: 'B20261013-0001',
      remark: '包含"引号"的备注',
      created_at: '2026-10-13 09:00:00',
    },
  ])
  const header = csv.split('\r\n')[0]
  assert.equal(header, '卡密,类型,状态,绑定机器码,激活时间,到期时间,换绑次数,批次,备注,创建时间')
  assert.ok(csv.includes('永久有效'))
  assert.ok(csv.includes('"AA,BB"'), '含逗号字段必须加引号')
  assert.ok(csv.includes('"包含""引号""的备注"'), '内部引号必须双写转义')
  assert.ok(csv.includes('1/3'))
})

test('导出文件名格式', () => {
  const name = exportFileName(new Date(2026, 9, 13, 8, 5, 9), 'csv')
  assert.equal(name, 'cards-20261013-080509.csv')
})
