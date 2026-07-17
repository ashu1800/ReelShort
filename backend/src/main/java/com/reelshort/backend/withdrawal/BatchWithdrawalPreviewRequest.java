package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Preview request for batch withdrawal payout. Needs the selected withdrawal IDs and optionally
 * the hot wallet private keys for each chain (provided per-call, not stored) to query balances.
 */
public record BatchWithdrawalPreviewRequest(
		@NotEmpty List<UUID> withdrawalIds,
		@Size(max = 128) String tronPrivateKey,
		@Size(max = 128) String ethPrivateKey) {
}
