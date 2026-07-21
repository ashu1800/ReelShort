package com.reelshort.backend.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ManualWithdrawalConfirmRequest(
		@NotBlank @Pattern(regexp = "\\d{6}") String totpCode) {
}
