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
		// L4: 用 HALF_UP 而非 UNNECESSARY，避免配置小数过多时抛 ArithmeticException
		BigDecimal normalizedRate = conversion.usdtPerPoint().setScale(8, RoundingMode.HALF_UP);
		// 需求2: pointAmount 是用户输入的扣除总额，实际提取 = pointAmount - feeAmount
		// USDT 按实际可提取积分换算
		int withdrawable = pointAmount - feeAmount;
		return new WithdrawalRequest(UUID.randomUUID(), userId, pointAmount, feeAmount,
				conversion.usdtAmount(withdrawable),
				normalizedRate, conversion.cnyPerPoint().setScale(8, RoundingMode.HALF_UP),
				conversion.cnyPerUsd().setScale(8, RoundingMode.HALF_UP),
				conversion.minimumUsd().setScale(2, RoundingMode.HALF_UP), network, walletAddress,
				OffsetDateTime.now());
	}

	public void approve(String txHash, String note, String reviewedBy) {
		if (status != WithdrawalStatus.PENDING) {
			throw new IllegalStateException("withdrawal is not pending");
		}
		this.status = WithdrawalStatus.APPROVED;
		this.txHash = txHash == null ? null : txHash.trim().toLowerCase(java.util.Locale.ROOT);
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

	/**
	 * 需求2: 用户输入的总额即为扣减总额（手续费从总额中扣除，不再额外扣）。
	 * 例如用户提现 1000 积分，手续费 10%，则扣减 1000 积分，实际提取 900。
	 */
	public int totalDeductedPoints() {
		return pointAmount;
	}

	/**
	 * 实际可提取积分 = 扣减总额 - 手续费。USDT 按此值换算。
	 */
	public int withdrawablePoints() {
		return pointAmount - feeAmount;
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
