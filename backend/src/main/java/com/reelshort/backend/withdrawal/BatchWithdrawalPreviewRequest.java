package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Preview request for batch withdrawal payout. Needs the selected withdrawal IDs and the hot wallet
 * private key (provided per-call, not stored) to query current balances.
 */
public record BatchWithdrawalPreviewRequest(
		@NotEmpty List<UUID> withdrawalIds,
		@NotBlank @Size(max = 128) String hotWalletPrivateKey) {
}
