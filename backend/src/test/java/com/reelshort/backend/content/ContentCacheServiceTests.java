package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ContentCacheServiceTests {

	@Autowired
	private ContentCacheService contentCacheService;

	@Autowired
	private ContentShelfCacheRepository contentShelfCacheRepository;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void cleanCache() {
		contentShelfCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
		contentEpisodeCacheRepository.deleteAll();
	}

	@Test
	void getShelfPersistsShelfAndBookCacheWhenProviderSucceeds() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND)).thenReturn(List.of(book("book-1", "Recommended")));

		List<ContentBook> books = contentCacheService.getShelf(ContentShelfType.RECOMMEND);

		assertThat(books).containsExactly(book("book-1", "Recommended"));
		assertThat(contentShelfCacheRepository.findById(ContentShelfType.RECOMMEND)).isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.itemCount()).isEqualTo(1);
					assertThat(cache.lastError()).isNull();
					assertThat(cache.refreshedAt()).isNotNull();
				});
		assertThat(contentBookCacheRepository.findById("book-1")).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.title()).isEqualTo("Recommended"));
	}

	@Test
	void getShelfFallsBackToCachedShelfWhenProviderFails() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND))
				.thenReturn(List.of(book("book-1", "Recommended")))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		contentCacheService.getShelf(ContentShelfType.RECOMMEND);
		List<ContentBook> cachedBooks = contentCacheService.getShelf(ContentShelfType.RECOMMEND);

		assertThat(cachedBooks).containsExactly(book("book-1", "Recommended"));
		assertThat(contentShelfCacheRepository.findById(ContentShelfType.RECOMMEND)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isEqualTo("content provider unavailable"));
	}

	@Test
	void getShelfPropagatesProviderFailureWhenNoCacheExists() {
		when(contentProvider.getShelf(ContentShelfType.NEW_RELEASE))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getShelf(ContentShelfType.NEW_RELEASE))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void refreshShelfRecordsLastErrorWhenProviderFailsAndCacheExists() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND))
				.thenReturn(List.of(book("book-1", "Recommended")))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));
		contentCacheService.refreshShelf(ContentShelfType.RECOMMEND);

		assertThatThrownBy(() -> contentCacheService.refreshShelf(ContentShelfType.RECOMMEND))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");

		assertThat(contentShelfCacheRepository.findById(ContentShelfType.RECOMMEND)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isEqualTo("content provider unavailable"));
	}

	@Test
	void getBookReturnsCachedBookDetail() {
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(book("book-detail", "Detail")));

		ContentBook book = contentCacheService.getBook("book-detail");

		assertThat(book).isEqualTo(book("book-detail", "Detail"));
	}

	@Test
	void getBookThrowsNotFoundWhenBookIsNotCached() {
		assertThatThrownBy(() -> contentCacheService.getBook("missing-book"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content book not cached");
	}

	@Test
	void getEpisodesPersistsEpisodeCacheWhenProviderSucceeds() {
		List<ContentEpisode> episodes = List.of(new ContentEpisode(1, "chapter-1"));
		when(contentProvider.getEpisodes("book-1", "love-story")).thenReturn(episodes);

		List<ContentEpisode> response = contentCacheService.getEpisodes("book-1", "love-story");

		assertThat(response).containsExactlyElementsOf(episodes);
		assertThat(contentEpisodeCacheRepository.findByBookIdAndFilteredTitle("book-1", "love-story")).isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.episodeCount()).isEqualTo(1);
					assertThat(cache.lastError()).isNull();
				});
	}

	@Test
	void getEpisodesFallsBackToCachedEpisodesWhenProviderFails() {
		List<ContentEpisode> episodes = List.of(new ContentEpisode(1, "chapter-1"));
		when(contentProvider.getEpisodes("book-1", "love-story"))
				.thenReturn(episodes)
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		contentCacheService.getEpisodes("book-1", "love-story");
		List<ContentEpisode> cached = contentCacheService.getEpisodes("book-1", "love-story");

		assertThat(cached).containsExactlyElementsOf(episodes);
		assertThat(contentEpisodeCacheRepository.findByBookIdAndFilteredTitle("book-1", "love-story")).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isEqualTo("content provider unavailable"));
	}

	@Test
	void getEpisodesPropagatesProviderFailureWhenNoCacheExists() {
		when(contentProvider.getEpisodes("book-1", "love-story"))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getEpisodes("book-1", "love-story"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void cacheStatusIncludesAllShelvesAndBookCount() {
		when(contentProvider.getShelf(ContentShelfType.DRAMA_DUB)).thenReturn(List.of(book("book-dub", "Dub")));
		contentCacheService.getShelf(ContentShelfType.DRAMA_DUB);

		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.bookCount()).isEqualTo(1);
		assertThat(status.shelves())
				.extracting(ContentCacheStatusResponse.ShelfStatus::shelfType)
				.containsExactlyInAnyOrder("recommend", "new-release", "drama-dub");
		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("drama-dub"))
				.singleElement()
				.satisfies(shelf -> assertThat(shelf.itemCount()).isEqualTo(1));
	}

	private ContentBook book(String bookId, String title) {
		return new ContentBook(bookId, title, title.toLowerCase(), "https://example.com/" + bookId + ".jpg", 10);
	}
}
