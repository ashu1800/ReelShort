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

	/**
	 * 历史快照列：旧规则从积分中扣的手续费积分。手续费改为按到账 USDT 收取后新申请固定写 0，
	 * 列保留只为历史记录与迁移兼容。
	 */
	@Column(name = "fee_amount", nullable = false)
	private int feeAmount;

	@Column(name = "usdt_amount", nullable = false, precision = 18, scale = 6)
	private BigDecimal usdtAmount;

	@Column(name = "usdt_per_point", nullable = false, precision = 18, scale = 8)
	private BigDecimal usdtPerPoint;

	@Column(name = "usdt_per_50_points", precision = 18, scale = 8)
	private BigDecimal usdtPer50Points;

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

	/** 客户端提交幂等键（可选），用于超时重试时复用同一笔申请。 */
	@Column(name = "idempotency_key", length = 128)
	private String idempotencyKey;

	/** PENDING 哨兵列：仅 PENDING 时为 'PENDING'，配合唯一约束保证每用户最多一笔在途申请。 */
	@Column(name = "pending_slot", length = 24)
	private String pendingSlot;

	protected WithdrawalRequest() {
	}

	private WithdrawalRequest(UUID id, UUID userId, int pointAmount, int feeAmount, BigDecimal usdtAmount,
			BigDecimal usdtPerPoint, BigDecimal usdtPer50Points, BigDecimal cnyPerPoint, BigDecimal cnyPerUsd,
			BigDecimal minimumUsd,
			String network, String walletAddress, String idempotencyKey, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.pointAmount = pointAmount;
		this.feeAmount = feeAmount;
		this.usdtAmount = usdtAmount;
		this.usdtPerPoint = usdtPerPoint;
		this.usdtPer50Points = usdtPer50Points;
		this.cnyPerPoint = cnyPerPoint;
		this.cnyPerUsd = cnyPerUsd;
		this.minimumUsd = minimumUsd;
		this.network = network;
		this.walletAddress = walletAddress;
		this.idempotencyKey = idempotencyKey;
		this.status = WithdrawalStatus.PENDING;
		this.pendingSlot = "PENDING";
		this.createdAt = createdAt;
	}

	public static WithdrawalRequest create(UUID userId, int pointAmount, WithdrawalConversion conversion,
			String network, String walletAddress) {
		return create(userId, pointAmount, conversion, network, walletAddress, null);
	}

	public static WithdrawalRequest create(UUID userId, int pointAmount, WithdrawalConversion conversion,
			String network, String walletAddress, String idempotencyKey) {
		// L4: 用 HALF_UP 而非 UNNECESSARY，避免配置小数过多时抛 ArithmeticException
		BigDecimal normalizedRate = conversion.usdtPerPoint().setScale(8, RoundingMode.HALF_UP);
		return new WithdrawalRequest(UUID.randomUUID(), userId, pointAmount, 0,
				conversion.usdtAmount(pointAmount), normalizedRate,
				conversion.usdtPer50Points().setScale(8, RoundingMode.HALF_UP), null, null, null,
				network, walletAddress, idempotencyKey,
				OffsetDateTime.now());
	}

	public String idempotencyKey() {
		return idempotencyKey;
	}

	public String pendingSlot() {
		return pendingSlot;
	}

	public void approve(String txHash, String note, String reviewedBy) {
		if (status != WithdrawalStatus.PENDING) {
			throw new IllegalStateException("withdrawal is not pending");
		}
		this.status = WithdrawalStatus.APPROVED;
		this.pendingSlot = null;
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
		this.pendingSlot = null;
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

	/**
	 * 历史手续费积分的快照列。手续费改为按到账 USDT 收取后，新申请固定写 0；
	 * 扣减积分永远是 {@link #totalDeductedPoints()}，不再从中扣除手续费。
	 */
	public int feeAmount() {
		return feeAmount;
	}

	/** 扣减总额 = 用户输入的积分全额（手续费在 USDT 侧扣除，不额外多扣积分）。 */
	public int totalDeductedPoints() {
		return pointAmount;
	}

	public BigDecimal usdtAmount() {
		return usdtAmount;
	}

	public BigDecimal usdtPerPoint() {
		return usdtPerPoint;
	}

	public BigDecimal usdtPer50Points() {
		return usdtPer50Points;
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
