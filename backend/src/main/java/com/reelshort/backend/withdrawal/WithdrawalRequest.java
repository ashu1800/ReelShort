package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
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

	@Column(name = "usdt_amount", nullable = false, precision = 18, scale = 6)
	private BigDecimal usdtAmount;

	@Column(name = "usdt_per_point", nullable = false, precision = 18, scale = 8)
	private BigDecimal usdtPerPoint;

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

	private WithdrawalRequest(UUID id, UUID userId, int pointAmount, BigDecimal usdtAmount,
			BigDecimal usdtPerPoint, String network, String walletAddress, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.pointAmount = pointAmount;
		this.usdtAmount = usdtAmount;
		this.usdtPerPoint = usdtPerPoint;
		this.network = network;
		this.walletAddress = walletAddress;
		this.status = WithdrawalStatus.PENDING;
		this.createdAt = createdAt;
	}

	public static WithdrawalRequest create(UUID userId, int pointAmount, BigDecimal usdtPerPoint,
			String network, String walletAddress) {
		BigDecimal usdtAmount = usdtPerPoint.multiply(BigDecimal.valueOf(pointAmount));
		return new WithdrawalRequest(UUID.randomUUID(), userId, pointAmount, usdtAmount, usdtPerPoint,
				network, walletAddress, OffsetDateTime.now());
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

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public int pointAmount() {
		return pointAmount;
	}

	public BigDecimal usdtAmount() {
		return usdtAmount;
	}

	public BigDecimal usdtPerPoint() {
		return usdtPerPoint;
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
