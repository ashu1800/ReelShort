package com.reelshort.backend.content;

import java.util.Arrays;
import java.util.List;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 内容缓存读写编排。
 *
 * <p>P1-7：本类的对外方法不再包裹数据库事务。上游 HTTP 调用一律在事务之外执行，
 * 缓存落库交给 {@link ContentCacheWriter}（自带事务边界）；只有纯只读查询保留
 * {@code @Transactional(readOnly = true)}。
 */
@Service
public class ContentCacheService {

	/** P2-7：固定条带锁数量，替代按客户端可控 bookId/chapterId 无界增长的锁表。 */
	private static final int VIDEO_CACHE_LOCK_STRIPES = 64;

	private static final ReentrantLock[] VIDEO_CACHE_LOCKS = createVideoCacheLocks();

	private static final Duration SHELF_STALE_AFTER = Duration.ofHours(12);

	private static final TypeReference<List<ContentBook>> CONTENT_BOOK_LIST = new TypeReference<>() {
	};

	private static final TypeReference<List<ContentEpisode>> CONTENT_EPISODE_LIST = new TypeReference<>() {
	};

	private static final TypeReference<ContentVideo> CONTENT_VIDEO = new TypeReference<>() {
	};

	private final ContentProvider contentProvider;
	private final ContentShelfCacheRepository contentShelfCacheRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final ContentVideoCacheRepository contentVideoCacheRepository;
	private final ContentRefreshRunRepository contentRefreshRunRepository;
	private final ContentRefreshRunRecorder contentRefreshRunRecorder;
	private final ContentCacheProperties contentCacheProperties;
	private final ContentCacheWriterProvider contentCacheWriterProvider;
	private final ShelfSingleFlight shelfSingleFlight;
	private final ObjectMapper objectMapper;

	public ContentCacheService(ContentProvider contentProvider, ContentShelfCacheRepository contentShelfCacheRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentVideoCacheRepository contentVideoCacheRepository,
			ContentRefreshRunRepository contentRefreshRunRepository,
			ContentRefreshRunRecorder contentRefreshRunRecorder,
			ContentCacheProperties contentCacheProperties,
			ContentCacheWriterProvider contentCacheWriterProvider,
			ShelfSingleFlight shelfSingleFlight,
			ObjectMapper objectMapper) {
		this.contentProvider = contentProvider;
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentVideoCacheRepository = contentVideoCacheRepository;
		this.contentRefreshRunRepository = contentRefreshRunRepository;
		this.contentRefreshRunRecorder = contentRefreshRunRecorder;
		this.contentCacheProperties = contentCacheProperties;
		this.contentCacheWriterProvider = contentCacheWriterProvider;
		this.shelfSingleFlight = shelfSingleFlight;
		this.objectMapper = objectMapper;
	}

	private static ReentrantLock[] createVideoCacheLocks() {
		ReentrantLock[] locks = new ReentrantLock[VIDEO_CACHE_LOCK_STRIPES];
		for (int index = 0; index < locks.length; index++) {
			locks[index] = new ReentrantLock();
		}
		return locks;
	}

	public List<ContentBook> search(String keywords) {
		return search(keywords, ContentLocale.ENGLISH);
	}

	public List<ContentBook> search(String keywords, ContentLocale locale) {
		try {
			List<ContentBook> books = contentProvider.search(keywords, locale);
			writer().saveBooks(books, locale);
			return books;
		}
		catch (ContentProviderException exception) {
			List<ContentBook> cachedBooks = searchCachedBooks(keywords, locale);
			if (!cachedBooks.isEmpty()) {
				return cachedBooks;
			}
			throw exception;
		}
	}

	public List<ContentBook> getShelf(ContentShelfType shelfType) {
		return getShelf(shelfType, ContentLocale.ENGLISH);
	}

	public List<ContentBook> getShelf(ContentShelfType shelfType, ContentLocale locale) {
		return contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
				.map(cache -> readShelfCacheOrRefresh(cache, shelfType, locale))
				.orElseGet(() -> refreshShelfFromCacheMiss(shelfType, locale));
	}

