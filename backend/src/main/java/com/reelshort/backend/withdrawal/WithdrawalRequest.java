package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "point_amount", nullable = false)
	private int pointAmount;

	@Column(name = "fee_amount", nullable = false)
	private int feeAmount;

	@Column(name = "usdt_amount", nullable = false, precision = 18, scale = 6)
	private BigDecimal usdtAmount;

	@Column(name = "usdt_per_point", nullable = false, precision = 18, scale = 8)
	private BigDecimal usdtPerPoint;

	@Column(name = "cny_per_point", precision = 18, scale = 8)
	private BigDecimal cnyPerPoint;

	@Column(name = "cny_per_usd", precision = 18, scale = 8)
	private BigDecimal cnyPerUsd;

	@Column(name = "minimum_usd", precision = 18, scale = 2)
	private BigDecimal minimumUsd;

	@Column(nullable = false, length = 16)
	private String network;

	@Column(name = "wallet_address", nullable = false, length = 128)
	private String walletAddress;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private WithdrawalStatus status;

	@Column(name = "tx_hash", length = 128)
	private String txHash;

	@Column(name = "admin_note", length = 255)
	private String adminNote;

	@Column(name = "reviewed_by", length = 64)
	private String reviewedBy;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "reviewed_at")
	private OffsetDateTime reviewedAt;

	protected WithdrawalRequest() {
	}

	private WithdrawalRequest(UUID id, UUID userId, int pointAmount, int feeAmount, BigDecimal usdtAmount,
			BigDecimal usdtPerPoint, BigDecimal cnyPerPoint, BigDecimal cnyPerUsd, BigDecimal minimumUsd,
			String network, String walletAddress, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.pointAmount = pointAmount;
		this.feeAmount = feeAmount;
		this.usdtAmount = usdtAmount;
		this.usdtPerPoint = usdtPerPoint;
		this.cnyPerPoint = cnyPerPoint;
		this.cnyPerUsd = cnyPerUsd;
		this.minimumUsd = minimumUsd;
		this.network = network;
		this.walletAddress = walletAddress;
		this.status = WithdrawalStatus.PENDING;
		this.createdAt = createdAt;
	}

	public static WithdrawalRequest create(UUID userId, int pointAmount, int feeAmount,
			WithdrawalConversion conversion, String network, String walletAddress) {
		BigDecimal normalizedRate = conversion.usdtPerPoint().setScale(8, RoundingMode.UNNECESSARY);
		return new WithdrawalRequest(UUID.randomUUID(), userId, pointAmount, feeAmount,
				conversion.usdtAmount(pointAmount),
				normalizedRate, conversion.cnyPerPoint().setScale(8, RoundingMode.UNNECESSARY),
				conversion.cnyPerUsd().setScale(8, RoundingMode.UNNECESSARY),
				conversion.minimumUsd().setScale(2, RoundingMode.UNNECESSARY), network, walletAddress,
				OffsetDateTime.now());
	}

	public void approve(String txHash, String note, String reviewedBy) {
		if (status != WithdrawalStatus.PENDING) {
			throw new IllegalStateException("withdrawal is not pending");
		}
		this.status = WithdrawalStatus.APPROVED;
		this.txHash = txHash;
		this.adminNote = note;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = OffsetDateTime.now();
	}

	public void reject(String reason, String reviewedBy) {
		if (status != WithdrawalStatus.PENDING) {
			throw new IllegalStateException("withdrawal is not pending");
		}
		this.status = WithdrawalStatus.REJECTED;
		this.adminNote = reason;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = OffsetDateTime.now();
	}

	/**
	 * H2: 广播失败时标记为 BROADCAST_FAILED。积分已扣减，需人工对账（退款或重试）。
	 */
	public void markBroadcastFailed(String reason, String reviewedBy) {
		if (status != WithdrawalStatus.PENDING) {
			throw new IllegalStateException("withdrawal is not pending for broadcast");
		}
		this.status = WithdrawalStatus.BROADCAST_FAILED;
		this.adminNote = reason;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = OffsetDateTime.now();
	}

	/**
	 * H2: 广播成功后从 BROADCAST_FAILED 恢复为 APPROVED（人工补登记 txHash）。
	 */
	public void markApprovedFromBroadcast(String txHash, String note, String reviewedBy) {
		if (status != WithdrawalStatus.BROADCAST_FAILED) {
			throw new IllegalStateException("withdrawal is not in broadcast-failed state");
		}
		this.status = WithdrawalStatus.APPROVED;
		this.txHash = txHash;
		this.adminNote = note;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = OffsetDateTime.now();
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public int pointAmount() {
		return pointAmount;
	}

	public int feeAmount() {
		return feeAmount;
	}

	public int totalDeductedPoints() {
		return pointAmount + feeAmount;
	}

	public BigDecimal usdtAmount() {
		return usdtAmount;
	}

	public BigDecimal usdtPerPoint() {
		return usdtPerPoint;
	}

	public BigDecimal cnyPerPoint() {
		return cnyPerPoint;
	}

	public BigDecimal cnyPerUsd() {
		return cnyPerUsd;
	}

	public BigDecimal minimumUsd() {
		return minimumUsd;
	}

	public String network() {
		return network;
	}

	public String walletAddress() {
		return walletAddress;
	}

	public WithdrawalStatus status() {
		return status;
	}

	public String txHash() {
		return txHash;
	}

	public String adminNote() {
		return adminNote;
	}

	public String createdAt() {
		return createdAt.toString();
	}

	public String reviewedAt() {
		return reviewedAt == null ? null : reviewedAt.toString();
	}
}
