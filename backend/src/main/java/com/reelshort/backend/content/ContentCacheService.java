package com.reelshort.backend.content;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ContentCacheService {

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
	private final ObjectMapper objectMapper;

	public ContentCacheService(ContentProvider contentProvider, ContentShelfCacheRepository contentShelfCacheRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentVideoCacheRepository contentVideoCacheRepository,
			ObjectMapper objectMapper) {
		this.contentProvider = contentProvider;
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentVideoCacheRepository = contentVideoCacheRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public List<ContentBook> search(String keywords) {
		return search(keywords, ContentLocale.ENGLISH);
	}

	@Transactional
	public List<ContentBook> search(String keywords, ContentLocale locale) {
		try {
			List<ContentBook> books = contentProvider.search(keywords, locale);
			saveBooks(books, locale);
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

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentBook> getShelf(ContentShelfType shelfType) {
		return getShelf(shelfType, ContentLocale.ENGLISH);
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentBook> getShelf(ContentShelfType shelfType, ContentLocale locale) {
		return contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
				.map(cache -> readShelfCacheOrRefresh(cache, shelfType, locale))
				.orElseGet(() -> refreshShelf(shelfType, locale));
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentBook> refreshShelf(ContentShelfType shelfType) {
		return refreshShelf(shelfType, ContentLocale.ENGLISH);
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentBook> refreshShelf(ContentShelfType shelfType, ContentLocale locale) {
		try {
			List<ContentBook> books = contentProvider.getShelf(shelfType, locale);
			saveBooks(books, locale);
			saveShelf(shelfType, locale, books);
			return books;
		}
		catch (ContentProviderException exception) {
			markShelfFailure(shelfType, locale, exception);
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

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle) {
		return getEpisodes(bookId, filteredTitle, ContentLocale.ENGLISH);
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle, ContentLocale locale) {
		return contentEpisodeCacheRepository.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.map(cache -> readEpisodeCacheOrRefresh(cache, bookId, filteredTitle, locale))
				.orElseGet(() -> refreshEpisodes(bookId, filteredTitle, locale));
	}

	private List<ContentBook> readShelfCacheOrRefresh(ContentShelfCache cache, ContentShelfType shelfType,
			ContentLocale locale) {
		try {
			return readBooks(cache.booksJson());
		}
		catch (IllegalStateException exception) {
			cache.markFailure(exception.getMessage());
			contentShelfCacheRepository.save(cache);
			return refreshShelf(shelfType, locale);
		}
	}

	private List<ContentEpisode> readEpisodeCacheOrRefresh(ContentEpisodeCache cache, String bookId,
			String filteredTitle, ContentLocale locale) {
		try {
			return readEpisodes(cache.episodesJson());
		}
		catch (IllegalStateException exception) {
			cache.markFailure(exception.getMessage());
			contentEpisodeCacheRepository.save(cache);
			return refreshEpisodes(bookId, filteredTitle, locale);
		}
	}

	private List<ContentEpisode> refreshEpisodes(String bookId, String filteredTitle, ContentLocale locale) {
		try {
			ContentEpisodesDetail detail = contentProvider.getEpisodesDetail(bookId, filteredTitle, locale);
			detail.book().ifPresent(book -> saveBook(book, locale));
			saveEpisodes(bookId, filteredTitle, locale, detail.episodes());
			return detail.episodes();
		}
		catch (ContentProviderException exception) {
			markEpisodeFailure(bookId, filteredTitle, locale, exception);
			throw exception;
		}
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		return getVideoUrl(bookId, episodeNum, filteredTitle, chapterId, ContentLocale.ENGLISH);
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale) {
		try {
			ContentVideo video = contentProvider.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId, locale);
			saveVideo(bookId, episodeNum, filteredTitle, chapterId, locale, video);
			return video;
		}
		catch (ContentProviderException exception) {
			return cachedVideoOrThrow(bookId, episodeNum, filteredTitle, chapterId, locale, exception);
		}
	}

	@Transactional(readOnly = true)
	public ContentCacheStatusResponse status() {
		List<ContentCacheStatusResponse.ShelfStatus> shelves = Arrays.stream(ContentShelfType.values())
				.map(this::shelfStatus)
				.toList();
		return new ContentCacheStatusResponse(contentBookCacheRepository.count(), contentEpisodeCacheRepository.count(),
				shelves);
	}

	private ContentCacheStatusResponse.ShelfStatus shelfStatus(ContentShelfType shelfType) {
		return contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, ContentLocale.ENGLISH)
				.map(cache -> new ContentCacheStatusResponse.ShelfStatus(shelfType.apiValue(), cache.itemCount(),
						cache.refreshedAt().toString(), cache.lastError()))
				.orElseGet(() -> new ContentCacheStatusResponse.ShelfStatus(shelfType.apiValue(), 0, null, null));
	}

	private void markShelfFailure(ContentShelfType shelfType, ContentLocale locale, ContentProviderException exception) {
		contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
				.ifPresent(cache -> markShelfFailure(cache, exception));
	}

	private void markEpisodeFailure(String bookId, String filteredTitle, ContentLocale locale,
			ContentProviderException exception) {
		contentEpisodeCacheRepository.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.ifPresent(cache -> {
					cache.markFailure(exception.getMessage());
					contentEpisodeCacheRepository.save(cache);
				});
	}

	private void markShelfFailure(ContentShelfCache cache, ContentProviderException exception) {
		cache.markFailure(exception.getMessage());
		contentShelfCacheRepository.save(cache);
	}

	private void saveShelf(ContentShelfType shelfType, ContentLocale locale, List<ContentBook> books) {
		String booksJson = writeBooks(books);
		ContentShelfCache cache = contentShelfCacheRepository.findByShelfTypeAndLocale(shelfType, locale)
				.orElseGet(() -> ContentShelfCache.create(shelfType, locale, booksJson, books.size()));
		cache.update(booksJson, books.size());
		contentShelfCacheRepository.save(cache);
	}

	private void saveBooks(List<ContentBook> books, ContentLocale locale) {
		for (ContentBook book : books) {
			saveBook(book, locale);
		}
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

	private void saveBook(ContentBook book, ContentLocale locale) {
		ContentBookCache cache = contentBookCacheRepository.findByBookIdAndLocale(book.bookId(), locale)
				.orElseGet(() -> ContentBookCache.from(book, locale));
		cache.update(book);
		contentBookCacheRepository.save(cache);
	}

	private void saveEpisodes(String bookId, String filteredTitle, ContentLocale locale, List<ContentEpisode> episodes) {
		String episodesJson = writeEpisodes(episodes);
		ContentEpisodeCache cache = contentEpisodeCacheRepository
				.findByBookIdAndFilteredTitleAndLocale(bookId, filteredTitle, locale)
				.orElseGet(() -> ContentEpisodeCache.create(bookId, filteredTitle, locale, episodesJson, episodes.size()));
		cache.update(episodesJson, episodes.size());
		contentEpisodeCacheRepository.save(cache);
	}

	private void saveVideo(String bookId, int episodeNum, String filteredTitle, String chapterId, ContentLocale locale,
			ContentVideo video) {
		String videoJson = writeVideo(video);
		ContentVideoCache cache = contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterIdAndLocale(bookId, episodeNum, filteredTitle,
						chapterId, locale)
				.orElseGet(() -> ContentVideoCache.create(bookId, episodeNum, filteredTitle, chapterId, locale,
						videoJson));
		cache.update(videoJson);
		contentVideoCacheRepository.save(cache);
	}

	private ContentVideo cachedVideoOrThrow(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale, ContentProviderException exception) {
		// 播放地址具备时效性，仅当上游不可用（5xx）时回退最后一次缓存；404 表示该集不存在，不回退。
		if (exception.statusCode() == 404) {
			throw exception;
		}
		return contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterIdAndLocale(bookId, episodeNum, filteredTitle,
						chapterId, locale)
				.map(cache -> {
					cache.markFailure(exception.getMessage());
					contentVideoCacheRepository.save(cache);
					return readVideo(cache.videoJson());
				})
				.orElseThrow(() -> exception);
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

	private String writeVideo(ContentVideo video) {
		try {
			return objectMapper.writeValueAsString(video);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to write content video cache", exception);
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
