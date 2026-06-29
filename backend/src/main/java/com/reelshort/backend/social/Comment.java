package com.reelshort.backend.social;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "username", nullable = false, length = 64)
	private String username;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "content", nullable = false, length = 500)
	private String content;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Comment() {
	}

	private Comment(UUID id, UUID userId, String username, String bookId, String content) {
		this.id = id;
		this.userId = userId;
		this.username = username;
		this.bookId = bookId;
		this.content = content;
		this.createdAt = OffsetDateTime.now();
	}

	public static Comment create(UUID userId, String username, String bookId, String content) {
		return new Comment(UUID.randomUUID(), userId, username, bookId, content);
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public String username() {
		return username;
	}

	public String bookId() {
		return bookId;
	}

	public String content() {
		return content;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
