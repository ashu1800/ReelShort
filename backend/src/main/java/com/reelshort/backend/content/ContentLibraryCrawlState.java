package com.reelshort.backend.content;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 视频库抓取进度：单行表，记录下一轮从内容源目录的哪一页继续抓。
 *
 * <p>只增不减的片库没有“刷新”语义，所以进度独立于 content_book_cache 存一行，
 * 每轮成功后推进 next_page；走到目录末尾（has_more=false）后停在 176，
 * 此后每轮只抓 page 0 继续发现新书，不重复扫描已入库的浏览页。
 */
@Entity
@Table(name = "content_library_crawl_state")
public class ContentLibraryCrawlState {

	public static final String SINGLETON_ID = "library";

	private static final int MAX_ERROR_LENGTH = 1024;

	@Id
	@Column(length = 32)
	private String id;

	@Column(name = "next_page", nullable = false)
	private int nextPage;

	@Column(name = "last_crawled_at")
	private OffsetDateTime lastCrawledAt;

	@Column(name = "last_added", nullable = false)
	private int lastAdded;

	@Column(name = "last_error", length = MAX_ERROR_LENGTH)
	private String lastError;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ContentLibraryCrawlState() {
	}

	static ContentLibraryCrawlState initial() {
		ContentLibraryCrawlState state = new ContentLibraryCrawlState();
		state.id = SINGLETON_ID;
		state.nextPage = 1;
		state.lastAdded = 0;
		state.updatedAt = OffsetDateTime.now();
		return state;
	}

	/**
	 * 成功抓完一轮后推进进度：清空 lastError，记录本轮新增数与抓取时刻。
	 */
	void recordCrawlSuccess(int nextPage, int added, OffsetDateTime crawledAt) {
		this.nextPage = nextPage;
		this.lastAdded = added;
		this.lastCrawledAt = crawledAt;
		this.lastError = null;
		this.updatedAt = OffsetDateTime.now();
	}

	/**
	 * 上游切换时重置进度。视频库只增不减，但换上游后旧 bookId 在新上游不可播，
	 * 必须整库作废并从头补齐（这是“只增不减”的唯一例外，见 V36 与 ContentCacheWriter）。
	 */
	void resetForUpstreamSwitch() {
		this.nextPage = 1;
		this.lastCrawledAt = null;
		this.lastAdded = 0;
		this.lastError = null;
		this.updatedAt = OffsetDateTime.now();
	}

	/**
	 * 上游失败时只记错误，不推进 nextPage：下一轮从同一页重试，避免跳页漏书。
	 */
	void markFailure(String errorMessage) {
		String message = errorMessage == null || errorMessage.isBlank()
				? "content library crawl failed"
				: errorMessage;
		this.lastError = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
		this.updatedAt = OffsetDateTime.now();
	}

	public String id() {
		return id;
	}

	public int nextPage() {
		return nextPage;
	}

	public OffsetDateTime lastCrawledAt() {
		return lastCrawledAt;
	}

	public int lastAdded() {
		return lastAdded;
	}

	public String lastError() {
		return lastError;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
