package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * P1-7 回归保护：上游 HTTP 调用必须发生在数据库事务之外，写库动作由 {@link ContentCacheWriter} 承载。
 */
@SpringBootTest
class ContentCacheServiceTransactionTests {

	/** 记录每次上游调用时是否存在活动事务，并统计调用次数。 */
	static class TransactionAwareContentProvider implements ContentProvider {

		private final List<String> calls = new ArrayList<>();
		private final List<Boolean> transactionFlags = new ArrayList<>();
		private final AtomicBoolean failShelf;
		private final Runnable beforeShelfCall;

		TransactionAwareContentProvider(AtomicBoolean failShelf, Runnable beforeShelfCall) {
			this.failShelf = failShelf;
			this.beforeShelfCall = beforeShelfCall;
		}

		@Override
		public List<ContentBook> search(String keywords, ContentLocale locale) {
			record("search");
			return List.of(book("book-search"));
		}

		@Override
		public List<ContentBook> getShelf(ContentShelfType shelfType, ContentLocale locale) {
			record("getShelf");
			if (beforeShelfCall != null) {
				beforeShelfCall.run();
			}
			if (failShelf.get()) {
				throw new ContentProviderException(503, "content provider unavailable");
			}
			return List.of(book("book-shelf"));
		}

		@Override
		public ContentEpisodesDetail getEpisodesDetail(String bookId, String filteredTitle, ContentLocale locale) {
			record("getEpisodesDetail");
			return new ContentEpisodesDetail(Optional.of(book(bookId)),
					List.of(new ContentEpisode(1, "chapter-1", "Episode 1", "First")));
		}

		@Override
		public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId,
				ContentLocale locale) {
			record("getVideoUrl");
			return new ContentVideo("https://cdn.example.com/1.m3u8", episodeNum, 120, null);
		}

		private void record(String call) {
			calls.add(call);
			transactionFlags.add(TransactionSynchronizationManager.isActualTransactionActive());
		}

		int callCount(String call) {
			return (int) calls.stream().filter(call::equals).count();
		}

		boolean anyCallInsideTransaction() {
			return transactionFlags.stream().anyMatch(Boolean::booleanValue);
		}

