const SUBMITTED_STATUSES = new Set(['PREPARED', 'BROADCASTED', 'CONFIRMED'])

export function isSubmittedPayoutStatus(status) {
  return SUBMITTED_STATUSES.has(status)
}

export function buildSinglePayoutResult(withdrawal) {
  const payoutStatus = withdrawal.payoutStatus ?? withdrawal.status
  const submitted = isSubmittedPayoutStatus(payoutStatus)
  const manualReview = payoutStatus === 'MANUAL_REVIEW' || withdrawal.manualReview
  const failureReason = submitted
    ? withdrawal.failureReason
    : withdrawal.failureReason || (manualReview ? 'payout requires manual review' : 'payout was not submitted')

  return {
    succeeded: submitted ? 1 : 0,
    failed: submitted ? 0 : 1,
    stoppedAtIndex: submitted ? -1 : 0,
    errorMessage: submitted ? null : failureReason,
    items: [{
      withdrawalId: withdrawal.id,
      payoutStatus,
      txHash: withdrawal.payoutTxHash ?? withdrawal.txHash,
      confirmationCount: withdrawal.confirmationCount,
      failureReason,
      manualReview,
      errorMessage: submitted ? null : failureReason,
    }],
  }
}
