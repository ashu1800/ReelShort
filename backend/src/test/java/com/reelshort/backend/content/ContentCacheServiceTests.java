package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

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

	@Autowired
	private ContentVideoCacheRepository contentVideoCacheRepository;

	@Autowired
	private ContentEpisodeRuntimeCacheRepository contentEpisodeRuntimeCacheRepository;

	@Autowired
	private ContentRefreshRunRepository contentRefreshRunRepository;

	@Autowired
	private ContentCacheProperties contentCacheProperties;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void cleanCache() {
		contentRefreshRunRepository.deleteAll();
		contentShelfCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
		contentEpisodeCacheRepository.deleteAll();
		contentVideoCacheRepository.deleteAll();
		contentEpisodeRuntimeCacheRepository.deleteAll();
		contentCacheProperties.setVideoFallbackTtl(Duration.ofMinutes(10));
	}

	@Test
	void getShelfPersistsShelfAndBookCacheWhenProviderSucceeds() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenReturn(List.of(book("book-1", "Recommended")));

		List<ContentBook> books = contentCacheService.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH);

		assertThat(books).containsExactly(book("book-1", "Recommended"));
		assertThat(contentShelfCacheRepository.findByShelfTypeAndLocale(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)).isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.itemCount()).isEqualTo(1);
					assertThat(cache.lastError()).isNull();
					assertThat(cache.refreshedAt()).isNotNull();
				});
		assertThat(contentBookCacheRepository.findByBookIdAndLocale("book-1", ContentLocale.ENGLISH)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.title()).isEqualTo("Recommended"));
	}

	@Test
	void getShelfCachesSameBookIdSeparatelyByLocale() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenReturn(List.of(book("book-1", "Love Story")));
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE))
				.thenReturn(List.of(book("book-1", "愛情故事")));

		List<ContentBook> english = contentCacheService.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH);
		List<ContentBook> traditionalChinese = contentCacheService.getShelf(
				ContentShelfType.RECOMMEND,
				ContentLocale.TRADITIONAL_CHINESE);

		assertThat(english).extracting(ContentBook::title).containsExactly("Love Story");
		assertThat(traditionalChinese).extracting(ContentBook::title).containsExactly("愛情故事");
		assertThat(contentBookCacheRepository.findByBookIdAndLocale("book-1", ContentLocale.ENGLISH)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.title()).isEqualTo("Love Story"));
		assertThat(contentBookCacheRepository.findByBookIdAndLocale("book-1", ContentLocale.TRADITIONAL_CHINESE)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.title()).isEqualTo("愛情故事"));
	}

	@Test
	void getShelfPersistsTraditionalChineseBookUsingStableIdForRealBookIdLength() {
		String longBookId = "670f2d8f4f3f2cb1c8ab1234";
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE))
				.thenReturn(List.of(new ContentBook(
						longBookId,
						"億萬戀人",
						"billionaire-lover",
						"https://example.com/cover.jpg",
						"desc",
						88)));

		List<ContentBook> books = contentCacheService.getShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE);

		assertThat(books).extracting(ContentBook::bookId).containsExactly(longBookId);
		assertThat(contentBookCacheRepository.findByBookIdAndLocale(longBookId, ContentLocale.TRADITIONAL_CHINESE)).isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.title()).isEqualTo("億萬戀人");
					assertThat(cache.filteredTitle()).isEqualTo("billionaire-lover");
				});
	}

	@Test
	void searchFallsBackToCachedMetadataWhenProviderFails() {
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("book-local", "Don't Toy with My Heart, Mr. Billionaire", "billionaire",
						"https://example.com/local.jpg", "A rich CEO romance.", 60),
				ContentLocale.ENGLISH));
		when(contentProvider.search("Billionaire", ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(404, "content provider returned 404"));

		List<ContentBook> books = contentCacheService.search("Billionaire", ContentLocale.ENGLISH);

		assertThat(books).extracting(ContentBook::bookId).containsExactly("book-local");
	}

	@Test
	void searchCachedFallbackKeepsLocaleBucketsIsolated() {
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("book-en", "Love Contract", "love-contract", "https://example.com/en.jpg", "", 20),
				ContentLocale.ENGLISH));
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("book-zh", "愛情契約", "love-contract", "https://example.com/zh.jpg", "", 20),
				ContentLocale.TRADITIONAL_CHINESE));
		when(contentProvider.search("愛情", ContentLocale.TRADITIONAL_CHINESE))
				.thenThrow(new ContentProviderException(404, "content provider returned 404"));

		List<ContentBook> books = contentCacheService.search("愛情", ContentLocale.TRADITIONAL_CHINESE);

		assertThat(books).extracting(ContentBook::bookId).containsExactly("book-zh");
	}

	@Test
	void getShelfReturnsCachedShelfWithoutCallingProviderWhenCacheExists() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenReturn(List.of(book("book-1", "Recommended")));

		contentCacheService.getShelf(ContentShelfType.RECOMMEND);
		List<ContentBook> cachedBooks = contentCacheService.getShelf(ContentShelfType.RECOMMEND);

		assertThat(cachedBooks).containsExactly(book("book-1", "Recommended"));
		verify(contentProvider, times(1)).getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH);
		assertThat(contentShelfCacheRepository.findById(ContentShelfType.RECOMMEND)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isNull());
	}

	@Test
	void getShelfRefreshesCacheWhenCachedShelfJsonIsCorrupt() {
		contentShelfCacheRepository.saveAndFlush(ContentShelfCache.create(ContentShelfType.RECOMMEND, "{broken", 1));
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)).thenReturn(List.of(book("book-1", "Recovered")));

		List<ContentBook> books = contentCacheService.getShelf(ContentShelfType.RECOMMEND);

		assertThat(books).containsExactly(book("book-1", "Recovered"));
		verify(contentProvider).getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH);
		assertThat(contentShelfCacheRepository.findById(ContentShelfType.RECOMMEND)).isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.itemCount()).isEqualTo(1);
					assertThat(cache.lastError()).isNull();
				});
	}

	@Test
	void getShelfRecordsCorruptCacheErrorWhenRefreshFails() {
		contentShelfCacheRepository.saveAndFlush(ContentShelfCache.create(ContentShelfType.RECOMMEND, "{broken", 1));
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getShelf(ContentShelfType.RECOMMEND))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");

		assertThat(contentShelfCacheRepository.findById(ContentShelfType.RECOMMEND)).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isEqualTo("content provider unavailable"));
	}

	@Test
	void getShelfPropagatesProviderFailureWhenNoCacheExists() {
		when(contentProvider.getShelf(ContentShelfType.NEW_RELEASE, ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getShelf(ContentShelfType.NEW_RELEASE))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void refreshShelfRecordsLastErrorWhenProviderFailsAndCacheExists() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
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
	void refreshShelfWithRunRecordsSuccess() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenReturn(List.of(book("book-1", "Recommended")));

		List<ContentBook> books = contentCacheService.refreshShelf(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.ADMIN);

		assertThat(books).hasSize(1);
		assertThat(contentRefreshRunRepository.findTop10ByOrderByStartedAtDesc())
				.singleElement()
				.satisfies(run -> {
					assertThat(run.triggerSource()).isEqualTo(ContentRefreshTriggerSource.ADMIN);
					assertThat(run.shelfType()).isEqualTo(ContentShelfType.RECOMMEND);
					assertThat(run.locale()).isEqualTo(ContentLocale.ENGLISH);
					assertThat(run.status()).isEqualTo(ContentRefreshRunStatus.SUCCESS);
					assertThat(run.itemCount()).isEqualTo(1);
					assertThat(run.errorMessage()).isNull();
					assertThat(run.durationMillis()).isGreaterThanOrEqualTo(0);
				});
	}

	@Test
	void refreshShelfWithRunRecordsFailureAndRethrows() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.refreshShelf(
				ContentShelfType.RECOMMEND,
				ContentLocale.TRADITIONAL_CHINESE,
				ContentRefreshTriggerSource.SCHEDULED))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");

		assertThat(contentRefreshRunRepository.findTop10ByOrderByStartedAtDesc())
				.singleElement()
				.satisfies(run -> {
					assertThat(run.triggerSource()).isEqualTo(ContentRefreshTriggerSource.SCHEDULED);
					assertThat(run.locale()).isEqualTo(ContentLocale.TRADITIONAL_CHINESE);
					assertThat(run.status()).isEqualTo(ContentRefreshRunStatus.FAILED);
					assertThat(run.itemCount()).isZero();
					assertThat(run.errorMessage()).isEqualTo("content provider unavailable");
				});
	}

	@Test
	void refreshShelfWithRunRecordsUnexpectedFailureAndRethrows() {
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenThrow(new IllegalStateException("provider returned invalid metadata"));

		assertThatThrownBy(() -> contentCacheService.refreshShelf(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.ADMIN))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("provider returned invalid metadata");

		assertThat(contentRefreshRunRepository.findTop10ByOrderByStartedAtDesc())
				.singleElement()
				.satisfies(run -> {
					assertThat(run.triggerSource()).isEqualTo(ContentRefreshTriggerSource.ADMIN);
					assertThat(run.status()).isEqualTo(ContentRefreshRunStatus.FAILED);
					assertThat(run.errorMessage()).isEqualTo("provider returned invalid metadata");
				});
	}

	@Test
	void refreshShelfWithRunRecordsDatabaseFailureBeforeSuccess() {
		String tooLongTitle = "T".repeat(300);
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.thenReturn(List.of(new ContentBook("book-invalid", tooLongTitle, "invalid",
						"https://example.com/invalid.jpg", "", 1)));

		assertThatThrownBy(() -> contentCacheService.refreshShelf(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.ADMIN))
				.isInstanceOf(RuntimeException.class);

		assertThat(contentRefreshRunRepository.findTop10ByOrderByStartedAtDesc())
				.singleElement()
				.satisfies(run -> {
					assertThat(run.triggerSource()).isEqualTo(ContentRefreshTriggerSource.ADMIN);
					assertThat(run.status()).isEqualTo(ContentRefreshRunStatus.FAILED);
					assertThat(run.errorMessage()).isNotBlank();
				});
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
		List<ContentEpisode> episodes = List.of(new ContentEpisode(1, "chapter-1", "", ""));
		when(contentProvider.getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH))
				.thenReturn(new ContentEpisodesDetail(java.util.Optional.empty(), episodes));

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
	void getEpisodesReturnsCachedEpisodesWithoutCallingProviderWhenCacheExists() {
		List<ContentEpisode> episodes = List.of(new ContentEpisode(1, "chapter-1", "", ""));
		when(contentProvider.getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH))
				.thenReturn(new ContentEpisodesDetail(java.util.Optional.empty(), episodes));

		contentCacheService.getEpisodes("book-1", "love-story");
		List<ContentEpisode> cached = contentCacheService.getEpisodes("book-1", "love-story");

		assertThat(cached).containsExactlyElementsOf(episodes);
		assertThat(contentEpisodeCacheRepository.findByBookIdAndFilteredTitle("book-1", "love-story")).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isNull());
		verify(contentProvider, times(1)).getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH);
		verifyNoMoreInteractions(contentProvider);
	}

	@Test
	void getEpisodesRefreshesCacheWhenCachedEpisodeJsonIsCorrupt() {
		contentEpisodeCacheRepository.saveAndFlush(ContentEpisodeCache.create("book-1", "love-story", "{broken", 1));
		List<ContentEpisode> episodes = List.of(new ContentEpisode(1, "chapter-1", "", ""));
		when(contentProvider.getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH))
				.thenReturn(new ContentEpisodesDetail(Optional.empty(), episodes));

		List<ContentEpisode> response = contentCacheService.getEpisodes("book-1", "love-story");

		assertThat(response).containsExactlyElementsOf(episodes);
		verify(contentProvider).getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH);
		assertThat(contentEpisodeCacheRepository.findByBookIdAndFilteredTitle("book-1", "love-story")).isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.episodeCount()).isEqualTo(1);
					assertThat(cache.lastError()).isNull();
				});
	}

	@Test
	void getEpisodesRecordsCorruptCacheErrorWhenRefreshFails() {
		contentEpisodeCacheRepository.saveAndFlush(ContentEpisodeCache.create("book-1", "love-story", "{broken", 1));
		when(contentProvider.getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getEpisodes("book-1", "love-story"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");

		assertThat(contentEpisodeCacheRepository.findByBookIdAndFilteredTitle("book-1", "love-story")).isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isEqualTo("content provider unavailable"));
	}

	@Test
	void getEpisodesPropagatesProviderFailureWhenNoCacheExists() {
		when(contentProvider.getEpisodesDetail("book-1", "love-story", ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getEpisodes("book-1", "love-story"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void cacheStatusIncludesAllShelvesAndBookCount() {
		when(contentProvider.getShelf(ContentShelfType.DRAMA_DUB, ContentLocale.ENGLISH)).thenReturn(List.of(book("book-dub", "Dub")));
		contentCacheService.getShelf(ContentShelfType.DRAMA_DUB);

		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.bookCount()).isEqualTo(1);
		assertThat(status.videoCacheCount()).isZero();
		assertThat(status.recentRefreshRuns()).isEmpty();
		assertThat(status.shelves())
				.hasSize(6);
		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("drama-dub") && shelf.locale().equals("en"))
				.singleElement()
				.satisfies(shelf -> assertThat(shelf.itemCount()).isEqualTo(1));
		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("drama-dub") && shelf.locale().equals("zh-TW"))
				.singleElement()
				.satisfies(shelf -> assertThat(shelf.itemCount()).isZero());
	}

	@Test
	void cacheStatusMarksMissingShelfHealth() {
		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("recommend") && shelf.locale().equals("en"))
				.singleElement()
				.satisfies(shelf -> {
					assertThat(shelf.health()).isEqualTo("MISSING");
					assertThat(shelf.healthMessage()).isEqualTo("not refreshed yet");
				});
	}

	@Test
	void cacheStatusMarksEmptyShelfHealth() {
		contentShelfCacheRepository.saveAndFlush(ContentShelfCache.create(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				"[]",
				0));

		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("recommend") && shelf.locale().equals("en"))
				.singleElement()
				.satisfies(shelf -> {
					assertThat(shelf.health()).isEqualTo("EMPTY");
					assertThat(shelf.healthMessage()).isEqualTo("last refresh returned no books");
				});
	}

	@Test
	void cacheStatusMarksErrorShelfHealthBeforeOtherWarnings() {
		ContentShelfCache cache = ContentShelfCache.create(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				"[]",
				0);
		cache.markFailure("content provider unavailable");
		contentShelfCacheRepository.saveAndFlush(cache);

		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("recommend") && shelf.locale().equals("en"))
				.singleElement()
				.satisfies(shelf -> {
					assertThat(shelf.health()).isEqualTo("ERROR");
					assertThat(shelf.healthMessage()).isEqualTo("content provider unavailable");
				});
	}

	@Test
	void cacheStatusMarksStaleShelfHealth() {
		ContentShelfCache cache = ContentShelfCache.create(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				"[{}]",
				1);
		ReflectionTestUtils.setField(cache, "refreshedAt", OffsetDateTime.now().minusHours(13));
		contentShelfCacheRepository.saveAndFlush(cache);

		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("recommend") && shelf.locale().equals("en"))
				.singleElement()
				.satisfies(shelf -> {
					assertThat(shelf.health()).isEqualTo("STALE");
					assertThat(shelf.healthMessage()).isEqualTo("last refresh is older than 12 hours");
				});
	}

	@Test
	void cacheStatusMarksHealthyShelfHealth() {
		contentShelfCacheRepository.saveAndFlush(ContentShelfCache.create(
				ContentShelfType.RECOMMEND,
				ContentLocale.ENGLISH,
				"[{}]",
				1));

		ContentCacheStatusResponse status = contentCacheService.status();

		assertThat(status.shelves())
				.filteredOn(shelf -> shelf.shelfType().equals("recommend") && shelf.locale().equals("en"))
				.singleElement()
				.satisfies(shelf -> {
					assertThat(shelf.health()).isEqualTo("HEALTHY");
					assertThat(shelf.healthMessage()).isEqualTo("cache is fresh");
				});
	}

	@Test
	void getVideoUrlPersistsVideoCacheWhenProviderSucceeds() {
		ContentVideo video = new ContentVideo("https://cdn.example.com/1.m3u8", 1, 120, null);
		when(contentProvider.getVideoUrl("book-1", 1, "love-story", "chapter-1", ContentLocale.ENGLISH)).thenReturn(video);

		ContentVideo response = contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1");

		assertThat(response).isEqualTo(video);
		assertThat(contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId("book-1", 1, "love-story", "chapter-1"))
				.isPresent()
				.get()
				.satisfies(cache -> {
					assertThat(cache.lastError()).isNull();
					assertThat(cache.refreshedAt()).isNotNull();
				});
		assertThat(contentEpisodeRuntimeCacheRepository
				.findByBookIdAndEpisodeNumAndChapterId("book-1", 1, "chapter-1"))
				.isPresent()
				.get()
				.extracting(ContentEpisodeRuntimeCache::durationSeconds)
				.isEqualTo(120);
	}

	@Test
	void getVideoUrlFallsBackToCachedVideoWhenProviderUnavailable() {
		ContentVideo video = new ContentVideo("https://cdn.example.com/1.m3u8", 1, 120, null);
		when(contentProvider.getVideoUrl("book-1", 1, "love-story", "chapter-1", ContentLocale.ENGLISH))
				.thenReturn(video)
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1");
		ContentVideo cached = contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1");

		assertThat(cached).isEqualTo(video);
		assertThat(contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId("book-1", 1, "love-story", "chapter-1"))
				.isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.lastError()).isEqualTo("content provider unavailable"));
	}

	@Test
	void getVideoUrlDoesNotFallbackToExpiredCachedVideoWhenProviderUnavailable() {
		ContentVideo video = new ContentVideo("https://cdn.example.com/1.m3u8", 1, 120, null);
		when(contentProvider.getVideoUrl("book-1", 1, "love-story", "chapter-1", ContentLocale.ENGLISH))
				.thenReturn(video)
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1");
		ContentVideoCache cache = contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId("book-1", 1, "love-story", "chapter-1")
				.orElseThrow();
		ReflectionTestUtils.setField(cache, "refreshedAt", OffsetDateTime.now().minusMinutes(11));
		contentVideoCacheRepository.saveAndFlush(cache);

		assertThatThrownBy(() -> contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void getVideoUrlDoesNotFallbackWhenVideoFallbackTtlIsDisabled() {
		contentCacheProperties.setVideoFallbackTtl(Duration.ZERO);
		ContentVideo video = new ContentVideo("https://cdn.example.com/1.m3u8", 1, 120, null);
		when(contentProvider.getVideoUrl("book-1", 1, "love-story", "chapter-1", ContentLocale.ENGLISH))
				.thenReturn(video)
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1");

		assertThatThrownBy(() -> contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void getVideoUrlDoesNotFallbackToCachedVideoForNonServerProviderFailure() {
		ContentVideo video = new ContentVideo("https://cdn.example.com/1.m3u8", 1, 120, null);
		when(contentProvider.getVideoUrl("book-1", 1, "love-story", "chapter-1", ContentLocale.ENGLISH))
				.thenReturn(video)
				.thenThrow(new ContentProviderException(400, "bad playback request"));

		contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1");

		assertThatThrownBy(() -> contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("bad playback request");
	}

	@Test
	void getVideoUrlPropagatesProviderFailureWhenNoCacheExists() {
		when(contentProvider.getVideoUrl("book-1", 1, "love-story", "chapter-1", ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");
	}

	@Test
	void getVideoUrlDoesNotFallbackOn404() {
		when(contentProvider.getVideoUrl("book-1", 99, "love-story", "missing", ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(404, "upstream not found"));

		assertThatThrownBy(() -> contentCacheService.getVideoUrl("book-1", 99, "love-story", "missing"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("upstream not found");
		assertThat(contentVideoCacheRepository
				.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId("book-1", 99, "love-story", "missing"))
				.isEmpty();
	}

	@Test
	void getEpisodesPopulatesBookCacheForDetailLookup() {
		ContentBook book = book("book-detail", "Detail");
		when(contentProvider.getEpisodesDetail("book-detail", "detail", ContentLocale.ENGLISH))
				.thenReturn(new ContentEpisodesDetail(Optional.of(book),
						List.of(new ContentEpisode(1, "chapter-1", "", ""))));

		contentCacheService.getEpisodes("book-detail", "detail");

		assertThat(contentCacheService.getBook("book-detail")).isEqualTo(book);
	}

	@Test
	void getEpisodesSkipsBookBackfillWhenBookAbsent() {
		when(contentProvider.getEpisodesDetail("book-sparse", "sparse", ContentLocale.ENGLISH))
				.thenReturn(new ContentEpisodesDetail(Optional.empty(),
						List.of(new ContentEpisode(1, "chapter-1", "", ""))));

		contentCacheService.getEpisodes("book-sparse", "sparse");

		assertThatThrownBy(() -> contentCacheService.getBook("book-sparse"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content book not cached");
	}

	private ContentBook book(String bookId, String title) {
		return new ContentBook(bookId, title, title.toLowerCase(), "https://example.com/" + bookId + ".jpg", "", 10);
	}
}
