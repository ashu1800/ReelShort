package com.reelshort.backend.content;

import java.util.List;

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
	private final ObjectMapper objectMapper;

	public ContentCacheWriter(ContentShelfCacheRepository contentShelfCacheRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentVideoCacheRepository contentVideoCacheRepository,
			ContentEpisodeRuntimeCacheRepository contentEpisodeRuntimeCacheRepository, ObjectMapper objectMapper) {
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentVideoCacheRepository = contentVideoCacheRepository;
		this.contentEpisodeRuntimeCacheRepository = contentEpisodeRuntimeCacheRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public void saveBooks(List<ContentBook> books, ContentLocale locale) {
		for (ContentBook book : books) {
			saveBook(book, locale);
		}
	}

	@Transactional
	public void saveBook(ContentBook book, ContentLocale locale) {
		// 主键由 (bookId, locale) 确定性派生，按主键查一次即可做到幂等 upsert：
		// 并发刷新同一部剧时不会因为同时插入而撞唯一约束。
		String key = ContentBookCache.key(book.bookId(), locale);
		ContentBookCache cache = contentBookCacheRepository.findById(key)
				.orElseGet(() -> contentBookCacheRepository.findByBookIdAndLocale(book.bookId(), locale)
						.orElseGet(() -> ContentBookCache.from(book, locale)));
		cache.update(book);
		contentBookCacheRepository.save(cache);
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
