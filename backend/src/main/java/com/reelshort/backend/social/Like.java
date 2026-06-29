package com.reelshort.backend.social;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "likes", uniqueConstraints = {
		@UniqueConstraint(name = "uk_likes_user_book", columnNames = { "user_id", "book_id" })
})
public class Like {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Like() {
	}

	private Like(UUID id, UUID userId, String bookId) {
		this.id = id;
		this.userId = userId;
		this.bookId = bookId;
		this.createdAt = OffsetDateTime.now();
	}

	public static Like create(UUID userId, String bookId) {
		return new Like(UUID.randomUUID(), userId, bookId);
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public String bookId() {
		return bookId;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
