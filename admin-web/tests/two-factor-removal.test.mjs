import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const sources = [
  '../src/App.vue',
  '../src/router/index.ts',
  '../src/services/adminApi.ts',
  '../src/views/LoginView.vue',
  '../src/views/VipOrdersView.vue',
].map((path) => readFileSync(new URL(path, import.meta.url), 'utf8')).join('\n')

test('admin web contains no two-factor UI, route, or request contract', () => {
  assert.doesNotMatch(sources, /2fa|totp|TwoFactor|two-factor/i)
})
