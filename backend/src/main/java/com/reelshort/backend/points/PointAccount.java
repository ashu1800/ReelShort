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

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected PointAccount() {
	}

	private PointAccount(UUID id, UUID userId, int balance, OffsetDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.balance = balance;
		this.updatedAt = updatedAt;
	}

	public static PointAccount create(UUID userId) {
		return new PointAccount(UUID.randomUUID(), userId, 0, OffsetDateTime.now());
	}

	public void add(int amount) {
		this.balance += amount;
		this.updatedAt = OffsetDateTime.now();
	}

	public boolean canAdjust(int amount) {
		long adjustedBalance = (long) this.balance + amount;
		return adjustedBalance >= 0 && adjustedBalance <= Integer.MAX_VALUE;
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

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
