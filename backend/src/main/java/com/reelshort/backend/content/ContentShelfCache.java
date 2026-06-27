package com.reelshort.backend.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_shelf_cache")
public class ContentShelfCache {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "shelf_type", length = 32)
	private ContentShelfType shelfType;

	@Column(name = "books_json", nullable = false, columnDefinition = "text")
	private String booksJson;

	@Column(name = "item_count", nullable = false)
	private int itemCount;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;

	@Column(name = "last_error", length = 512)
	private String lastError;

	protected ContentShelfCache() {
	}

	private ContentShelfCache(ContentShelfType shelfType, String booksJson, int itemCount, OffsetDateTime refreshedAt) {
		this.shelfType = shelfType;
		this.booksJson = booksJson;
		this.itemCount = itemCount;
		this.refreshedAt = refreshedAt;
	}

	public static ContentShelfCache create(ContentShelfType shelfType, String booksJson, int itemCount) {
		return new ContentShelfCache(shelfType, booksJson, itemCount, OffsetDateTime.now());
	}

	public void update(String booksJson, int itemCount) {
		this.booksJson = booksJson;
		this.itemCount = itemCount;
		this.refreshedAt = OffsetDateTime.now();
		this.lastError = null;
	}

	public void markFailure(String lastError) {
		this.lastError = lastError;
	}

	public ContentShelfType shelfType() {
		return shelfType;
	}

	public String booksJson() {
		return booksJson;
	}

	public int itemCount() {
		return itemCount;
	}

	public OffsetDateTime refreshedAt() {
		return refreshedAt;
	}

	public String lastError() {
		return lastError;
	}
}
