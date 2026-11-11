package com.reelshort.backend.content;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.reelshort.backend.system.config.SystemConfigChangedEvent;
import com.reelshort.backend.system.config.SystemConfigRegistry;

/**
 * 视频库：content_book_cache 从“货架快照的副产品”升级为只增不减的片库。
 *
 * <p>生产每 30 分钟从内容源目录拉一页新书入库（首次部署一次补齐前 12 页作为种子），
 * 首页按随机抽样的方式返回，不再复用旧的三货架快照。旧货架代码与表暂时保留。
 *
 * <p>P1-7 的边界同样适用：上游 HTTP 调用一律在数据库事务之外执行，写库交给
 * {@link ContentCacheWriter}（每方法自带事务边界）。
 */
@Service
public class ContentLibraryService {

	private static final Logger LOG = LoggerFactory.getLogger(ContentLibraryService.class);

	/** 片库只有一套英文数据。 */
	private static final ContentLocale LOCALE = ContentLocale.ENGLISH;

	/** 内容源目录总页数：page 0 首页入口 + /browse 的第 1..175 页。后台仪表盘也用它。 */
	public static final int TOTAL_PAGES = 176;

	/** 首次部署的种子页数：/browse 前 12 页（每页约 60 部）。 */
	static final int SEED_PAGES = 12;

	/** 库存低于该值时，从 nextPage==1 开始的一轮按种子规模补齐。 */
	static final int SEED_BOOK_THRESHOLD = 600;

	/** 首页抽样的硬上限：配置写大也不能一次抽全表。 */
	static final int MAX_SAMPLE_LIMIT = 1000;

	/** 后台分页上限。 */
	static final int MAX_PAGE_SIZE = 100;

	private final ContentProvider contentProvider;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final ContentVideoCacheRepository contentVideoCacheRepository;
	private final ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository;
	private final ContentCacheWriterProvider contentCacheWriterProvider;
	private final ContentLibraryProperties properties;

	public ContentLibraryService(ContentProvider contentProvider,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentVideoCacheRepository contentVideoCacheRepository,
			ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository,
			ContentCacheWriterProvider contentCacheWriterProvider,
			ContentLibraryProperties properties) {
		this.contentProvider = contentProvider;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentVideoCacheRepository = contentVideoCacheRepository;
		this.contentLibraryCrawlStateRepository = contentLibraryCrawlStateRepository;
		this.contentCacheWriterProvider = contentCacheWriterProvider;
		this.properties = properties;
	}

	/**
	 * 首页推荐：从片库随机抽一批。
	 *
	 * <p>库为空（首次部署、抓取还没跑完）时先同步抓一轮再查一次；抓取失败只记日志并
	 * 返回空列表——未鉴权的首页请求不该因为上游抖动变成 500。
	 */
	public List<ContentBook> homeBooks(int limit) {
		int resolvedLimit = Math.min(Math.max(limit, 1), MAX_SAMPLE_LIMIT);
		List<ContentBook> books = randomBooks(resolvedLimit);
		if (books.isEmpty()) {
			try {
				crawl(ContentRefreshTriggerSource.SCHEDULED);
			}
			catch (RuntimeException exception) {
				LOG.debug("Home books falling back to an empty list: {}", exception.getMessage());
			}
			books = randomBooks(resolvedLimit);
		}
		return books;
	}

	/**
	 * 从内容源目录抓一轮入库。
	 *
	 * <p>每轮先拉 page 0（首页入口，用来发现新剧），再按 nextPage 顺序拉浏览页：
	 * 首次部署（nextPage==1 且库存不足 600）一次补齐 12 页，稳态每轮 1 页。
	 * 上游 hasMore=false 或抛异常即停止本轮；游标停在 176 表示目录已抓完，
	 * 此后每轮只拉 page 0 继续发现新书（不会绕回第 1 页重复抓已入库的页）。
	 * 失败只记 lastError，下一轮从同一页重试。
	 */
	public ContentLibraryCrawlResult crawl(ContentRefreshTriggerSource trigger) {
		ContentLibraryCrawlState state = loadOrCreateState();
		long beforeCount = contentBookCacheRepository.count();
		int pagesToFetch = state.nextPage() == 1 && beforeCount < SEED_BOOK_THRESHOLD ? SEED_PAGES : 1;
		int fetchedPages = 0;
		int nextPage = Math.max(1, state.nextPage());
		try {
			ContentProvider.ContentCatalogPage homePage = contentProvider.getCatalogPage(0, LOCALE);
			writer().saveBooks(homePage.books(), LOCALE);
			fetchedPages++;
			// page 0 每轮都抓（发现新书），但不用它的 hasMore 控制浏览页游标。
			for (int offset = 0; offset < pagesToFetch && nextPage <= TOTAL_PAGES - 1; offset++) {
				ContentProvider.ContentCatalogPage catalogPage = contentProvider.getCatalogPage(nextPage, LOCALE);
				writer().saveBooks(catalogPage.books(), LOCALE);
				fetchedPages++;
				nextPage++;
				if (!catalogPage.hasMore()) {
					// 上游没有下一页：游标停在 176，稳态只抓 page 0，不再重复扫已入库的页。
					nextPage = TOTAL_PAGES;
					break;
				}
			}
			boolean hasMore = nextPage <= TOTAL_PAGES - 1;
			long total = contentBookCacheRepository.count();
			int added = (int) Math.max(0, total - beforeCount);
			writer().recordLibraryCrawlSuccess(nextPage, added);
			return new ContentLibraryCrawlResult(added, fetchedPages, total, nextPage, hasMore);
		}
		catch (RuntimeException exception) {
			LOG.debug("Content library crawl ({}) failed: {}", trigger, exception.getMessage());
			writer().markLibraryCrawlFailure(exception.getMessage());
			throw exception;
		}
	}

