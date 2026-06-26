package com.reelshort.backend.watch;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "watch_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_watch_records_user_book_episode", columnNames = {
				"user_id", "book_id", "episode_num"
		})
})
public class WatchRecord {

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

	@Column(name = "episode_num", nullable = false)
	private int episodeNum;

	@Column(name = "chapter_id", nullable = false, length = 128)
	private String chapterId;

	@Column(name = "position_seconds", nullable = false)
	private int positionSeconds;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(name = "progress_percent", nullable = false)
	private int progressPercent;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected WatchRecord() {
	}

	private WatchRecord(UUID id, UUID userId, String bookId, String bookTitle, String filteredTitle, int episodeNum,
			String chapterId) {
		this.id = id;
		this.userId = userId;
		this.bookId = bookId;
		this.bookTitle = bookTitle;
		this.filteredTitle = filteredTitle;
		this.episodeNum = episodeNum;
		this.chapterId = chapterId;
	}

	public static WatchRecord create(UUID userId, String bookId, String bookTitle, String filteredTitle, int episodeNum,
			String chapterId) {
		return new WatchRecord(UUID.randomUUID(), userId, bookId, bookTitle, filteredTitle, episodeNum, chapterId);
	}

	public void update(String bookTitle, String filteredTitle, String chapterId, int positionSeconds, int durationSeconds,
			int progressPercent) {
		this.bookTitle = bookTitle;
		this.filteredTitle = filteredTitle;
		this.chapterId = chapterId;
		this.positionSeconds = positionSeconds;
		this.durationSeconds = durationSeconds;
		this.progressPercent = progressPercent;
		this.updatedAt = OffsetDateTime.now();
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

	public int episodeNum() {
		return episodeNum;
	}

	public String chapterId() {
		return chapterId;
	}

	public int positionSeconds() {
		return positionSeconds;
	}

	public int durationSeconds() {
		return durationSeconds;
	}

	public int progressPercent() {
		return progressPercent;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
