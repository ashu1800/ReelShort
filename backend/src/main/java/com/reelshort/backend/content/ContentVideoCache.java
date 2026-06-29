package com.reelshort.backend.content;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "content_video_cache",
		uniqueConstraints = @UniqueConstraint(columnNames = { "book_id", "episode_num", "filtered_title", "chapter_id" }))
public class ContentVideoCache {

	@Id
	private UUID id;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "episode_num", nullable = false)
	private int episodeNum;

	@Column(name = "filtered_title", nullable = false, length = 255)
	private String filteredTitle;

	@Column(name = "chapter_id", nullable = false, length = 128)
	private String chapterId;

	@Column(name = "video_json", nullable = false, columnDefinition = "text")
	private String videoJson;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;

	@Column(name = "last_error", length = 512)
	private String lastError;

	protected ContentVideoCache() {
	}

	private ContentVideoCache(UUID id, String bookId, int episodeNum, String filteredTitle, String chapterId,
			String videoJson, OffsetDateTime refreshedAt) {
		this.id = id;
		this.bookId = bookId;
		this.episodeNum = episodeNum;
		this.filteredTitle = filteredTitle;
		this.chapterId = chapterId;
		this.videoJson = videoJson;
		this.refreshedAt = refreshedAt;
	}

	public static ContentVideoCache create(String bookId, int episodeNum, String filteredTitle, String chapterId,
			String videoJson) {
		return new ContentVideoCache(UUID.randomUUID(), bookId, episodeNum, filteredTitle, chapterId, videoJson,
				OffsetDateTime.now());
	}

	public void update(String videoJson) {
		this.videoJson = videoJson;
		this.refreshedAt = OffsetDateTime.now();
		this.lastError = null;
	}

	public void markFailure(String message) {
		this.lastError = message;
	}

	public UUID id() {
		return id;
	}

	public String bookId() {
		return bookId;
	}

	public int episodeNum() {
		return episodeNum;
	}

	public String filteredTitle() {
		return filteredTitle;
	}

	public String chapterId() {
		return chapterId;
	}

	public String videoJson() {
		return videoJson;
	}

	public OffsetDateTime refreshedAt() {
		return refreshedAt;
	}

	public String lastError() {
		return lastError;
	}
}
