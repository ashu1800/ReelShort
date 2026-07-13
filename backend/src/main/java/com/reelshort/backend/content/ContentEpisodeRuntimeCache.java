package com.reelshort.backend.content;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "content_episode_runtime_cache", uniqueConstraints = {
		@UniqueConstraint(name = "uk_content_episode_runtime", columnNames = { "book_id", "episode_num", "chapter_id" })
})
public class ContentEpisodeRuntimeCache {

	@Id
	private UUID id;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "episode_num", nullable = false)
	private int episodeNum;

	@Column(name = "chapter_id", nullable = false, length = 128)
	private String chapterId;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;

	protected ContentEpisodeRuntimeCache() {
	}

	private ContentEpisodeRuntimeCache(UUID id, String bookId, int episodeNum, String chapterId, int durationSeconds,
			OffsetDateTime refreshedAt) {
		this.id = id;
		this.bookId = bookId;
		this.episodeNum = episodeNum;
		this.chapterId = chapterId;
		this.durationSeconds = durationSeconds;
		this.refreshedAt = refreshedAt;
	}

	public static ContentEpisodeRuntimeCache create(String bookId, int episodeNum, String chapterId,
			int durationSeconds) {
		return new ContentEpisodeRuntimeCache(UUID.randomUUID(), bookId, episodeNum, chapterId, durationSeconds,
				OffsetDateTime.now());
	}

	public void update(int durationSeconds) {
		this.durationSeconds = durationSeconds;
		this.refreshedAt = OffsetDateTime.now();
	}

	public int durationSeconds() {
		return durationSeconds;
	}
}
