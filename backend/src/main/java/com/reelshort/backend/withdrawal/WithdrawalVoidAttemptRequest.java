package com.reelshort.backend.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for voiding a stuck automatic payout attempt.
 *
 * @param reason 作废原因，必填，写入审计
 */
public record WithdrawalVoidAttemptRequest(
		@NotBlank @Size(max = 255) String reason) {
}
