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
		List<ContentBook> books = contentProvider.search(keywords);
		saveBooks(books);
		return books;
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentBook> getShelf(ContentShelfType shelfType) {
		return contentShelfCacheRepository.findById(shelfType)
				.map(cache -> readShelfCacheOrRefresh(cache, shelfType))
				.orElseGet(() -> refreshShelf(shelfType));
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentBook> refreshShelf(ContentShelfType shelfType) {
		try {
			List<ContentBook> books = contentProvider.getShelf(shelfType);
			saveBooks(books);
			saveShelf(shelfType, books);
			return books;
		}
		catch (ContentProviderException exception) {
			markShelfFailure(shelfType, exception);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public ContentBook getBook(String bookId) {
		return contentBookCacheRepository.findById(bookId)
				.map(ContentBookCache::toContentBook)
				.orElseThrow(() -> new ContentProviderException(404, "content book not cached"));
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle) {
		return contentEpisodeCacheRepository.findByBookIdAndFilteredTitle(bookId, filteredTitle)
				.map(cache -> readEpisodeCacheOrRefresh(cache, bookId, filteredTitle))
				.orElseGet(() -> refreshEpisodes(bookId, filteredTitle));
	}

	private List<ContentBook> readShelfCacheOrRefresh(ContentShelfCache cache, ContentShelfType shelfType) {
		try {
			return readBooks(cache.booksJson());
		}
		catch (IllegalStateException exception) {
			cache.markFailure(exception.getMessage());
			contentShelfCacheRepository.save(cache);
			return refreshShelf(shelfType);
		}
	}

	private List<ContentEpisode> readEpisodeCacheOrRefresh(ContentEpisodeCache cache, String bookId,
			String filteredTitle) {
		try {
			return readEpisodes(cache.episodesJson());
		}
		catch (IllegalStateException exception) {
			cache.markFailure(exception.getMessage());
			contentEpisodeCacheRepository.save(cache);
			return refreshEpisodes(bookId, filteredTitle);
		}
	}

	private List<ContentEpisode> refreshEpisodes(String bookId, String filteredTitle) {
		try {
			ContentEpisodesDetail detail = contentProvider.getEpisodesDetail(bookId, filteredTitle);
			detail.book().ifPresent(this::saveBook);
			saveEpisodes(bookId, filteredTitle, detail.episodes());
			return detail.episodes();
		}
		catch (ContentProviderException exception) {
			markEpisodeFailure(bookId, filteredTitle, exception);
			throw exception;
		}
	}

	@Transactional(noRollbackFor = ContentProviderException.class)
	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		try {
			ContentVideo video = contentProvider.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId);
			saveVideo(bookId, episodeNum, filteredTitle, chapterId, video);
			return video;
		}
		catch (ContentProviderException exception) {
			return cachedVideoOrThrow(bookId, episodeNum, filteredTitle, chapterId, exception);
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
		return contentShelfCacheRepository.findById(shelfType)
				.map(cache -> new ContentCacheStatusResponse.ShelfStatus(shelfType.apiValue(), cache.itemCount(),
						cache.refreshedAt().toString(), cache.lastError()))
				.orElseGet(() -> new ContentCacheStatusResponse.ShelfStatus(shelfType.apiValue(), 0, null, null));
	}

	private void markShelfFailure(ContentShelfType shelfType, ContentProviderException exception) {
		contentShelfCacheRepository.findById(shelfType)
				.ifPresent(cache -> markShelfFailure(cache, exception));
	}

	private void markEpisodeFailure(String bookId, String filteredTitle, ContentProviderException exception) {
		contentEpisodeCacheRepository.findByBookIdAndFilteredTitle(bookId, filteredTitle)
				.ifPresent(cache -> {
					cache.markFailure(exception.getMessage());
					contentEpisodeCacheRepository.save(cache);
				});
	}

	private void markShelfFailure(ContentShelfCache cache, ContentProviderException exception) {
		cache.markFailure(exception.getMessage());
		contentShelfCacheRepository.save(cache);
	}

	private void saveShelf(ContentShelfType shelfType, List<ContentBook> books) {
		String booksJson = writeBooks(books);
		ContentShelfCache cache = contentShelfCacheRepository.findById(shelfType)
				.orElseGet(() -> ContentShelfCache.create(shelfType, booksJson, books.size()));
		cache.update(booksJson, books.size());
		contentShelfCacheRepository.save(cache);
	}

	private void saveBooks(List<ContentBook> books) {
		for (ContentBook book : books) {
			saveBook(book);
		}
	}

	private void saveBook(ContentBook book) {
		ContentBookCache cache = contentBookCacheRepository.findById(book.bookId())
				.orElseGet(() -> ContentBookCache.from(book));
		cache.update(book);
		contentBookCacheRepository.save(cache);
	}

	private void saveEpisodes(String bookId, String filteredTitle, List<ContentEpisode> episodes) {
		String episodesJson = writeEpisodes(episodes);
		ContentEpisodeCache cache = contentEpisodeCacheRepository.findByBookIdAndFilteredTitle(bookId, filteredTitle)
				.orElseGet(() -> ContentEpisodeCache.create(bookId, filteredTitle, episodesJson, episodes.size()));
		cache.update(episodesJson, episodes.size());
		contentEpisodeCacheRepository.save(cache);
	}

	private void saveVideo(String bookId, int episodeNum, String filteredTitle, String chapterId, ContentVideo video) {
		String videoJson = writeVideo(video);
		ContentVideoCache cache = contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId(bookId, episodeNum, filteredTitle, chapterId)
				.orElseGet(() -> ContentVideoCache.create(bookId, episodeNum, filteredTitle, chapterId, videoJson));
		cache.update(videoJson);
		contentVideoCacheRepository.save(cache);
	}

	private ContentVideo cachedVideoOrThrow(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentProviderException exception) {
		// 播放地址具备时效性，仅当上游不可用（5xx）时回退最后一次缓存；404 表示该集不存在，不回退。
		if (exception.statusCode() == 404) {
			throw exception;
		}
		return contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId(bookId, episodeNum, filteredTitle, chapterId)
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
