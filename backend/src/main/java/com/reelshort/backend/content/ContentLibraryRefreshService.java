package com.reelshort.backend.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 视频库定时抓取：默认每 30 分钟从内容源目录拉一页新书入库。
 *
 * <p>抓取失败只记日志（错误状态已由 {@link ContentLibraryService#crawl} 写进
 * content_library_crawl_state.last_error），不中断调度；下一轮从同一页重试。
 */
@Service
public class ContentLibraryRefreshService {

	private static final Logger log = LoggerFactory.getLogger(ContentLibraryRefreshService.class);

	private final ContentLibraryService contentLibraryService;
	private final ContentLibraryProperties properties;

	public ContentLibraryRefreshService(ContentLibraryService contentLibraryService,
			ContentLibraryProperties properties) {
		this.contentLibraryService = contentLibraryService;
		this.properties = properties;
	}

	@Scheduled(initialDelayString = "${reelshort.content.library.initial-delay:15s}",
			fixedDelayString = "${reelshort.content.library.interval:30m}")
	public void refreshLibrary() {
		if (!properties.isEnabled()) {
			return;
		}
		try {
			ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.SCHEDULED);
			log.info("Content library crawled {} pages, added {} books (total {}, next page {})",
					result.fetchedPages(), result.added(), result.total(), result.nextPage());
		}
		catch (RuntimeException exception) {
			log.warn("Content library crawl failed: {}", exception.getMessage());
		}
	}
}