	public List<ContentBook> refreshShelf(ContentShelfType shelfType) {
		return refreshShelf(shelfType, ContentLocale.ENGLISH);
	}

	public List<ContentBook> refreshShelf(ContentShelfType shelfType, ContentLocale locale) {
		return shelfSingleFlight.run(shelfType, locale, () -> refreshShelfFromProvider(shelfType, locale, false));
	}

	/**
	 * 缓存未命中时的刷新入口。
	 *
	 * <p>single-flight 只保证“同一时刻只有一次上游刷新”；等待者依次拿到执行权后必须
	 * 复查缓存，否则前一个请求刚落库的货架又会被打一次上游（未鉴权读路径会被放大成 N 次上游）。
	 */
	private List<ContentBook> refreshShelfFromCacheMiss(ContentShelfType shelfType, ContentLocale locale) {
		return shelfSingleFlight.run(shelfType, locale, () -> refreshShelfFromProvider(shelfType, locale, true));
	}

	/**
	 * 缓存里已有一行记录、但内容为空或损坏时的刷新入口（同样带复查）。
	 */
	private List<ContentBook> readShelfCacheOrRefresh(ContentShelfCache cache, ContentShelfType shelfType,
			ContentLocale locale) {
		try {
			List<ContentBook> books = readBooks(cache.booksJson());
			if (!books.isEmpty()) {
				return books;
			}
		}
		catch (IllegalStateException exception) {
			writer().markCorruptShelfCache(shelfType, locale, exception.getMessage());
		}
		return refreshShelfFromCacheMiss(shelfType, locale);
	}

