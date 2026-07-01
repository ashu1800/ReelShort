package com.reelshort.backend.content;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_refresh_runs")
public class ContentRefreshRun {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_source", nullable = false, length = 32)
	private ContentRefreshTriggerSource triggerSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "shelf_type", nullable = false, length = 32)
	private ContentShelfType shelfType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContentLocale locale;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContentRefreshRunStatus status;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "finished_at", nullable = false)
	private OffsetDateTime finishedAt;

	@Column(name = "duration_millis", nullable = false)
	private long durationMillis;

	@Column(name = "item_count", nullable = false)
	private int itemCount;

	@Column(name = "error_message", length = 1024)
	private String errorMessage;

	protected ContentRefreshRun() {
	}

	private ContentRefreshRun(ContentRefreshTriggerSource triggerSource, ContentShelfType shelfType, ContentLocale locale,
			ContentRefreshRunStatus status, OffsetDateTime startedAt, OffsetDateTime finishedAt, int itemCount,
			String errorMessage) {
		this.id = UUID.randomUUID();
		this.triggerSource = triggerSource;
		this.shelfType = shelfType;
		this.locale = locale;
		this.status = status;
		this.startedAt = startedAt;
		this.finishedAt = finishedAt;
		this.durationMillis = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
		this.itemCount = itemCount;
		this.errorMessage = errorMessage;
	}

	public static ContentRefreshRun success(ContentRefreshTriggerSource triggerSource, ContentShelfType shelfType,
			ContentLocale locale, OffsetDateTime startedAt, OffsetDateTime finishedAt, int itemCount) {
		return new ContentRefreshRun(triggerSource, shelfType, locale, ContentRefreshRunStatus.SUCCESS, startedAt,
				finishedAt, itemCount, null);
	}

	public static ContentRefreshRun failure(ContentRefreshTriggerSource triggerSource, ContentShelfType shelfType,
			ContentLocale locale, OffsetDateTime startedAt, OffsetDateTime finishedAt, String errorMessage) {
		return new ContentRefreshRun(triggerSource, shelfType, locale, ContentRefreshRunStatus.FAILED, startedAt,
				finishedAt, 0, errorMessage);
	}

	public UUID id() {
		return id;
	}

	public ContentRefreshTriggerSource triggerSource() {
		return triggerSource;
	}

	public ContentShelfType shelfType() {
		return shelfType;
	}

	public ContentLocale locale() {
		return locale;
	}

	public ContentRefreshRunStatus status() {
		return status;
	}

	public OffsetDateTime startedAt() {
		return startedAt;
	}

	public OffsetDateTime finishedAt() {
		return finishedAt;
	}

	public long durationMillis() {
		return durationMillis;
	}

	public int itemCount() {
		return itemCount;
	}

	public String errorMessage() {
		return errorMessage;
	}
}
