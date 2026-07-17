import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import { clearWithdrawalSecrets } from '../src/services/withdrawalSecrets.js'

test('batch preview request never contains private keys', () => {
  const source = readFileSync(new URL('../src/services/adminApi.ts', import.meta.url), 'utf8')
  const match = source.match(
    /export async function batchPreviewWithdrawals[\s\S]*?return response\.data\.data\s*\n}/,
  )

  assert.ok(match, 'batchPreviewWithdrawals function must exist')
  assert.doesNotMatch(match[0], /PrivateKey/)
})

test('credential cleanup clears both chain keys and TOTP in place', () => {
  const credentials = {
    tronPrivateKey: 'tron-secret',
    ethPrivateKey: 'eth-secret',
    totpCode: '123456',
  }

  clearWithdrawalSecrets(credentials)

  assert.deepEqual(credentials, { tronPrivateKey: '', ethPrivateKey: '', totpCode: '' })
})
