package com.reelshort.backend.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WalletBindRequest(
		@NotBlank @Pattern(regexp = "TRC20|ERC20", message = "network must be TRC20 or ERC20") String network,
		@NotBlank @Size(max = 128) String walletAddress,
		@NotBlank @Size(max = 128) String password) {
}
