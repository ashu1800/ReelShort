package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.BigInteger;
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

	@Column(name = "signed_raw_transaction", nullable = false, columnDefinition = "text")
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

	public static WithdrawalPayoutAttempt prepared(UUID withdrawalRequestId, int attemptNumber,
			String destinationAddress, BigDecimal tokenAmount, PreparedPayoutTransaction signed, String createdBy) {
		WithdrawalPayoutAttempt attempt = new WithdrawalPayoutAttempt();
		OffsetDateTime now = OffsetDateTime.now();
		attempt.id = UUID.randomUUID();
		attempt.withdrawalRequestId = withdrawalRequestId;
		attempt.attemptNumber = attemptNumber;
		attempt.network = signed.network();
		attempt.hotWalletAddress = signed.hotWalletAddress();
		attempt.destinationAddress = destinationAddress;
		attempt.tokenContractAddress = signed.tokenContractAddress();
		attempt.tokenAmount = tokenAmount;
		attempt.chainId = signed.chainId();
		attempt.nonce = signed.nonce();
		attempt.signedRawTransaction = signed.signedRawTransaction();
		attempt.txHash = signed.txHash();
		attempt.status = WithdrawalPayoutStatus.PREPARED;
		attempt.activeSlot = "ACTIVE";
		attempt.createdBy = createdBy;
		attempt.createdAt = now;
		attempt.updatedAt = now;
		attempt.preparedAt = now;
		return attempt;
	}

	public void markBroadcasted() {
		requireActive();
		status = WithdrawalPayoutStatus.BROADCASTED;
		broadcastAt = OffsetDateTime.now();
		updatedAt = broadcastAt;
		failureCode = null;
		failureReason = null;
	}

	public void markBroadcastUnknown(String reason) {
		requireActive();
		status = WithdrawalPayoutStatus.BROADCASTED;
		failureCode = "BROADCAST_UNKNOWN";
		failureReason = truncate(reason);
		updatedAt = OffsetDateTime.now();
	}

	public void markFailedRetryable(String code, String reason) {
		requireActive();
		status = WithdrawalPayoutStatus.FAILED_RETRYABLE;
		activeSlot = null;
		failureCode = code;
		failureReason = truncate(reason);
		updatedAt = OffsetDateTime.now();
	}

	public void markManualReview(String reason) {
		requireActive();
		status = WithdrawalPayoutStatus.MANUAL_REVIEW;
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
	}

	public void markConfirmed(int confirmations) {
		if (status == WithdrawalPayoutStatus.CONFIRMED) {
			return;
		}
		requireActive();
		recordConfirmations(confirmations);
		status = WithdrawalPayoutStatus.CONFIRMED;
		activeSlot = null;
		confirmedAt = OffsetDateTime.now();
		updatedAt = confirmedAt;
		failureCode = null;
		failureReason = null;
	}

	private void requireActive() {
		if (activeSlot == null || status == WithdrawalPayoutStatus.CONFIRMED
				|| status == WithdrawalPayoutStatus.FAILED_RETRYABLE) {
			throw new IllegalStateException("payout attempt is not active");
		}
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
	public String signedRawTransaction() { return signedRawTransaction; }
	public String txHash() { return txHash; }
	public WithdrawalPayoutStatus status() { return status; }
	public String activeSlot() { return activeSlot; }
	public int confirmationCount() { return confirmationCount; }
	public String failureCode() { return failureCode; }
	public String failureReason() { return failureReason; }
	public String createdBy() { return createdBy; }
}
