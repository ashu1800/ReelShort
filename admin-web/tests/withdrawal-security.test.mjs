import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import { clearWithdrawalSecrets } from '../src/services/withdrawalSecrets.js'
import { buildSinglePayoutResult } from '../src/services/payoutOutcome.js'

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

test('single payout UI never hard-codes a successful result', () => {
  const source = readFileSync(new URL('../src/views/WithdrawalsView.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(source, /succeeded:\s*1/)
})

test('single payout result treats only submitted statuses as success', () => {
  const withdrawal = {
    id: 'withdrawal-1',
    status: 'PENDING',
    payoutStatus: 'FAILED_RETRYABLE',
    payoutTxHash: '0xfailed',
    txHash: null,
    confirmationCount: 0,
    failureReason: 'node rejected transaction',
    manualReview: false,
  }

  assert.deepEqual(buildSinglePayoutResult(withdrawal), {
    succeeded: 0,
    failed: 1,
    stoppedAtIndex: 0,
    errorMessage: 'node rejected transaction',
    items: [{
      withdrawalId: 'withdrawal-1',
      payoutStatus: 'FAILED_RETRYABLE',
      txHash: '0xfailed',
      confirmationCount: 0,
      failureReason: 'node rejected transaction',
      manualReview: false,
      errorMessage: 'node rejected transaction',
    }],
  })

  withdrawal.payoutStatus = 'CONFIRMED'
  withdrawal.failureReason = null
  assert.equal(buildSinglePayoutResult(withdrawal).succeeded, 1)
})

test('single manual-review result is visible and never successful', () => {
  const result = buildSinglePayoutResult({
    id: 'withdrawal-2',
    status: 'PENDING',
    payoutStatus: 'MANUAL_REVIEW',
    payoutTxHash: '0xunknown',
    txHash: null,
    confirmationCount: 0,
    failureReason: 'chain state is ambiguous',
    manualReview: true,
  })

  assert.equal(result.succeeded, 0)
  assert.equal(result.failed, 1)
  assert.equal(result.items[0].manualReview, true)
})
