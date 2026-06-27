package com.reelshort.backend.payment;

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
@Table(name = "payment_events", uniqueConstraints = {
		@UniqueConstraint(name = "uk_payment_events_provider_event_id", columnNames = "provider_event_id")
})
public class PaymentEvent {

	@Id
	private UUID id;

	@Column(name = "provider_event_id", nullable = false, length = 128)
	private String providerEventId;

	@Column(name = "order_no", nullable = false, length = 64)
	private String orderNo;

	@Column(name = "payment_channel", nullable = false, length = 64)
	private String paymentChannel;

	@Column(name = "amount_cents", nullable = false)
	private int amountCents;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PaymentEventStatus status;

	@Column(name = "failure_reason", length = 255)
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "processed_at", nullable = false)
	private OffsetDateTime processedAt;

	protected PaymentEvent() {
	}

	private PaymentEvent(UUID id, String providerEventId, String orderNo, String paymentChannel, int amountCents,
			PaymentEventStatus status, String failureReason, OffsetDateTime createdAt, OffsetDateTime processedAt) {
		this.id = id;
		this.providerEventId = providerEventId;
		this.orderNo = orderNo;
		this.paymentChannel = paymentChannel;
		this.amountCents = amountCents;
		this.status = status;
		this.failureReason = failureReason;
		this.createdAt = createdAt;
		this.processedAt = processedAt;
	}

	public static PaymentEvent processed(PaymentCallbackRequest request) {
		OffsetDateTime now = OffsetDateTime.now();
		return new PaymentEvent(UUID.randomUUID(), request.providerEventId(), request.orderNo(),
				request.paymentChannel(), request.amountCents(), PaymentEventStatus.PROCESSED, null, now, now);
	}

	public static PaymentEvent rejected(PaymentCallbackRequest request, String failureReason) {
		OffsetDateTime now = OffsetDateTime.now();
		return new PaymentEvent(UUID.randomUUID(), request.providerEventId(), request.orderNo(),
				request.paymentChannel(), request.amountCents(), PaymentEventStatus.REJECTED, failureReason, now, now);
	}

	public UUID id() {
		return id;
	}

	public String providerEventId() {
		return providerEventId;
	}

	public String orderNo() {
		return orderNo;
	}

	public String paymentChannel() {
		return paymentChannel;
	}

	public int amountCents() {
		return amountCents;
	}

	public PaymentEventStatus status() {
		return status;
	}

	public String failureReason() {
		return failureReason;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}

	public OffsetDateTime processedAt() {
		return processedAt;
	}
}