		private static ContentBook book(String bookId) {
			return new ContentBook(bookId, "Title " + bookId, "title-" + bookId, "https://example.com/c.jpg", "", 3);
		}
	}

	@MockitoBean
	private ContentProvider contentProvider;

	@Autowired
	private ContentCacheService contentCacheService;

	@Autowired
	private ContentCacheWriter contentCacheWriter;

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

	@BeforeEach
	void cleanCache() {
		contentRefreshRunRepository.deleteAll();
		contentShelfCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
		contentEpisodeCacheRepository.deleteAll();
		contentVideoCacheRepository.deleteAll();
		contentEpisodeRuntimeCacheRepository.deleteAll();
	}

	@Test
	void searchCallsUpstreamOutsideTransactionAndStillWritesCache() {
		TransactionAwareContentProvider provider = new TransactionAwareContentProvider(new AtomicBoolean(false), null);
		contentCacheService = serviceWith(provider);

		List<ContentBook> books = contentCacheService.search("love", ContentLocale.ENGLISH);

		assertThat(books).hasSize(1);
		assertThat(provider.anyCallInsideTransaction()).isFalse();
		assertThat(contentBookCacheRepository.findByBookIdAndLocale("book-search", ContentLocale.ENGLISH)).isPresent();
	}

	@Test
	void getShelfMissCallsUpstreamOutsideTransactionAndPersistsShelf() {
		TransactionAwareContentProvider provider = new TransactionAwareContentProvider(new AtomicBoolean(false), null);
		contentCacheService = serviceWith(provider);

		List<ContentBook> books = contentCacheService.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH);

		assertThat(books).hasSize(1);
		assertThat(provider.anyCallInsideTransaction()).isFalse();
		assertThat(contentShelfCacheRepository.findByShelfTypeAndLocale(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.isPresent()
				.get()
				.satisfies(cache -> assertThat(cache.itemCount()).isEqualTo(1));
	}

	@Test
	void getEpisodesMissCallsUpstreamOutsideTransaction() {
		TransactionAwareContentProvider provider = new TransactionAwareContentProvider(new AtomicBoolean(false), null);
		contentCacheService = serviceWith(provider);

		List<ContentEpisode> episodes = contentCacheService.getEpisodes("book-1", "love-story", ContentLocale.ENGLISH);

		assertThat(episodes).hasSize(1);
		assertThat(provider.anyCallInsideTransaction()).isFalse();
	}

	@Test
	void getVideoUrlCallsUpstreamOutsideTransaction() {
		TransactionAwareContentProvider provider = new TransactionAwareContentProvider(new AtomicBoolean(false), null);
		contentCacheService = serviceWith(provider);

		ContentVideo video = contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1",
				ContentLocale.ENGLISH);

		assertThat(video.duration()).isEqualTo(120);
		assertThat(provider.anyCallInsideTransaction()).isFalse();
	}

	@Test
	void shelfRefreshFailureStillRecordsLastError() {
		TransactionAwareContentProvider provider = new TransactionAwareContentProvider(new AtomicBoolean(true), null);
		contentCacheService = serviceWith(provider);

		assertThatThrownBy(() -> contentCacheService.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");

		assertThat(provider.anyCallInsideTransaction()).isFalse();
	}

	@Test
	void concurrentShelfMissesRunSingleFlightUpstreamCall() throws Exception {
		AtomicBoolean failShelf = new AtomicBoolean(false);
		CountDownLatch upstreamEntered = new CountDownLatch(1);
		CountDownLatch releaseUpstream = new CountDownLatch(1);
		TransactionAwareContentProvider provider = new TransactionAwareContentProvider(failShelf, () -> {
			upstreamEntered.countDown();
			try {
				releaseUpstream.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		ContentCacheService service = serviceWith(provider);

		ExecutorService executor = Executors.newFixedThreadPool(8);
		int callsAfterRelease;
		try {
			List<Future<List<ContentBook>>> futures = new ArrayList<>();
			for (int index = 0; index < 8; index++) {
				futures.add(executor.submit(
						() -> service.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)));
			}
			// 首个上游调用被闩锁卡住时，其余并发 miss 必须是“等待同一个刷新”，而不能各自再打上游。
			assertThat(upstreamEntered.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(provider.callCount("getShelf")).isEqualTo(1);
			releaseUpstream.countDown();
			for (Future<List<ContentBook>> future : futures) {
				assertThat(future.get(20, TimeUnit.SECONDS)).hasSize(1);
			}
			callsAfterRelease = provider.callCount("getShelf");
		}
		finally {
			executor.shutdownNow();
		}

		// 8 个并发 miss 只允许一次上游调用（写入期间到达的个别线程最多再触发一次刷新）。
		assertThat(callsAfterRelease).isBetween(1, 2);
	}

	@Test
	void contentCacheWriterMethodsAreTransactional() throws Exception {
		for (String method : List.of("saveBooks", "saveBook", "saveShelf", "saveEpisodes", "saveVideo",
				"markShelfFailure", "markEpisodeFailure")) {
			boolean transactional = false;
			for (java.lang.reflect.Method candidate : ContentCacheWriter.class.getMethods()) {
				if (candidate.getName().equals(method) && candidate.isAnnotationPresent(
						org.springframework.transaction.annotation.Transactional.class)) {
					transactional = true;
				}
			}
			assertThat(transactional).as("ContentCacheWriter.%s must be @Transactional", method).isTrue();
		}
		assertThat(contentCacheWriter).isNotNull();
	}

	private ContentCacheService serviceWith(ContentProvider provider) {
		return new ContentCacheService(provider, contentShelfCacheRepository, contentBookCacheRepository,
				contentEpisodeCacheRepository, contentVideoCacheRepository, contentRefreshRunRepository,
				new ContentRefreshRunRecorder(contentRefreshRunRepository), new ContentCacheProperties(),
				() -> contentCacheWriter, new ShelfSingleFlight(),
				new com.fasterxml.jackson.databind.ObjectMapper());
	}
}
