package com.reelshort.backend.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_card_attempts")
public class BankCardAttempt {

	private static final int MAX_ATTEMPTS = 3;

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true)
	private UUID userId;

	@Column(name = "card_number_last4", nullable = false, length = 4)
	private String cardNumberLast4;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "locked_until")
	private OffsetDateTime lockedUntil;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected BankCardAttempt() {
	}

	private BankCardAttempt(UUID id, UUID userId, String cardNumberLast4, int attemptCount,
			OffsetDateTime lockedUntil, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.cardNumberLast4 = cardNumberLast4;
		this.attemptCount = attemptCount;
		this.lockedUntil = lockedUntil;
		this.createdAt = createdAt;
	}

	public static BankCardAttempt create(UUID userId, String cardNumberLast4) {
		return new BankCardAttempt(UUID.randomUUID(), userId, cardNumberLast4, 0, null, OffsetDateTime.now());
	}

	public UUID userId() {
		return userId;
	}

	public String cardNumberLast4() {
		return cardNumberLast4;
	}

	public int attemptCount() {
		return attemptCount;
	}

	public boolean isLocked() {
		return lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now());
	}

	/**
	 * Records another attempt and locks the entity once the max attempt count is reached.
	 *
	 * @return the updated attempt count
	 */
	public int recordAttempt() {
		this.attemptCount++;
		if (this.attemptCount >= MAX_ATTEMPTS) {
			this.lockedUntil = OffsetDateTime.now().plusHours(24);
		}
		return this.attemptCount;
	}
}
