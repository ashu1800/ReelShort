package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reelshort.backend.content.ContentProvider.ContentCatalogPage;

/**
 * 视频库抓取编排：种子 12 页 + 每轮 page 0、稳态每轮 1 页、hasMore/异常停止、
 * 重复书不计入 added、首页空库触发抓取。
 */
@SpringBootTest
class ContentLibraryServiceTests {

	@Autowired
	private ContentLibraryService contentLibraryService;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@Autowired
	private ContentVideoCacheRepository contentVideoCacheRepository;

	@Autowired
	private ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository;

	@Autowired
	private ContentLibraryProperties contentLibraryProperties;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void cleanLibrary() {
		contentBookCacheRepository.deleteAll();
		contentEpisodeCacheRepository.deleteAll();
		contentVideoCacheRepository.deleteAll();
		contentLibraryCrawlStateRepository.deleteAll();
		contentLibraryProperties.setEnabled(true);
	}

	@Test
	void firstCrawlSeedsTwelveBrowsePagesPlusTheHomePage() {
		givenCatalogWithOneBookPerPage(30);

		ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED);

		assertThat(result.fetchedPages()).isEqualTo(13);
		assertThat(result.added()).isEqualTo(13);
		assertThat(result.total()).isEqualTo(13);
		assertThat(result.nextPage()).isEqualTo(13);
		assertThat(result.hasMore()).isTrue();
		verify(contentProvider).getCatalogPage(0, ContentLocale.ENGLISH);
		for (int page = 1; page <= ContentLibraryService.SEED_PAGES; page++) {
			verify(contentProvider).getCatalogPage(page, ContentLocale.ENGLISH);
		}
		verify(contentProvider, times(13)).getCatalogPage(anyInt(), any(ContentLocale.class));
		assertThat(contentLibraryCrawlStateRepository.findById(ContentLibraryCrawlState.SINGLETON_ID))
				.isPresent()
				.get()
				.satisfies(state -> {
					assertThat(state.nextPage()).isEqualTo(13);
					assertThat(state.lastAdded()).isEqualTo(13);
					assertThat(state.lastError()).isNull();
					assertThat(state.lastCrawledAt()).isNotNull();
				});
	}

	@Test
	void steadyStateCrawlFetchesTheHomePageAndOneCursorPage() {
		seedState(13);
		givenCatalogWithOneBookPerPage(30);

		ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED);

		assertThat(result.fetchedPages()).isEqualTo(2);
		assertThat(result.nextPage()).isEqualTo(14);
		verify(contentProvider).getCatalogPage(0, ContentLocale.ENGLISH);
		verify(contentProvider).getCatalogPage(13, ContentLocale.ENGLISH);
		verify(contentProvider, never()).getCatalogPage(14, ContentLocale.ENGLISH);
	}

	@Test
	void crawlStopsWhenUpstreamReportsNoMorePagesAndParksTheCursorAtTheEnd() {
		// 稳态从第 7 页开始；第 7 页返回 has_more=false 后不能再翻第 8 页，游标停在 176。
		seedState(7);
		when(contentProvider.getCatalogPage(0, ContentLocale.ENGLISH))
				.thenReturn(new ContentCatalogPage(List.of(book("home-book")), true));
		when(contentProvider.getCatalogPage(7, ContentLocale.ENGLISH))
				.thenReturn(new ContentCatalogPage(List.of(book("last-book")), false));

		ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED);

		assertThat(result.fetchedPages()).isEqualTo(2);
		assertThat(result.hasMore()).isFalse();
		assertThat(result.nextPage()).isEqualTo(ContentLibraryService.TOTAL_PAGES);
		verify(contentProvider, never()).getCatalogPage(8, ContentLocale.ENGLISH);
		assertThat(state().nextPage()).isEqualTo(ContentLibraryService.TOTAL_PAGES);
	}

	@Test
	void exhaustedCursorOnlyFetchesTheHomePage() {
		// 目录抓完后游标停在 176：此后每轮只抓 page 0 发现新书，不再重复扫浏览页。
		seedState(ContentLibraryService.TOTAL_PAGES);
		when(contentProvider.getCatalogPage(0, ContentLocale.ENGLISH))
				.thenReturn(new ContentCatalogPage(List.of(book("new-home-book")), true));

		ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED);

		assertThat(result.fetchedPages()).isEqualTo(1);
		assertThat(result.hasMore()).isFalse();
		assertThat(result.nextPage()).isEqualTo(ContentLibraryService.TOTAL_PAGES);
		verify(contentProvider).getCatalogPage(0, ContentLocale.ENGLISH);
		verify(contentProvider, never()).getCatalogPage(176, ContentLocale.ENGLISH);
		verify(contentProvider, never()).getCatalogPage(1, ContentLocale.ENGLISH);
	}

	@Test
	void duplicateBooksAreStoredOnceAndCountedOnce() {
		// 每页都返回同一本书：首次入库算 1 本，后面 12 页的重复书不能重复计入 added。
		when(contentProvider.getCatalogPage(anyInt(), any(ContentLocale.class)))
				.thenReturn(new ContentCatalogPage(List.of(book("book-same")), true));

		ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED);

		assertThat(result.fetchedPages()).isEqualTo(13);
		assertThat(result.added()).isEqualTo(1);
		assertThat(result.total()).isEqualTo(1);
		assertThat(contentBookCacheRepository.count()).isEqualTo(1);
	}

	@Test
	void upstreamFailureWritesLastErrorAndKeepsTheCursor() {
		seedState(4);
		when(contentProvider.getCatalogPage(0, ContentLocale.ENGLISH))
				.thenReturn(new ContentCatalogPage(List.of(book("home-book")), true));
		when(contentProvider.getCatalogPage(4, ContentLocale.ENGLISH))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		assertThatThrownBy(() -> contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider unavailable");

		ContentLibraryCrawlState state = state();
		assertThat(state.lastError()).isEqualTo("content provider unavailable");
		// 失败不推进游标：下一轮从同一页重试，避免跳页漏书。
		assertThat(state.nextPage()).isEqualTo(4);
	}

	@Test
	void homeBooksCrawlsOnceWhenTheLibraryIsEmpty() {
		givenCatalogWithOneBookPerPage(30);

		List<ContentBook> books = contentLibraryService.homeBooks(10);

		assertThat(books).hasSize(10);
		verify(contentProvider, times(13)).getCatalogPage(anyInt(), any(ContentLocale.class));
	}

	@Test
	void homeBooksSamplesTheLibraryWithoutCallingUpstream() {
		for (int index = 0; index < 5; index++) {
			contentBookCacheRepository.saveAndFlush(ContentBookCache.from(book("book-" + index)));
		}

		List<ContentBook> books = contentLibraryService.homeBooks(3);

		assertThat(books).hasSize(3);
		verifyNoInteractions(contentProvider);
	}

	@Test
	void homeBooksClampsLimitToAtLeastOne() {
		for (int index = 0; index < 3; index++) {
			contentBookCacheRepository.saveAndFlush(ContentBookCache.from(book("book-" + index)));
		}

		assertThat(contentLibraryService.homeBooks(0)).hasSize(1);
	}

	@Test
	void homeBooksReturnsAnEmptyListWhenTheColdCrawlFails() {
		when(contentProvider.getCatalogPage(anyInt(), any(ContentLocale.class)))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		// 未鉴权的首页请求不该因为上游抖动变成 500。
		assertThat(contentLibraryService.homeBooks(10)).isEmpty();
	}

	@Test
	void booksPageClampsPagingAndSearchesCaseInsensitively() {
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("book-love", "Love Story", "love-story", "https://example.com/love.jpg",
						"A romantic drama.", 12)));
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("book-revenge", "Revenge", "revenge", "https://example.com/revenge.jpg",
						"Dark secrets.", 8)));

		ContentBookPageResponse firstPage = contentLibraryService.books(0, 1, null);
		assertThat(firstPage.total()).isEqualTo(2);
		assertThat(firstPage.books()).hasSize(1);
		assertThat(firstPage.page()).isZero();
		assertThat(firstPage.size()).isEqualTo(1);

		ContentBookPageResponse search = contentLibraryService.books(0, 24, "LOVE");
		assertThat(search.total()).isEqualTo(1);
		assertThat(search.books()).extracting(ContentBook::bookId).containsExactly("book-love");

		ContentBookPageResponse clamped = contentLibraryService.books(-3, 500, null);
		assertThat(clamped.page()).isZero();
		assertThat(clamped.size()).isEqualTo(ContentLibraryService.MAX_PAGE_SIZE);
	}

	@Test
	void statusReportsLibraryCountsAndCrawlCursor() {
		seedState(9);
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(book("book-status")));

		ContentLibraryStatusResponse status = contentLibraryService.status();

		assertThat(status.bookCount()).isEqualTo(1);
		assertThat(status.nextPage()).isEqualTo(9);
		assertThat(status.totalPages()).isEqualTo(176);
		assertThat(status.active()).isTrue();
	}

	/**
	 * 每页一本书、hasMore=true；页号大于 30 之后才停，保证种子测试正好抓满 13 页。
	 */
	private void givenCatalogWithOneBookPerPage(int stopAfterPage) {
		when(contentProvider.getCatalogPage(anyInt(), any(ContentLocale.class))).thenAnswer(invocation -> {
			int page = invocation.getArgument(0);
			return new ContentCatalogPage(List.of(book("book-" + page)), page < stopAfterPage);
		});
	}

	private void seedState(int nextPage) {
		ContentLibraryCrawlState state = ContentLibraryCrawlState.initial();
		state.recordCrawlSuccess(nextPage, 0, OffsetDateTime.now());
		contentLibraryCrawlStateRepository.saveAndFlush(state);
	}

	private ContentLibraryCrawlState state() {
		return contentLibraryCrawlStateRepository.findById(ContentLibraryCrawlState.SINGLETON_ID).orElseThrow();
	}

	private ContentBook book(String bookId) {
		return new ContentBook(bookId, "Title " + bookId, bookId.toLowerCase(), "https://example.com/" + bookId
				+ ".jpg", "", 10);
	}
}
