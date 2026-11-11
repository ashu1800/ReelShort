package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;

/**
 * 上游切换必须真的把视频库清掉并重置抓取游标。
 *
 * <p>只用 mock 断言 {@code clearLibraryForUpstreamSwitch()} 被调用是不够的：调用点在配置变更
 * 事件的 {@code afterCommit} 回调里，传播级别用错（REQUIRED）时删除会被 EntityManager
 * 静默丢弃——mock 全绿，线上还留着旧上游的 bookId（不可播的死链）。这里落真行、走真事务、断真结果。
 *
 * <p>旧货架的 {@code content_shelf_cache} 已废弃：切换上游不再清空或重填它，
 * 由 {@link ContentMetadataRefreshService} 的旧刷新入口保留给人工调用。
 */
@SpringBootTest
class ContentUpstreamSwitchTests {

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private ContentShelfCacheRepository contentShelfCacheRepository;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void stubProvider() {
		// 切换会触发后台重抓；返回空目录即可，这里只关心旧行有没有被删掉、游标有没有重置。
		when(contentProvider.getCatalogPage(anyInt(), any(ContentLocale.class)))
				.thenReturn(new ContentProvider.ContentCatalogPage(List.of(), false));
	}

	@AfterEach
	void cleanUp() {
		contentShelfCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
		contentLibraryCrawlStateRepository.deleteAll();
	}

	@Test
	void switchingUpstreamClearsTheContentLibraryAndResetsTheCursor() {
		// 先落一个与目标不同的基线值，确保下面的切换一定触发事件。
		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "TWISTYTV");
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(new ContentBook("old-upstream-book", "Old",
				"old", "https://example.com/old.jpg", "", 3)));
		ContentLibraryCrawlState state = ContentLibraryCrawlState.initial();
		state.recordCrawlSuccess(42, 7, OffsetDateTime.now());
		contentLibraryCrawlStateRepository.saveAndFlush(state);

		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "REELSHORT");

		// 旧上游的 bookId 在新上游不可播，换上游后整库必须作废并从头补齐。
		assertThat(contentBookCacheRepository.count()).isZero();
		assertThat(contentLibraryCrawlStateRepository.findById(ContentLibraryCrawlState.SINGLETON_ID))
				.isPresent()
				.get()
				.satisfies(cleared -> {
					assertThat(cleared.nextPage()).isEqualTo(1);
					assertThat(cleared.lastAdded()).isZero();
					assertThat(cleared.lastError()).isNull();
				});
	}

	@Test
	void resavingTheSameUpstreamKeepsTheContentLibrary() {
		// 先落一个确定的基线值，再把书写进去——顺序很重要，切换本身就会清库。
		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "TWISTYTV");
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(new ContentBook("same-upstream-book", "Same",
				"same", "https://example.com/same.jpg", "", 3)));

		// 值没变就不该触发清库 + 重抓。
		systemConfigService.update(SystemConfigRegistry.CONTENT_UPSTREAM, "TWISTYTV");

		assertThat(contentBookCacheRepository.findByBookIdAndLocale("same-upstream-book", ContentLocale.ENGLISH))
				.isPresent();
	}
}
