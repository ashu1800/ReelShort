package com.reelshort.backend.points;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_transfers")
public class PointTransfer {

	@Id
	private UUID id;

	@Column(name = "sender_user_id", nullable = false)
	private UUID senderUserId;

	@Column(name = "recipient_user_id", nullable = false)
	private UUID recipientUserId;

	@Column(name = "sender_account", nullable = false, length = 32)
	private String senderAccount;

	@Column(name = "recipient_account", nullable = false, length = 32)
	private String recipientAccount;

	@Column(name = "point_amount", nullable = false)
	private int pointAmount;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PointTransfer() {
	}

	private PointTransfer(UUID id, UUID senderUserId, UUID recipientUserId, String senderAccount,
			String recipientAccount, int pointAmount, OffsetDateTime createdAt) {
		this.id = id;
		this.senderUserId = senderUserId;
		this.recipientUserId = recipientUserId;
		this.senderAccount = senderAccount;
		this.recipientAccount = recipientAccount;
		this.pointAmount = pointAmount;
		this.createdAt = createdAt;
	}

	public static PointTransfer create(UUID senderUserId, UUID recipientUserId, String senderAccount,
			String recipientAccount, int pointAmount) {
		return new PointTransfer(UUID.randomUUID(), senderUserId, recipientUserId, senderAccount,
				recipientAccount, pointAmount, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public UUID senderUserId() {
		return senderUserId;
	}

	public UUID recipientUserId() {
		return recipientUserId;
	}

	public String senderAccount() {
		return senderAccount;
	}

	public String recipientAccount() {
		return recipientAccount;
	}

	public int pointAmount() {
		return pointAmount;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
