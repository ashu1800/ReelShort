package com.reelshort.backend.points;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_accounts")
public class PointAccount {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	private int balance;

	@Column(name = "frozen_points", nullable = false)
	private int frozenPoints;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected PointAccount() {
	}

	private PointAccount(UUID id, UUID userId, int balance, int frozenPoints, OffsetDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.balance = balance;
		this.frozenPoints = frozenPoints;
		this.updatedAt = updatedAt;
	}

	public static PointAccount create(UUID userId) {
		return new PointAccount(UUID.randomUUID(), userId, 0, 0, OffsetDateTime.now());
	}

	public void add(int amount) {
		long adjustedBalance = (long) this.balance + amount;
		if (adjustedBalance > Integer.MAX_VALUE) {
			throw new IllegalStateException("point balance overflow");
		}
		if (adjustedBalance < this.frozenPoints) {
			throw new IllegalStateException("point balance below frozen points");
		}
		this.balance = (int) adjustedBalance;
		this.updatedAt = OffsetDateTime.now();
	}

	public boolean canAdjust(int amount) {
		long adjustedBalance = (long) this.balance + amount;
		return adjustedBalance >= this.frozenPoints && adjustedBalance <= Integer.MAX_VALUE;
	}

	public boolean canUseAvailable(int amount) {
		return amount > 0 && availablePoints() >= amount;
	}

	public void freeze(int amount) {
		if (!canUseAvailable(amount)) {
			throw new IllegalStateException("insufficient available point balance");
		}
		long adjustedFrozenPoints = (long) this.frozenPoints + amount;
		if (adjustedFrozenPoints > this.balance) {
			throw new IllegalStateException("invalid frozen point amount");
		}
		this.frozenPoints = (int) adjustedFrozenPoints;
		this.updatedAt = OffsetDateTime.now();
	}

	public void releaseFrozen(int amount) {
		if (amount <= 0 || amount > this.frozenPoints) {
			throw new IllegalStateException("invalid frozen point amount");
		}
		this.frozenPoints -= amount;
		this.updatedAt = OffsetDateTime.now();
	}

	public void deductFrozen(int amount) {
		if (amount <= 0 || amount > this.frozenPoints || amount > this.balance) {
			throw new IllegalStateException("invalid frozen point amount");
		}
		this.frozenPoints -= amount;
		this.balance -= amount;
		this.updatedAt = OffsetDateTime.now();
	}

	public void deductAvailable(int amount) {
		if (!canUseAvailable(amount)) {
			throw new IllegalStateException("insufficient available point balance");
		}
		this.balance -= amount;
		this.updatedAt = OffsetDateTime.now();
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public int balance() {
		return balance;
	}

	public int frozenPoints() {
		return frozenPoints;
	}

	public int availablePoints() {
		return balance - frozenPoints;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
