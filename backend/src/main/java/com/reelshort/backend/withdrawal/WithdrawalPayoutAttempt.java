package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "withdrawal_payout_attempts", uniqueConstraints = {
		@UniqueConstraint(name = "uk_withdrawal_payout_attempt_number",
				columnNames = {"withdrawal_request_id", "attempt_number"}),
		@UniqueConstraint(name = "uk_withdrawal_payout_attempt_active",
				columnNames = {"withdrawal_request_id", "active_slot"}),
		@UniqueConstraint(name = "uk_withdrawal_payout_attempt_tx_hash", columnNames = "tx_hash")
})
public class WithdrawalPayoutAttempt {

	@Id
	private UUID id;
	@Column(name = "withdrawal_request_id", nullable = false)
	private UUID withdrawalRequestId;
	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;
	@Column(nullable = false, length = 16)
	private String network;
	@Column(name = "hot_wallet_address", nullable = false, length = 128)
	private String hotWalletAddress;
	@Column(name = "destination_address", nullable = false, length = 128)
	private String destinationAddress;
	@Column(name = "token_contract_address", nullable = false, length = 128)
	private String tokenContractAddress;
	@Column(name = "token_amount", nullable = false, precision = 36, scale = 18)
	private BigDecimal tokenAmount;
	@Column(name = "chain_id", nullable = false)
	private long chainId;
	@Column(nullable = false, precision = 38, scale = 0)
	private BigInteger nonce;
	@Column(name = "gas_price", precision = 38, scale = 0)
	private BigInteger gasPrice;
	@Column(name = "signed_raw_transaction", columnDefinition = "text")
	private String signedRawTransaction;
	@Column(name = "tx_hash", length = 128, unique = true)
	private String txHash;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private WithdrawalPayoutStatus status;
	@Column(name = "active_slot", length = 24)
	private String activeSlot;
	@Column(name = "confirmation_count", nullable = false)
	private int confirmationCount;
	@Column(name = "failure_code", length = 64)
	private String failureCode;
	@Column(name = "failure_reason", length = 512)
	private String failureReason;
	@Column(name = "signing_owner", length = 64)
	private String signingOwner;
	@Column(name = "signing_lease_until")
	private OffsetDateTime signingLeaseUntil;
	@Column(name = "unknown_count", nullable = false)
	private int unknownCount;
	@Column(name = "unknown_first_seen")
	private OffsetDateTime unknownFirstSeen;
	@Column(name = "created_by", nullable = false, length = 64)
	private String createdBy;
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
	@Column(name = "prepared_at")
	private OffsetDateTime preparedAt;
	@Column(name = "broadcast_at")
	private OffsetDateTime broadcastAt;
	@Column(name = "confirmed_at")
	private OffsetDateTime confirmedAt;

	protected WithdrawalPayoutAttempt() {
	}

	public static WithdrawalPayoutAttempt signingIntent(UUID withdrawalRequestId, int attemptNumber,
			String network, String hotWalletAddress, String destinationAddress, String tokenContractAddress,
			BigDecimal tokenAmount, long chainId, BigInteger nonce, BigInteger gasPrice,
			String signingOwner, String createdBy, Duration signingLease) {
		WithdrawalPayoutAttempt attempt = new WithdrawalPayoutAttempt();
		OffsetDateTime now = OffsetDateTime.now();
		attempt.id = UUID.randomUUID();
		attempt.withdrawalRequestId = withdrawalRequestId;
		attempt.attemptNumber = attemptNumber;
		attempt.network = network;
		attempt.hotWalletAddress = hotWalletAddress;
		attempt.destinationAddress = destinationAddress;
		attempt.tokenContractAddress = tokenContractAddress;
		attempt.tokenAmount = tokenAmount;
		attempt.chainId = chainId;
		attempt.nonce = nonce;
		attempt.gasPrice = gasPrice;
		attempt.status = WithdrawalPayoutStatus.SIGNING;
		attempt.activeSlot = "ACTIVE";
		attempt.signingOwner = signingOwner;
		attempt.signingLeaseUntil = now.plus(signingLease);
		attempt.createdBy = createdBy;
		attempt.createdAt = now;
		attempt.updatedAt = now;
		return attempt;
	}

	public static WithdrawalPayoutAttempt prepared(UUID withdrawalRequestId, int attemptNumber,
			String destinationAddress, BigDecimal tokenAmount, PreparedPayoutTransaction signed, String createdBy) {
		String owner = "factory-" + UUID.randomUUID();
		WithdrawalPayoutAttempt attempt = signingIntent(withdrawalRequestId, attemptNumber, signed.network(),
				signed.hotWalletAddress(), destinationAddress, signed.tokenContractAddress(), tokenAmount,
				signed.chainId(), signed.nonce(), BigInteger.ZERO, owner, createdBy, Duration.ofMinutes(1));
		attempt.completeSigning(owner, signed);
		return attempt;
	}

	public void claimSigning(String owner, Duration lease) {
		if (status != WithdrawalPayoutStatus.SIGNING) {
			return;
		}
		OffsetDateTime now = OffsetDateTime.now();
		if (!owner.equals(signingOwner) && signingLeaseUntil != null && signingLeaseUntil.isAfter(now)) {
			return;
		}
		signingOwner = owner;
		signingLeaseUntil = now.plus(lease);
		updatedAt = now;
	}

