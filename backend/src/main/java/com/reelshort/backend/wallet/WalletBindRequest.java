package com.reelshort.backend.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletBindRequest(
		@NotBlank @Pattern(regexp = "TRC20|ERC20", message = "network must be TRC20 or ERC20") String network,
		@NotBlank String walletAddress) {
}
