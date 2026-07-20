import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

import { clearWithdrawalSecrets } from '../src/services/withdrawalSecrets.js'
import { buildSinglePayoutResult, isPayoutEligibleForExecution } from '../src/services/payoutOutcome.js'

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
    bepPrivateKey: 'bep-secret',
    totpCode: '123456',
  }

  clearWithdrawalSecrets(credentials)

  assert.deepEqual(credentials, {
    tronPrivateKey: '',
    ethPrivateKey: '',
    bepPrivateKey: '',
    totpCode: '',
  })
})

test('single payout UI never hard-codes a successful result', () => {
  const source = readFileSync(new URL('../src/views/WithdrawalsView.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(source, /succeeded:\s*1/)
})

test('payout execution eligibility excludes in-flight and terminal attempts', () => {
  const base = { status: 'PENDING' }
  assert.equal(isPayoutEligibleForExecution({ ...base, payoutStatus: null }), true)
  assert.equal(isPayoutEligibleForExecution({ ...base, payoutStatus: 'SIGNING' }), true)
  assert.equal(isPayoutEligibleForExecution({ ...base, payoutStatus: 'FAILED_RETRYABLE' }), true)
  for (const payoutStatus of ['PREPARED', 'BROADCASTED', 'MANUAL_REVIEW', 'CONFIRMED']) {
    assert.equal(isPayoutEligibleForExecution({ ...base, payoutStatus }), false, payoutStatus)
  }
  assert.equal(isPayoutEligibleForExecution({ status: 'APPROVED', payoutStatus: null }), false)

  const source = readFileSync(new URL('../src/views/WithdrawalsView.vue', import.meta.url), 'utf8')
  assert.match(source, /:selectable="isPayoutEligibleForExecution"/)
  assert.match(source, /selectedRows\.value\.filter\(isPayoutEligibleForExecution\)/)
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
    pending: 0,
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

test('prepared payout is pending rather than submitted or failed', () => {
  const result = buildSinglePayoutResult({
    id: 'withdrawal-3',
    status: 'PENDING',
    payoutStatus: 'PREPARED',
    payoutTxHash: '0xprepared',
    txHash: null,
    confirmationCount: 0,
    failureReason: null,
    manualReview: false,
  })

  assert.equal(result.succeeded, 0)
  assert.equal(result.failed, 0)
  assert.equal(result.pending, 1)
  assert.equal(result.items[0].errorMessage, null)
})

test('batch preview requires both withdrawal read and write permissions', () => {
  const source = readFileSync(
    new URL('../../backend/src/main/java/com/reelshort/backend/withdrawal/AdminWithdrawalController.java', import.meta.url),
    'utf8',
  )
  const match = source.match(/@PostMapping\("\/batch-preview"\)[\s\S]*?public ApiResponse/)

  assert.ok(match, 'batch preview endpoint must exist')
  assert.match(match[0], /WITHDRAWAL_READ/)
  assert.match(match[0], /WITHDRAWAL_WRITE/)
})

test('admin HTTP client allows long-running payout requests and handles timeout as uncertain', () => {
  const httpSource = readFileSync(new URL('../src/services/http.ts', import.meta.url), 'utf8')
  const viewSource = readFileSync(new URL('../src/views/WithdrawalsView.vue', import.meta.url), 'utf8')

  assert.match(httpSource, /timeout:\s*120000/)
  assert.match(httpSource, /502/)
  assert.match(httpSource, /504/)
  assert.match(viewSource, /isRequestTimeout/)
  assert.match(viewSource, /后台可能仍在处理/)
})

test('compose and host nginx keep payout execution routes open for 150 seconds', () => {
  const composeNginx = readFileSync(new URL('../nginx.conf', import.meta.url), 'utf8')
  const hostTemplateUrl = new URL('../../infra/nginx/shortlink-payout-timeouts.conf.template', import.meta.url)
  assert.ok(existsSync(hostTemplateUrl), 'host nginx payout timeout template must exist')
  const hostNginx = readFileSync(hostTemplateUrl, 'utf8')

  for (const source of [composeNginx, hostNginx]) {
    const match = source.match(/location\s+~\s+[^\n]*withdrawals[^\{]*\{[\s\S]*?\n\s*}/)
    assert.ok(match, 'payout-specific nginx location must exist')
    assert.match(match[0], /batch-approve/)
    assert.match(match[0], /approve/)
    assert.match(match[0], /proxy_read_timeout\s+150s/)
    assert.match(match[0], /proxy_send_timeout\s+150s/)
  }
})
