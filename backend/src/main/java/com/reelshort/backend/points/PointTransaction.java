package com.reelshort.backend.points;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_transactions")
public class PointTransaction {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private int amount;

	@Column(nullable = false)
	private int balanceAfter;

	@Column(nullable = false, length = 64)
	private String source;

	@Column(length = 128)
	private String bookId;

	private Integer episodeNum;

	private Integer stage;

	@Column(length = 255)
	private String reason;

	@Column(name = "idempotency_key", length = 128, unique = true)
	private String idempotencyKey;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected PointTransaction() {
	}

	private PointTransaction(UUID id, UUID userId, int amount, int balanceAfter, String source, String bookId,
			Integer episodeNum, Integer stage, String reason, String idempotencyKey, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.source = source;
		this.bookId = bookId;
		this.episodeNum = episodeNum;
		this.stage = stage;
		this.reason = reason;
		this.idempotencyKey = idempotencyKey;
		this.createdAt = createdAt;
	}

	public static PointTransaction watchReward(UUID userId, int amount, int balanceAfter, String bookId, int episodeNum,
			int stage) {
		return watchReward(userId, amount, balanceAfter, bookId, episodeNum, stage, OffsetDateTime.now());
	}

	public static PointTransaction watchReward(UUID userId, int amount, int balanceAfter, String bookId, int episodeNum,
			int stage, OffsetDateTime createdAt) {
		return new PointTransaction(UUID.randomUUID(), userId, amount, balanceAfter, "WATCH_REWARD", bookId, episodeNum,
				stage, null, null, createdAt);
	}

	public static PointTransaction watchReward(UUID userId, int amount, int balanceAfter, String bookId, int episodeNum,
			OffsetDateTime createdAt) {
		return new PointTransaction(UUID.randomUUID(), userId, amount, balanceAfter, "WATCH_REWARD", bookId, episodeNum,
				null, null, null, createdAt);
	}

	public static PointTransaction adminAdjustment(UUID userId, int amount, int balanceAfter, String reason) {
		return new PointTransaction(UUID.randomUUID(), userId, amount, balanceAfter, "ADMIN_ADJUSTMENT", null, null,
				null, reason, null, OffsetDateTime.now());
	}

	public static PointTransaction rechargeOrder(UUID userId, int amount, int balanceAfter, String orderNo) {
		return new PointTransaction(UUID.randomUUID(), userId, amount, balanceAfter, "RECHARGE_ORDER", null, null,
				null, orderNo, null, OffsetDateTime.now());
	}

	public static PointTransaction withdrawal(UUID userId, int amount, int balanceAfter, String withdrawalId) {
		return new PointTransaction(UUID.randomUUID(), userId, -Math.abs(amount), balanceAfter, "WITHDRAWAL", null, null,
				null, withdrawalId, "withdrawal:" + withdrawalId, OffsetDateTime.now());
	}

	public static PointTransaction transferOut(UUID userId, int amount, int balanceAfter, String transferId) {
		return new PointTransaction(UUID.randomUUID(), userId, -Math.abs(amount), balanceAfter, "TRANSFER_OUT", null, null,
				null, transferId, null, OffsetDateTime.now());
	}

	public static PointTransaction transferIn(UUID userId, int amount, int balanceAfter, String transferId) {
		return new PointTransaction(UUID.randomUUID(), userId, Math.abs(amount), balanceAfter, "TRANSFER_IN", null, null,
				null, transferId, null, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public int amount() {
		return amount;
	}

	public int balanceAfter() {
		return balanceAfter;
	}

	public String source() {
		return source;
	}

	public String bookId() {
		return bookId;
	}

	public Integer episodeNum() {
		return episodeNum;
	}

	public Integer stage() {
		return stage;
	}

	public String reason() {
		return reason;
	}

	public String idempotencyKey() {
		return idempotencyKey;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
