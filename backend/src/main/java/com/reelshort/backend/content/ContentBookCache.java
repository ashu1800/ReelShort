package com.reelshort.backend.content;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "content_book_cache",
		uniqueConstraints = @UniqueConstraint(columnNames = { "book_id", "locale" }))
public class ContentBookCache {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "book_id", length = 128)
	private String bookId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContentLocale locale;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(name = "filtered_title", nullable = false, length = 255)
	private String filteredTitle;

	@Column(name = "cover_url", length = 1024)
	private String coverUrl;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "chapter_count", nullable = false)
	private int chapterCount;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ContentBookCache() {
	}

	private ContentBookCache(ContentBook book, ContentLocale locale, OffsetDateTime updatedAt) {
		this.id = key(book.bookId(), locale);
		this.bookId = book.bookId();
		this.locale = locale;
		this.title = book.title();
		this.filteredTitle = book.filteredTitle();
		this.coverUrl = book.coverUrl();
		this.description = book.description();
		this.chapterCount = book.chapterCount();
		this.updatedAt = updatedAt;
	}

	public static ContentBookCache from(ContentBook book) {
		return from(book, ContentLocale.ENGLISH);
	}

	public static ContentBookCache from(ContentBook book, ContentLocale locale) {
		return new ContentBookCache(book, locale, OffsetDateTime.now());
	}

	/**
	 * 用新抓到的书目刷新缓存行。
	 *
	 * <p>同一本书会从不同来源进来：首页/分类页的卡片带简介和集数，`/browse` 列表页
	 * 两者都没有。无条件覆盖会把已经补好的简介和集数抹成空，所以这里只允许"变丰富"：
	 * 简介保留更长的那个，集数保留已知的正数，封面保留非空值。
	 */
	public void update(ContentBook book) {
		this.title = book.title();
		this.filteredTitle = book.filteredTitle();
		if (book.coverUrl() != null && !book.coverUrl().isBlank()) {
			this.coverUrl = book.coverUrl();
		}
		String incoming = book.description() == null ? "" : book.description().trim();
		String existing = this.description == null ? "" : this.description.trim();
		if (!incoming.isEmpty() && incoming.length() >= existing.length()) {
			this.description = book.description();
		}
		if (book.chapterCount() > 0) {
			this.chapterCount = book.chapterCount();
		}
		this.updatedAt = OffsetDateTime.now();
	}

	public String bookId() {
		return bookId;
	}

	public ContentLocale locale() {
		return locale;
	}

	public String title() {
		return title;
	}

	public String filteredTitle() {
		return filteredTitle;
	}

	public String coverUrl() {
		return coverUrl;
	}

	public String description() {
		return description;
	}

	public int chapterCount() {
		return chapterCount;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}

	public ContentBook toContentBook() {
		return new ContentBook(bookId, title, filteredTitle, coverUrl, description, chapterCount);
	}

	static String key(String bookId, ContentLocale locale) {
		return UUID.nameUUIDFromBytes((bookId + "::" + locale.name()).getBytes(StandardCharsets.UTF_8)).toString();
	}
}
