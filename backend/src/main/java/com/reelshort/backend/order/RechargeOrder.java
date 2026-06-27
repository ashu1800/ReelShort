package com.reelshort.backend.order;

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
@Table(name = "recharge_orders", uniqueConstraints = {
		@UniqueConstraint(name = "uk_recharge_orders_order_no", columnNames = "order_no")
})
public class RechargeOrder {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "order_no", nullable = false, length = 64)
	private String orderNo;

	@Column(name = "amount_cents", nullable = false)
	private int amountCents;

	@Column(name = "point_amount", nullable = false)
	private int pointAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RechargeOrderStatus status;

	@Column(name = "payment_channel", length = 64)
	private String paymentChannel;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected RechargeOrder() {
	}

	private RechargeOrder(UUID id, UUID userId, String orderNo, int amountCents, int pointAmount,
			RechargeOrderStatus status, String paymentChannel, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.orderNo = orderNo;
		this.amountCents = amountCents;
		this.pointAmount = pointAmount;
		this.status = status;
		this.paymentChannel = paymentChannel;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static RechargeOrder create(UUID userId, String orderNo, int amountCents, int pointAmount) {
		OffsetDateTime now = OffsetDateTime.now();
		return new RechargeOrder(UUID.randomUUID(), userId, orderNo, amountCents, pointAmount,
				RechargeOrderStatus.CREATED, null, now, now);
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public String orderNo() {
		return orderNo;
	}

	public int amountCents() {
		return amountCents;
	}

	public int pointAmount() {
		return pointAmount;
	}

	public RechargeOrderStatus status() {
		return status;
	}

	public String paymentChannel() {
		return paymentChannel;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
