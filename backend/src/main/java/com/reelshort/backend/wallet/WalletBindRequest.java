package com.reelshort.backend.wallet;

import jakarta.validation.constraints.NotBlank;

public record WalletBindRequest(
		@NotBlank String walletAddress) {
}
