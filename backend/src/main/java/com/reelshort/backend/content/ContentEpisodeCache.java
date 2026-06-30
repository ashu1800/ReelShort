package com.reelshort.backend.content;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "content_episode_cache",
		uniqueConstraints = @UniqueConstraint(columnNames = { "book_id", "filtered_title", "locale" }))
public class ContentEpisodeCache {

	@Id
	private UUID id;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "filtered_title", nullable = false, length = 255)
	private String filteredTitle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContentLocale locale;

	@Column(name = "episodes_json", nullable = false, columnDefinition = "text")
	private String episodesJson;

	@Column(name = "episode_count", nullable = false)
	private int episodeCount;

	@Column(name = "refreshed_at", nullable = false)
	private OffsetDateTime refreshedAt;

	@Column(name = "last_error", length = 512)
	private String lastError;

	protected ContentEpisodeCache() {
	}

	private ContentEpisodeCache(UUID id, String bookId, String filteredTitle, ContentLocale locale, String episodesJson,
			int episodeCount, OffsetDateTime refreshedAt) {
		this.id = id;
		this.bookId = bookId;
		this.filteredTitle = filteredTitle;
		this.locale = locale;
		this.episodesJson = episodesJson;
		this.episodeCount = episodeCount;
		this.refreshedAt = refreshedAt;
	}

	public static ContentEpisodeCache create(String bookId, String filteredTitle, String episodesJson,
			int episodeCount) {
		return create(bookId, filteredTitle, ContentLocale.ENGLISH, episodesJson, episodeCount);
	}

	public static ContentEpisodeCache create(String bookId, String filteredTitle, ContentLocale locale,
			String episodesJson, int episodeCount) {
		return new ContentEpisodeCache(UUID.randomUUID(), bookId, filteredTitle, locale, episodesJson, episodeCount,
				OffsetDateTime.now());
	}

	public void update(String episodesJson, int episodeCount) {
		this.episodesJson = episodesJson;
		this.episodeCount = episodeCount;
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

	public String filteredTitle() {
		return filteredTitle;
	}

	public ContentLocale locale() {
		return locale;
	}

	public String episodesJson() {
		return episodesJson;
	}

	public int episodeCount() {
		return episodeCount;
	}

	public OffsetDateTime refreshedAt() {
		return refreshedAt;
	}

	public String lastError() {
		return lastError;
	}
}
