import type { BatchWithdrawalResult, WithdrawalRequest } from './adminApi'

export function isSubmittedPayoutStatus(status: string): boolean
export function buildSinglePayoutResult(withdrawal: WithdrawalRequest): BatchWithdrawalResult
