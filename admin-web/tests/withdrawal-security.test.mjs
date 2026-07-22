import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const apiSource = readFileSync(new URL('../src/services/adminApi.ts', import.meta.url), 'utf8')
const viewSource = readFileSync(new URL('../src/views/WithdrawalsView.vue', import.meta.url), 'utf8')
const usersViewSource = readFileSync(new URL('../src/views/UsersView.vue', import.meta.url), 'utf8')
const configLabelsSource = readFileSync(new URL('../src/views/systemConfigLabels.ts', import.meta.url), 'utf8')
const configViewSource = readFileSync(new URL('../src/views/SystemConfigsView.vue', import.meta.url), 'utf8')

test('manual withdrawal confirmation sends no second-factor data', () => {
  const match = apiSource.match(
    /export async function manualConfirmWithdrawal[\s\S]*?return response\.data\.data\s*\n}/,
  )

  assert.ok(match, 'manual confirmation API must exist')
  assert.match(match[0], /\/withdrawals\/\$\{withdrawalId\}\/manual-confirm/)
  assert.doesNotMatch(match[0], /totpCode|PrivateKey|txHash|batch/i)
})

test('withdrawal page removes all automatic signing and batch payout controls', () => {
  for (const forbidden of [
    /batchPreviewWithdrawals/,
    /batchApproveWithdrawals/,
    /approveWithdrawal/,
    /PrivateKey/,
    /确认并签名打款/,
    /批量打款/,
    /链上 tx hash/,
  ]) {
    assert.doesNotMatch(viewSource, forbidden)
  }
  assert.match(viewSource, /确认已外部打款/)
  assert.match(viewSource, /manualConfirmWithdrawal/)
  assert.doesNotMatch(viewSource, /totpCode|totpEnabled|2FA 验证码/)
})

test('withdrawal stats API supports all preset time ranges', () => {
  assert.match(apiSource, /export type WithdrawalStatsRange = 'TODAY' \| 'YESTERDAY' \| 'THIS_WEEK' \| 'THIS_MONTH' \| 'LAST_MONTH'/)
  assert.match(apiSource, /export async function fetchWithdrawalStats\(range: WithdrawalStatsRange\)/)
  assert.match(apiSource, /params: \{ range \}/)

  for (const label of ['今天', '昨天', '本周', '本月', '上月']) {
    assert.match(viewSource, new RegExp(label))
  }
  assert.match(viewSource, /打款笔数/)
  assert.match(viewSource, /打款金额（USDT）/)
})

test('manual confirmation remains an explicit external payout action', () => {
  assert.match(viewSource, /确认外部 ERC20 打款/)
  assert.match(viewSource, /确认已外部打款/)
  assert.match(viewSource, /不会广播或核验链上交易/)
})

test('system config exposes direct USDT rate without legacy currency conversion', () => {
  assert.match(configLabelsSource, /withdraw\.usdt-per-50-points/)
  assert.match(configLabelsSource, /提现 · 每50积分USDT/)
  assert.doesNotMatch(configLabelsSource, /withdraw\.cny-per-point|withdraw\.cny-per-usd|withdraw\.minimum-usd/)
  assert.doesNotMatch(configViewSource, /withdraw\.cny-per-point|withdraw\.cny-per-usd|withdraw\.minimum-usd/)
})

test('user VIP operations only submit an expiry timestamp or an empty cancel request', () => {
  const setMatch = apiSource.match(
    /export async function setUserVip[\s\S]*?return response\.data\.data\s*\n}/,
  )
  const cancelMatch = apiSource.match(
    /export async function cancelUserVip[\s\S]*?return response\.data\.data\s*\n}/,
  )

  assert.ok(setMatch, 'setUserVip API must exist')
  assert.ok(cancelMatch, 'cancelUserVip API must exist')
  assert.match(setMatch[0], /\/users\/\$\{userId\}\/vip/)
  assert.match(setMatch[0], /\{ vipUntil \}/)
  assert.match(cancelMatch[0], /\/users\/\$\{userId\}\/vip\/cancel/)
  assert.doesNotMatch(`${setMatch[0]}${cancelMatch[0]}`, /totpCode|txHash|orderId|privateKey/i)
  assert.match(usersViewSource, /setUserVip/)
  assert.match(usersViewSource, /cancelUserVip/)
  assert.match(usersViewSource, /type="datetime"/)
})

test('cancelled point transfers are absent from user detail contracts', () => {
  assert.doesNotMatch(apiSource, /PointTransfer|fetchUserPointTransfers|point-transfers/)
  assert.doesNotMatch(usersViewSource, /积分交易|pointTransfers|fetchUserPointTransfers|pointTransferRecordCount/)
})
