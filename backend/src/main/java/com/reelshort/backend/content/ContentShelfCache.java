package com.reelshort.backend.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "content_shelf_cache",
		uniqueConstraints = @UniqueConstraint(columnNames = { "shelf_type", "locale" }))
public class ContentShelfCache {

	@Id
	@Column(length = 80)
	private String id;

	@Enumerated(EnumType.STRING)
	@Column(name = "shelf_type", length = 32)
	private ContentShelfType shelfType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContentLocale locale;

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

	private ContentShelfCache(ContentShelfType shelfType, ContentLocale locale, String booksJson, int itemCount,
			OffsetDateTime refreshedAt) {
		this.id = key(shelfType, locale);
		this.shelfType = shelfType;
		this.locale = locale;
		this.booksJson = booksJson;
		this.itemCount = itemCount;
		this.refreshedAt = refreshedAt;
	}

	public static ContentShelfCache create(ContentShelfType shelfType, String booksJson, int itemCount) {
		return create(shelfType, ContentLocale.ENGLISH, booksJson, itemCount);
	}

	public static ContentShelfCache create(ContentShelfType shelfType, ContentLocale locale, String booksJson,
			int itemCount) {
		return new ContentShelfCache(shelfType, locale, booksJson, itemCount, OffsetDateTime.now());
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

	public ContentLocale locale() {
		return locale;
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

	static String key(ContentShelfType shelfType, ContentLocale locale) {
		return locale == ContentLocale.ENGLISH ? shelfType.name() : shelfType.name() + "::" + locale.name();
	}
}