	public List<ContentBook> refreshShelf(ContentShelfType shelfType, ContentLocale locale,
			ContentRefreshTriggerSource triggerSource) {
		OffsetDateTime startedAt = OffsetDateTime.now();
		try {
			List<ContentBook> books = refreshShelf(shelfType, locale);
			contentRefreshRunRecorder.recordSuccess(
					triggerSource,
					shelfType,
					locale,
					startedAt,
					OffsetDateTime.now(),
					books.size());
			return books;
		}
		catch (RuntimeException exception) {
			recordRefreshFailure(triggerSource, shelfType, locale, startedAt, exception);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public ContentBook getBook(String bookId) {
		return getBook(bookId, ContentLocale.ENGLISH);
	}

	@Transactional(readOnly = true)
	public ContentBook getBook(String bookId, ContentLocale locale) {
		return contentBookCacheRepository.findByBookIdAndLocale(bookId, locale)
				.map(ContentBookCache::toContentBook)
				.orElseThrow(() -> new ContentProviderException(404, "content book not cached"));
	}

	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle) {
		return getEpisodes(bookId, filteredTitle, ContentLocale.ENGLISH);
	}

	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle, ContentLocale locale) {
		return contentEpisodeCacheRepository.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.map(cache -> readEpisodeCacheOrRefresh(cache, bookId, filteredTitle, locale))
				.orElseGet(() -> refreshEpisodes(bookId, filteredTitle, locale));
	}

	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		return getVideoUrl(bookId, episodeNum, filteredTitle, chapterId, ContentLocale.ENGLISH);
	}

	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale) {
		// P2-7：条带锁按 key 的 hash 取模落到 64 个固定锁上，客户端可控的 bookId/chapterId 不再撑大锁表。
		ReentrantLock lock = videoCacheLock(bookId, episodeNum, filteredTitle, chapterId, locale);
		lock.lock();
		try {
			// writer 在自己的事务里提交，因此锁可以在本方法返回前直接释放，不再依赖外层事务回调。
			ContentVideo video = contentProvider.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId, locale);
			writer().saveVideo(bookId, episodeNum, filteredTitle, chapterId, locale, video);
			return video;
		}
		catch (ContentProviderException exception) {
			return cachedVideoOrThrow(bookId, episodeNum, filteredTitle, chapterId, locale, exception);
		}
		finally {
			lock.unlock();
		}
	}

	@Transactional(readOnly = true)
	public ContentCacheStatusResponse status() {
		List<ContentCacheStatusResponse.ShelfStatus> shelves = Arrays.stream(ContentShelfType.values())
				.flatMap(shelfType -> Arrays.stream(ContentLocale.values()).map(locale -> shelfStatus(shelfType, locale)))
				.toList();
		List<ContentCacheStatusResponse.RefreshRunStatus> refreshRuns = contentRefreshRunRepository
				.findTop10ByOrderByStartedAtDesc()
				.stream()
				.map(this::refreshRunStatus)
				.toList();
		return new ContentCacheStatusResponse(contentBookCacheRepository.count(), contentEpisodeCacheRepository.count(),
				contentVideoCacheRepository.count(), shelves, refreshRuns);
	}

	private ReentrantLock videoCacheLock(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale) {
		String lockKey = bookId + "\u0000" + episodeNum + "\u0000" + chapterId + "\u0000" + locale.apiValue();
		return VIDEO_CACHE_LOCKS[Math.floorMod(lockKey.hashCode(), VIDEO_CACHE_LOCK_STRIPES)];
	}

	private ContentCacheWriter writer() {
		return contentCacheWriterProvider.get();
	}

	private List<ContentBook> refreshShelfFromProvider(ContentShelfType shelfType, ContentLocale locale,
			boolean reuseFreshCache) {
		if (reuseFreshCache) {
			List<ContentBook> cached = contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
					.map(cache -> {
						try {
							return readBooks(cache.booksJson());
						}
						catch (IllegalStateException exception) {
							return List.<ContentBook>of();
						}
					})
					.orElse(List.of());
			if (!cached.isEmpty()) {
				return cached;
			}
		}
		try {
			List<ContentBook> books = contentProvider.getShelf(shelfType, locale);
			ContentCacheWriter writer = writer();
			writer.saveBooks(books, locale);
			writer.saveShelf(shelfType, locale, books);
			return books;
		}
		catch (ContentProviderException exception) {
			writer().markShelfFailure(shelfType, locale, exception.getMessage());
			throw exception;
		}
	}

	private List<ContentEpisode> readEpisodeCacheOrRefresh(ContentEpisodeCache cache, String bookId,
			String filteredTitle, ContentLocale locale) {
		try {
			return readEpisodes(cache.episodesJson());
		}
		catch (IllegalStateException exception) {
			writer().markCorruptEpisodeCache(bookId, filteredTitle, locale, exception.getMessage());
			return refreshEpisodes(bookId, filteredTitle, locale);
		}
	}

	private List<ContentEpisode> refreshEpisodes(String bookId, String filteredTitle, ContentLocale locale) {
		try {
			ContentEpisodesDetail detail = contentProvider.getEpisodesDetail(bookId, filteredTitle, locale);
			ContentCacheWriter writer = writer();
			detail.book().ifPresent(book -> writer.saveBook(book, locale));
			writer.saveEpisodes(bookId, filteredTitle, locale, detail.episodes());
			return detail.episodes();
		}
		catch (ContentProviderException exception) {
			writer().markEpisodeFailure(bookId, filteredTitle, locale, exception.getMessage());
			throw exception;
		}
	}

	private ContentCacheStatusResponse.ShelfStatus shelfStatus(ContentShelfType shelfType, ContentLocale locale) {
		return contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
				.map(cache -> {
					ShelfHealthStatus health = shelfHealth(cache);
					return new ContentCacheStatusResponse.ShelfStatus(
							shelfType.apiValue(),
							locale.apiValue(),
							cache.itemCount(),
							cache.refreshedAt().toString(),
							cache.lastError(),
							health.health().name(),
							health.message());
				})
				.orElseGet(() -> new ContentCacheStatusResponse.ShelfStatus(
						shelfType.apiValue(),
						locale.apiValue(),
						0,
						null,
						null,
						ContentShelfCacheHealth.MISSING.name(),
						"not refreshed yet"));
	}

	private ShelfHealthStatus shelfHealth(ContentShelfCache cache) {
		if (cache.lastError() != null && !cache.lastError().isBlank()) {
			return new ShelfHealthStatus(ContentShelfCacheHealth.ERROR, cache.lastError());
		}
		if (cache.itemCount() <= 0) {
			return new ShelfHealthStatus(ContentShelfCacheHealth.EMPTY, "last refresh returned no books");
		}
		if (Duration.between(cache.refreshedAt(), OffsetDateTime.now()).compareTo(SHELF_STALE_AFTER) > 0) {
			return new ShelfHealthStatus(ContentShelfCacheHealth.STALE, "last refresh is older than 12 hours");
		}
		return new ShelfHealthStatus(ContentShelfCacheHealth.HEALTHY, "cache is fresh");
	}

	private record ShelfHealthStatus(ContentShelfCacheHealth health, String message) {
	}

	private ContentCacheStatusResponse.RefreshRunStatus refreshRunStatus(ContentRefreshRun run) {
		return new ContentCacheStatusResponse.RefreshRunStatus(
				run.triggerSource().name(),
				run.shelfType().apiValue(),
				run.locale().apiValue(),
				run.status().name(),
				run.startedAt().toString(),
				run.finishedAt().toString(),
				run.durationMillis(),
				run.itemCount(),
				run.errorMessage());
	}

	private void recordRefreshFailure(ContentRefreshTriggerSource triggerSource, ContentShelfType shelfType,
			ContentLocale locale, OffsetDateTime startedAt, RuntimeException exception) {
		contentRefreshRunRecorder.recordFailure(
				triggerSource,
				shelfType,
				locale,
				startedAt,
				OffsetDateTime.now(),
				exception.getMessage());
	}

	private List<ContentBook> searchCachedBooks(String keywords, ContentLocale locale) {
		String keyword = keywords == null ? "" : keywords.trim();
		if (keyword.isBlank()) {
			return List.of();
		}
		return contentBookCacheRepository
				.findTop50ByLocaleAndTitleContainingIgnoreCaseOrLocaleAndDescriptionContainingIgnoreCase(
						locale,
						keyword,
						locale,
						keyword)
				.stream()
				.map(ContentBookCache::toContentBook)
				.toList();
	}

	private ContentVideo cachedVideoOrThrow(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale, ContentProviderException exception) {
		// 播放地址具备时效性，仅当上游不可用（5xx）且缓存仍在短 TTL 内时兜底；404 表示该集不存在，不回退。
		if (exception.statusCode() < 500) {
			throw exception;
		}
		ContentVideoCache cache = contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterIdAndLocale(bookId, episodeNum, filteredTitle,
						chapterId, locale)
				.orElseThrow(() -> exception);
		writer().markVideoFailure(bookId, episodeNum, filteredTitle, chapterId, locale, exception.getMessage());
		if (!isVideoFallbackCacheFresh(cache)) {
			throw exception;
		}
		ContentVideo video = readVideo(cache.videoJson());
		if (video.duration() > 0) {
			writer().saveRuntime(bookId, episodeNum, chapterId, video.duration());
		}
		return video;
	}

	private boolean isVideoFallbackCacheFresh(ContentVideoCache cache) {
		Duration ttl = contentCacheProperties.getVideoFallbackTtl();
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			return false;
		}
		return Duration.between(cache.refreshedAt(), OffsetDateTime.now()).compareTo(ttl) <= 0;
	}

	private List<ContentBook> readBooks(String booksJson) {
		try {
			return objectMapper.readValue(booksJson, CONTENT_BOOK_LIST);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to read content shelf cache", exception);
		}
	}

	private List<ContentEpisode> readEpisodes(String episodesJson) {
		try {
			return objectMapper.readValue(episodesJson, CONTENT_EPISODE_LIST);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to read content episode cache", exception);
		}
	}

	private ContentVideo readVideo(String videoJson) {
		try {
			return objectMapper.readValue(videoJson, CONTENT_VIDEO);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to read content video cache", exception);
		}
	}
}
