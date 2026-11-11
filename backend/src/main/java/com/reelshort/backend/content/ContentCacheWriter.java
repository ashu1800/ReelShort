package com.reelshort.backend.content;

import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 内容缓存写库入口。
 *
 * <p>P1-7：上游 HTTP 调用必须发生在数据库事务之外，因此把所有写库动作集中到这个组件，
 * 由 {@link ContentCacheService} 按“事务外取上游 → 调用 writer 落库”的顺序调用。
 * 每个公开方法自带事务边界，调用方不需要（也不应该）再包一层事务。
 */
@Service
public class ContentCacheWriter {

	private final ContentShelfCacheRepository contentShelfCacheRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final ContentVideoCacheRepository contentVideoCacheRepository;
	private final ContentEpisodeRuntimeCacheRepository contentEpisodeRuntimeCacheRepository;
	private final ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository;
	private final ObjectMapper objectMapper;

	public ContentCacheWriter(ContentShelfCacheRepository contentShelfCacheRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentVideoCacheRepository contentVideoCacheRepository,
			ContentEpisodeRuntimeCacheRepository contentEpisodeRuntimeCacheRepository,
			ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository, ObjectMapper objectMapper) {
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentVideoCacheRepository = contentVideoCacheRepository;
		this.contentEpisodeRuntimeCacheRepository = contentEpisodeRuntimeCacheRepository;
		this.contentLibraryCrawlStateRepository = contentLibraryCrawlStateRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * 批量 upsert 书目，并返回落库后的视图。
	 *
	 * <p>返回值不是入参的拷贝：缓存行会保留已有简介/集数（见 {@link ContentBookCache#update}），
	 * 调用方拿这份视图去写货架快照，货架 JSON 才不会把之前补好的简介覆盖成空。
	 */
	@Transactional
	public List<ContentBook> saveBooks(List<ContentBook> books, ContentLocale locale) {
		List<ContentBook> stored = new ArrayList<>(books.size());
		for (ContentBook book : books) {
			stored.add(saveBook(book, locale));
		}
		return stored;
	}

	@Transactional
	public ContentBook saveBook(ContentBook book, ContentLocale locale) {
		// 主键由 (bookId, locale) 确定性派生，按主键查一次即可做到幂等 upsert：
		// 并发刷新同一部剧时不会因为同时插入而撞唯一约束。
		String key = ContentBookCache.key(book.bookId(), locale);
		ContentBookCache cache = contentBookCacheRepository.findById(key)
				.orElseGet(() -> contentBookCacheRepository.findByBookIdAndLocale(book.bookId(), locale)
						.orElseGet(() -> ContentBookCache.from(book, locale)));
		cache.update(book);
		return contentBookCacheRepository.save(cache).toContentBook();
	}

	/**
	 * 记下"这本书已经尝试补过简介，上游没有"。
	 *
	 * <p>TwistyTV 的 NetShort(`ns_`) 书目在详情页里没有 description 字段，不记这一笔
	 * 补抓任务会每轮把这批书重翻一次（纯打上游）。
	 */
	@Transactional
	public void markDescriptionChecked(String bookId, ContentLocale locale) {
		String key = ContentBookCache.key(bookId, locale);
		contentBookCacheRepository.findById(key).ifPresent(cache -> {
			cache.markDescriptionChecked();
			contentBookCacheRepository.save(cache);
		});
	}

	@Transactional
	public void saveShelf(ContentShelfType shelfType, ContentLocale locale, List<ContentBook> books) {
		String booksJson = writeBooks(books);
		ContentShelfCache cache = contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
				.orElseGet(() -> ContentShelfCache.create(shelfType, locale, booksJson, books.size()));
		cache.update(booksJson, books.size());
		contentShelfCacheRepository.save(cache);
	}

	@Transactional
	public void saveEpisodes(String bookId, String filteredTitle, ContentLocale locale, List<ContentEpisode> episodes) {
		String episodesJson = writeEpisodes(episodes);
		ContentEpisodeCache cache = contentEpisodeCacheRepository
				.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.orElseGet(() -> ContentEpisodeCache.create(bookId, filteredTitle, locale, episodesJson,
						episodes.size()));
		cache.update(episodesJson, episodes.size());
		contentEpisodeCacheRepository.save(cache);
	}

	@Transactional
	public void saveVideo(String bookId, int episodeNum, String filteredTitle, String chapterId, ContentLocale locale,
			ContentVideo video) {
		String videoJson = writeVideo(video);
		ContentVideoCache cache = contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterIdAndLocale(bookId, episodeNum, filteredTitle,
						chapterId, locale)
				.orElseGet(() -> ContentVideoCache.create(bookId, episodeNum, filteredTitle, chapterId, locale,
						videoJson));
		cache.update(videoJson);
		contentVideoCacheRepository.save(cache);
		if (video.duration() > 0) {
			saveRuntime(bookId, episodeNum, chapterId, video.duration());
		}
	}

	@Transactional
	public void saveRuntime(String bookId, int episodeNum, String chapterId, int durationSeconds) {
		ContentEpisodeRuntimeCache runtime = contentEpisodeRuntimeCacheRepository
				.findByBookIdAndEpisodeNumAndChapterId(bookId, episodeNum, chapterId)
				.orElseGet(() -> ContentEpisodeRuntimeCache.create(bookId, episodeNum, chapterId, durationSeconds));
		runtime.update(durationSeconds);
		contentEpisodeRuntimeCacheRepository.save(runtime);
	}

	/**
	 * 切换内容上游时作废整个视频库并把抓取游标重置到第 1 页。
	 *
	 * <p>视频库“只增不减”的唯一例外：旧上游的 bookId 在新上游不可播，留着会变成死链，
	 * 所以换上游后必须整库重抓。必须 REQUIRES_NEW：调用点是配置变更事件的 afterCommit
	 * 回调，那里原事务已经提交，用默认的 REQUIRED 会加入一个“再也不会提交”的事务，
	 * deleteAll() 的删除会被静默丢弃（与 {@link ContentCacheService#clearShelves()} 同因）。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void clearLibraryForUpstreamSwitch() {
		contentBookCacheRepository.deleteAll();
		ContentLibraryCrawlState state = contentLibraryCrawlStateRepository
				.findById(ContentLibraryCrawlState.SINGLETON_ID)
				.orElseGet(ContentLibraryCrawlState::initial);
		state.resetForUpstreamSwitch();
		contentLibraryCrawlStateRepository.save(state);
	}

	/**
	 * 视频库抓取成功后推进进度：nextPage、本轮新增数、抓取时刻，并清空 lastError。
	 */
	@Transactional
	public void recordLibraryCrawlSuccess(int nextPage, int added) {
		ContentLibraryCrawlState state = contentLibraryCrawlStateRepository
				.findById(ContentLibraryCrawlState.SINGLETON_ID)
				.orElseGet(ContentLibraryCrawlState::initial);
		state.recordCrawlSuccess(nextPage, added, OffsetDateTime.now());
		contentLibraryCrawlStateRepository.save(state);
	}

	/**
	 * 记录视频库抓取失败。REQUIRES_NEW 独立提交，抓取本身的异常不会带走这条错误状态。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markLibraryCrawlFailure(String errorMessage) {
		ContentLibraryCrawlState state = contentLibraryCrawlStateRepository
				.findById(ContentLibraryCrawlState.SINGLETON_ID)
				.orElseGet(ContentLibraryCrawlState::initial);
		state.markFailure(errorMessage);
		contentLibraryCrawlStateRepository.save(state);
	}

	/**
	 * 记录货架最近一次刷新失败。REQUIRES_NEW 独立提交，保证刷新自身的失败不会带走这条错误状态。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markShelfFailure(ContentShelfType shelfType, ContentLocale locale, String errorMessage) {
		contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale).ifPresent(cache -> {
			cache.markFailure(errorMessage);
			contentShelfCacheRepository.save(cache);
		});
	}

	/**
	 * 记录分集缓存最近一次刷新失败，REQUIRES_NEW 独立提交。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markEpisodeFailure(String bookId, String filteredTitle, ContentLocale locale, String errorMessage) {
		contentEpisodeCacheRepository.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.ifPresent(cache -> {
					cache.markFailure(errorMessage);
					contentEpisodeCacheRepository.save(cache);
				});
	}

	/**
	 * 记录播放地址缓存最近一次上游失败，REQUIRES_NEW 独立提交。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markVideoFailure(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale, String errorMessage) {
		contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterIdAndLocale(bookId, episodeNum, filteredTitle,
						chapterId, locale)
				.ifPresent(cache -> {
					cache.markFailure(errorMessage);
					contentVideoCacheRepository.save(cache);
				});
	}

	/**
	 * 记录损坏的货架缓存 JSON，REQUIRES_NEW 独立提交（随后通常会触发一次真正的刷新）。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCorruptShelfCache(ContentShelfType shelfType, ContentLocale locale, String errorMessage) {
		contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale).ifPresent(cache -> {
			cache.markFailure(errorMessage);
			contentShelfCacheRepository.save(cache);
		});
	}

	/**
	 * 记录损坏的分集缓存 JSON，REQUIRES_NEW 独立提交（随后通常会触发一次真正的刷新）。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCorruptEpisodeCache(String bookId, String filteredTitle, ContentLocale locale, String errorMessage) {
		contentEpisodeCacheRepository.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.ifPresent(cache -> {
					cache.markFailure(errorMessage);
					contentEpisodeCacheRepository.save(cache);
				});
	}

	private String writeBooks(List<ContentBook> books) {
		try {
			return objectMapper.writeValueAsString(books);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to write content shelf cache", exception);
		}
	}

	private String writeEpisodes(List<ContentEpisode> episodes) {
		try {
			return objectMapper.writeValueAsString(episodes);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to write content episode cache", exception);
		}
	}

	private String writeVideo(ContentVideo video) {
		try {
			return objectMapper.writeValueAsString(video);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to write content video cache", exception);
		}
	}
}
