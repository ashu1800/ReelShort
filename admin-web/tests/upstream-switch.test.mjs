import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const view = readFileSync(
  new URL('../src/views/SystemConfigsView.vue', import.meta.url),
  'utf8',
)
const labels = readFileSync(
  new URL('../src/views/systemConfigLabels.ts', import.meta.url),
  'utf8',
)
const registry = readFileSync(
  new URL('../../backend/src/main/java/com/reelshort/backend/system/config/SystemConfigRegistry.java', import.meta.url),
  'utf8',
)

test('content upstream is switchable from the admin config page', () => {
  // 前端只能提交后端枚举里存在的两个值：手输会绕过校验，也写不出第三个上游。
  assert.match(view, /content\.upstream/)
  assert.match(view, /value: 'TWISTYTV'/)
  assert.match(view, /value: 'REELSHORT'/)
  assert.match(view, /isUpstreamConfig\(row\.key\)/)
  assert.match(view, /el-select/)
})

test('switching upstream asks for confirmation before saving', () => {
  assert.match(view, /confirmUpstreamSwitch/)
  assert.match(view, /isUpstreamConfig\(config\.key\) && !\(await confirmUpstreamSwitch/)
})

test('backend keeps the same two allowed values and defaults to twistytv', () => {
  assert.match(registry, /CONTENT_UPSTREAM = "content\.upstream"/)
  assert.match(registry, /DEFAULT_CONTENT_UPSTREAM = "twistytv"/)
  assert.match(registry, /Set\.of\("TWISTYTV", "REELSHORT"\)/)
})

test('the admin label explains what switching does', () => {
  assert.match(labels, /'content\.upstream'/)
  assert.match(labels, /内容上游/)
})
