const SUBMITTED_STATUSES = new Set(['BROADCASTED', 'CONFIRMED'])

export function isSubmittedPayoutStatus(status) {
  return SUBMITTED_STATUSES.has(status)
}

export function buildSinglePayoutResult(withdrawal) {
  const payoutStatus = withdrawal.payoutStatus ?? withdrawal.status
  const submitted = isSubmittedPayoutStatus(payoutStatus)
  const pending = payoutStatus === 'PREPARED'
  const manualReview = payoutStatus === 'MANUAL_REVIEW' || withdrawal.manualReview
  const failureReason = submitted || pending
    ? withdrawal.failureReason
    : withdrawal.failureReason || (manualReview ? 'payout requires manual review' : 'payout was not submitted')

  return {
    succeeded: submitted ? 1 : 0,
    failed: submitted || pending ? 0 : 1,
    pending: pending ? 1 : 0,
    stoppedAtIndex: submitted || pending ? -1 : 0,
    errorMessage: submitted || pending ? null : failureReason,
    items: [{
      withdrawalId: withdrawal.id,
      payoutStatus,
      txHash: withdrawal.payoutTxHash ?? withdrawal.txHash,
      confirmationCount: withdrawal.confirmationCount,
      failureReason,
      manualReview,
      errorMessage: submitted || pending ? null : failureReason,
    }],
  }
}
