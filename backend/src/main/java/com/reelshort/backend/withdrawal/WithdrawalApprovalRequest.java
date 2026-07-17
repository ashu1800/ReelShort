package com.reelshort.backend.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalApprovalRequest(
		@NotBlank @Size(max = 128) String txHash,
		@Size(max = 255) String note,
		@NotBlank @Size(min = 6, max = 6) String totpCode) {
}