	public ContentLibraryStatusResponse status() {
		ContentLibraryCrawlState state = contentLibraryCrawlStateRepository
				.findById(ContentLibraryCrawlState.SINGLETON_ID)
				.orElse(null);
		return new ContentLibraryStatusResponse(
				contentBookCacheRepository.count(),
				contentEpisodeCacheRepository.count(),
				contentVideoCacheRepository.count(),
				state == null ? 1 : state.nextPage(),
				TOTAL_PAGES,
				state == null || state.lastCrawledAt() == null ? null : state.lastCrawledAt().toString(),
				state == null ? null : state.lastError(),
				state == null ? 0 : state.lastAdded(),
				properties.isEnabled());
	}

	/**
	 * 后台视频库分页：page 负数归 0，size 收敛到 1..100；query 非空时按标题/bookId/过滤标题/简介模糊匹配。
	 */
	public ContentBookPageResponse books(int page, int size, String query) {
		int resolvedPage = Math.max(0, page);
		int resolvedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		String keyword = query == null ? "" : query.trim();
		PageRequest pageRequest = PageRequest.of(resolvedPage, resolvedSize);
		Page<ContentBookCache> result = keyword.isEmpty()
				? contentBookCacheRepository.findByLocaleOrderByCreatedAtDesc(LOCALE, pageRequest)
				: contentBookCacheRepository.searchByLocale(LOCALE.name(),
						"%" + keyword.toLowerCase(Locale.ROOT) + "%", pageRequest);
		return new ContentBookPageResponse(
				result.getContent().stream().map(ContentBookCache::toContentBook).toList(),
				result.getTotalElements(),
				resolvedPage,
				resolvedSize);
	}

	/**
	 * 切换内容上游后作废视频库并重置抓取游标，然后后台线程立刻补种子。
	 *
	 * <p>这是“视频库只增不减”的唯一例外：旧上游的 bookId 在新上游下不可播，留着会变成死链。
	 * 清空必须走 {@link ContentCacheWriter#clearLibraryForUpstreamSwitch()}（REQUIRES_NEW），
	 * 不能在本方法里直接写库；重抓放后台线程，避免管理员的保存请求等一整轮抓取。
	 */
	@EventListener
	public void onConfigChanged(SystemConfigChangedEvent event) {
		if (!SystemConfigRegistry.CONTENT_UPSTREAM.equals(event.key())) {
			return;
		}
		writer().clearLibraryForUpstreamSwitch();
		CompletableFuture.runAsync(() -> {
			try {
				crawl(ContentRefreshTriggerSource.SCHEDULED);
			}
			catch (RuntimeException exception) {
				LOG.warn("Content library was not recrawled after upstream switch: {}", exception.getMessage());
			}
		});
	}

	private List<ContentBook> randomBooks(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return contentBookCacheRepository.findRandom(LOCALE.name(), limit)
				.stream()
				.map(ContentBookCache::toContentBook)
				.toList();
	}

	private ContentLibraryCrawlState loadOrCreateState() {
		return contentLibraryCrawlStateRepository.findById(ContentLibraryCrawlState.SINGLETON_ID)
				.orElseGet(() -> contentLibraryCrawlStateRepository.save(ContentLibraryCrawlState.initial()));
	}

	private ContentCacheWriter writer() {
		return contentCacheWriterProvider.get();
	}
}
