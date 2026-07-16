package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vip_orders")
public class VipOrder {

	private static final BigDecimal SUFFIX_DIVISOR = new BigDecimal("10000");

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "order_no", nullable = false, unique = true, length = 64)
	private String orderNo;

	@Column(name = "usdt_amount", nullable = false, precision = 18, scale = 6)
	private BigDecimal usdtAmount;

	@Column(name = "unique_suffix", nullable = false)
	private int uniqueSuffix;

	@Column(name = "tx_hash", length = 128)
	private String txHash;

	@Column(nullable = false, length = 24)
	private String status;

	@Column(name = "payment_method", nullable = false, length = 32)
	private String paymentMethod;

	@Column(name = "confirmed_by", length = 64)
	private String confirmedBy;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at")
	private OffsetDateTime expiresAt;

	@Column(name = "confirmed_at")
	private OffsetDateTime confirmedAt;

	protected VipOrder() {
	}

	private VipOrder(UUID id, UUID userId, String orderNo, BigDecimal usdtAmount, int uniqueSuffix,
			String status, String paymentMethod, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
		this.id = id;
		this.userId = userId;
		this.orderNo = orderNo;
		this.usdtAmount = usdtAmount;
		this.uniqueSuffix = uniqueSuffix;
		this.status = status;
		this.paymentMethod = paymentMethod;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public static VipOrder create(UUID userId, String orderNo, BigDecimal usdtAmount, int uniqueSuffix,
			int timeoutMinutes) {
		OffsetDateTime now = OffsetDateTime.now();
		return new VipOrder(UUID.randomUUID(), userId, orderNo, usdtAmount, uniqueSuffix, "PENDING", "USDT_TRC20",
				now, timeoutMinutes > 0 ? now.plusMinutes(timeoutMinutes) : null);
	}

	public BigDecimal payableAmount() {
		return usdtAmount.add(BigDecimal.valueOf(uniqueSuffix).divide(SUFFIX_DIVISOR, 6, RoundingMode.DOWN));
	}

	public boolean isExpired() {
		return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now()) && "PENDING".equals(status);
	}

	public void confirm(String txHash, String confirmedBy) {
		if (!"PENDING".equals(this.status)) {
			throw new IllegalStateException("order is not pending");
		}
		this.txHash = txHash;
		this.confirmedBy = confirmedBy;
		this.status = "CONFIRMED";
		this.confirmedAt = OffsetDateTime.now();
	}

	public void reject(String confirmedBy) {
		if (!"PENDING".equals(this.status)) {
			throw new IllegalStateException("order is not pending");
		}
		this.confirmedBy = confirmedBy;
		this.status = "REJECTED";
		this.confirmedAt = OffsetDateTime.now();
	}

	public void expire() {
		if (!"PENDING".equals(this.status)) {
			return;
		}
		this.status = "EXPIRED";
	}

	public UUID id() { return id; }
	public UUID userId() { return userId; }
	public String orderNo() { return orderNo; }
	public BigDecimal usdtAmount() { return usdtAmount; }
	public int uniqueSuffix() { return uniqueSuffix; }
	public String txHash() { return txHash; }
	public String status() { return status; }
	public String paymentMethod() { return paymentMethod; }
	public String confirmedBy() { return confirmedBy; }
	public OffsetDateTime createdAt() { return createdAt; }
	public OffsetDateTime expiresAt() { return expiresAt; }
	public OffsetDateTime confirmedAt() { return confirmedAt; }
}
