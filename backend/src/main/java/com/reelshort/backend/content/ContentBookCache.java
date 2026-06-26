package com.reelshort.backend.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_book_cache")
public class ContentBookCache {

	@Id
	@Column(name = "book_id", length = 128)
	private String bookId;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(name = "filtered_title", nullable = false, length = 255)
	private String filteredTitle;

	@Column(name = "cover_url", length = 1024)
	private String coverUrl;

	@Column(name = "chapter_count", nullable = false)
	private int chapterCount;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ContentBookCache() {
	}

	private ContentBookCache(ContentBook book, OffsetDateTime updatedAt) {
		this.bookId = book.bookId();
		this.title = book.title();
		this.filteredTitle = book.filteredTitle();
		this.coverUrl = book.coverUrl();
		this.chapterCount = book.chapterCount();
		this.updatedAt = updatedAt;
	}

	public static ContentBookCache from(ContentBook book) {
		return new ContentBookCache(book, OffsetDateTime.now());
	}

	public void update(ContentBook book) {
		this.title = book.title();
		this.filteredTitle = book.filteredTitle();
		this.coverUrl = book.coverUrl();
		this.chapterCount = book.chapterCount();
		this.updatedAt = OffsetDateTime.now();
	}

	public String bookId() {
		return bookId;
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

	public int chapterCount() {
		return chapterCount;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
