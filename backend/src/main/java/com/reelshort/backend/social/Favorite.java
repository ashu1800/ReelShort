package com.reelshort.backend.social;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "favorites", uniqueConstraints = {
		@UniqueConstraint(name = "uk_favorites_user_book", columnNames = { "user_id", "book_id" })
})
public class Favorite {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "book_title", nullable = false, length = 255)
	private String bookTitle;

	@Column(name = "filtered_title", nullable = false, length = 255)
	private String filteredTitle;

	@Column(name = "cover_url", length = 512)
	private String coverUrl;

	@Column(name = "chapter_count", nullable = false)
	private int chapterCount;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Favorite() {
	}

	private Favorite(UUID id, UUID userId, String bookId, String bookTitle, String filteredTitle, String coverUrl,
			int chapterCount) {
		this.id = id;
		this.userId = userId;
		this.bookId = bookId;
		this.bookTitle = bookTitle;
		this.filteredTitle = filteredTitle;
		this.coverUrl = coverUrl;
		this.chapterCount = chapterCount;
		this.createdAt = OffsetDateTime.now();
	}

	public static Favorite create(UUID userId, String bookId, String bookTitle, String filteredTitle, String coverUrl,
			int chapterCount) {
		return new Favorite(UUID.randomUUID(), userId, bookId, bookTitle, filteredTitle, coverUrl, chapterCount);
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

	public String bookTitle() {
		return bookTitle;
	}

	public String filteredTitle() {
		return filteredTitle;
	}

	public String coverUrl() {
		return coverUrl;
	}

	public int chapterCount() {
		return chapterCount;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
