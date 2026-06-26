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

	private final ContentProvider contentProvider;
	private final ContentShelfCacheRepository contentShelfCacheRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ObjectMapper objectMapper;

	public ContentCacheService(ContentProvider contentProvider, ContentShelfCacheRepository contentShelfCacheRepository,
			ContentBookCacheRepository contentBookCacheRepository, ObjectMapper objectMapper) {
		this.contentProvider = contentProvider;
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public List<ContentBook> search(String keywords) {
		List<ContentBook> books = contentProvider.search(keywords);
		saveBooks(books);
		return books;
	}

	@Transactional
	public List<ContentBook> getShelf(ContentShelfType shelfType) {
		try {
			return refreshShelf(shelfType);
		}
		catch (ContentProviderException exception) {
			return cachedShelfOrThrow(shelfType, exception);
		}
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
	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle) {
		return contentProvider.getEpisodes(bookId, filteredTitle);
	}

	@Transactional(readOnly = true)
	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		return contentProvider.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId);
	}

	@Transactional(readOnly = true)
	public ContentCacheStatusResponse status() {
		List<ContentCacheStatusResponse.ShelfStatus> shelves = Arrays.stream(ContentShelfType.values())
				.map(this::shelfStatus)
				.toList();
		return new ContentCacheStatusResponse(contentBookCacheRepository.count(), shelves);
	}

	private ContentCacheStatusResponse.ShelfStatus shelfStatus(ContentShelfType shelfType) {
		return contentShelfCacheRepository.findById(shelfType)
				.map(cache -> new ContentCacheStatusResponse.ShelfStatus(shelfType.apiValue(), cache.itemCount(),
						cache.refreshedAt().toString(), cache.lastError()))
				.orElseGet(() -> new ContentCacheStatusResponse.ShelfStatus(shelfType.apiValue(), 0, null, null));
	}

	private List<ContentBook> cachedShelfOrThrow(ContentShelfType shelfType, ContentProviderException exception) {
		return contentShelfCacheRepository.findById(shelfType)
				.map(cache -> {
					markShelfFailure(cache, exception);
					return readBooks(cache.booksJson());
				})
				.orElseThrow(() -> exception);
	}

	private void markShelfFailure(ContentShelfType shelfType, ContentProviderException exception) {
		contentShelfCacheRepository.findById(shelfType)
				.ifPresent(cache -> markShelfFailure(cache, exception));
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
			ContentBookCache cache = contentBookCacheRepository.findById(book.bookId())
					.orElseGet(() -> ContentBookCache.from(book));
			cache.update(book);
			contentBookCacheRepository.save(cache);
		}
	}

	private String writeBooks(List<ContentBook> books) {
		try {
			return objectMapper.writeValueAsString(books);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to write content shelf cache", exception);
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
}