	public void completeSigning(String owner, PreparedPayoutTransaction signed) {
		if (status != WithdrawalPayoutStatus.SIGNING || !owner.equals(signingOwner)) {
			throw new IllegalStateException("payout signing intent is not owned by caller");
		}
		if (!network.equals(signed.network()) || !hotWalletAddress.equalsIgnoreCase(signed.hotWalletAddress())
				|| !tokenContractAddress.equalsIgnoreCase(signed.tokenContractAddress())
				|| chainId != signed.chainId() || !nonce.equals(signed.nonce())) {
			throw new IllegalStateException("signed transaction does not match payout intent");
		}
		OffsetDateTime now = OffsetDateTime.now();
		signedRawTransaction = signed.signedRawTransaction();
		txHash = signed.txHash() == null ? null : signed.txHash().trim().toLowerCase(java.util.Locale.ROOT);
		status = WithdrawalPayoutStatus.PREPARED;
		signingOwner = null;
		signingLeaseUntil = null;
		preparedAt = now;
		updatedAt = now;
	}

	public boolean isSigningOwner(String owner) {
		return status == WithdrawalPayoutStatus.SIGNING && owner.equals(signingOwner);
	}

	public void markBroadcasted() {
		requireSignedActive();
		status = WithdrawalPayoutStatus.BROADCASTED;
		broadcastAt = OffsetDateTime.now();
		updatedAt = broadcastAt;
		failureCode = null;
		failureReason = null;
		clearUnknown();
	}

	public void markBroadcastUnknown(String reason, int maximumAttempts, Duration maximumAge) {
		requireSignedActive();
		OffsetDateTime now = OffsetDateTime.now();
		if (unknownFirstSeen == null) {
			unknownFirstSeen = now;
		}
		unknownCount++;
		failureCode = "BROADCAST_UNKNOWN";
		failureReason = truncate(reason);
		updatedAt = now;
		if (unknownCount >= maximumAttempts || !unknownFirstSeen.plus(maximumAge).isAfter(now)) {
			status = WithdrawalPayoutStatus.MANUAL_REVIEW;
			failureCode = "UNKNOWN_LIMIT_REACHED";
		}
		else {
			status = WithdrawalPayoutStatus.BROADCASTED;
		}
	}

	public void markFailedRetryable(String code, String reason) {
		requireActive();
		status = WithdrawalPayoutStatus.FAILED_RETRYABLE;
		activeSlot = null;
		signingOwner = null;
		signingLeaseUntil = null;
		failureCode = code;
		failureReason = truncate(reason);
		updatedAt = OffsetDateTime.now();
	}

	public void markManualReview(String reason) {
		requireActive();
		status = WithdrawalPayoutStatus.MANUAL_REVIEW;
		signingOwner = null;
		signingLeaseUntil = null;
		failureCode = "MANUAL_REVIEW";
		failureReason = truncate(reason);
		updatedAt = OffsetDateTime.now();
	}

	public void recordConfirmations(int confirmations) {
		if (confirmations < 0) {
			throw new IllegalArgumentException("confirmations must be non-negative");
		}
		confirmationCount = Math.max(confirmationCount, confirmations);
		updatedAt = OffsetDateTime.now();
		clearUnknown();
	}

	public void markConfirmed(int confirmations) {
		if (status == WithdrawalPayoutStatus.CONFIRMED) {
			return;
		}
		requireSignedActive();
		recordConfirmations(confirmations);
		status = WithdrawalPayoutStatus.CONFIRMED;
		activeSlot = null;
		confirmedAt = OffsetDateTime.now();
		updatedAt = confirmedAt;
		failureCode = null;
		failureReason = null;
	}

	private void requireSignedActive() {
		requireActive();
		if (status == WithdrawalPayoutStatus.SIGNING || signedRawTransaction == null || txHash == null) {
			throw new IllegalStateException("payout attempt has not been signed");
		}
	}

	private void requireActive() {
		if (activeSlot == null || status == WithdrawalPayoutStatus.CONFIRMED
				|| status == WithdrawalPayoutStatus.FAILED_RETRYABLE) {
			throw new IllegalStateException("payout attempt is not active");
		}
	}

	private void clearUnknown() {
		unknownCount = 0;
		unknownFirstSeen = null;
	}

	private String truncate(String reason) {
		if (reason == null) {
			return null;
		}
		return reason.length() <= 512 ? reason : reason.substring(0, 512);
	}

	public UUID id() { return id; }
	public UUID withdrawalRequestId() { return withdrawalRequestId; }
	public int attemptNumber() { return attemptNumber; }
	public String network() { return network; }
	public String hotWalletAddress() { return hotWalletAddress; }
	public String destinationAddress() { return destinationAddress; }
	public String tokenContractAddress() { return tokenContractAddress; }
	public BigDecimal tokenAmount() { return tokenAmount; }
	public long chainId() { return chainId; }
	public BigInteger nonce() { return nonce; }
	public BigInteger gasPrice() { return gasPrice; }
	public String signedRawTransaction() { return signedRawTransaction; }
	public String txHash() { return txHash; }
	public WithdrawalPayoutStatus status() { return status; }
	public String activeSlot() { return activeSlot; }
	public int confirmationCount() { return confirmationCount; }
	public int unknownCount() { return unknownCount; }
	public String failureCode() { return failureCode; }
	public String failureReason() { return failureReason; }
	public String createdBy() { return createdBy; }
}
