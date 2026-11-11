package com.reelshort.backend.content;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ContentMetadataRefreshService {

	private static final Logger log = LoggerFactory.getLogger(ContentMetadataRefreshService.class);

	private final ContentCacheService contentCacheService;
	private final ContentRefreshProperties properties;

	public ContentMetadataRefreshService(ContentCacheService contentCacheService, ContentRefreshProperties properties) {
		this.contentCacheService = contentCacheService;
		this.properties = properties;
	}

	/**
	 * 简介补抓：TwistyTV 的 `/browse` 卡片没有简介，视频库（content_book_cache）里那些书
	 * 只能按本翻详情页补。每次跑一小批并留间隔，跑完一轮就停（队列为空时是空查询，成本可忽略）。
	 */
	@Scheduled(
			initialDelayString = "${reelshort.content.description-backfill.initial-delay:2m}",
			fixedDelayString = "${reelshort.content.description-backfill.interval:10m}")
	public void backfillMissingDescriptions() {
		if (!properties.isDescriptionBackfillEnabled()) {
			return;
		}
		int limit = properties.getDescriptionBackfillLimit();
		int filled = contentCacheService.backfillMissingDescriptions(
				ContentLocale.ENGLISH, limit, properties.getDescriptionBackfillPause());
		if (filled > 0) {
			log.info("Backfilled {} book descriptions (limit {})", filled, limit);
		}
	}

	/**
	 * 旧货架刷新入口。首页与后台已改用视频库（{@link ContentLibraryRefreshService}），
	 * 这里不再挂 {@code @Scheduled}；上游切换只由 {@link ContentLibraryService#onConfigChanged}
	 * 负责清库 + 重置游标 + 重抓，旧货架不再被清空或重新填充。
	 * 方法保留给既有测试与人工调用。
	 */
	public void refreshConfiguredShelves() {
		if (!properties.isEnabled()) {
			return;
		}
		refreshShelves(properties.getShelves());
	}

	int refreshShelves(List<String> shelfValues) {
		int refreshed = 0;
		for (String shelfValue : shelfValues) {
			for (String localeValue : properties.getLocales()) {
				try {
					ContentShelfType shelfType = ContentShelfType.fromApiValue(shelfValue);
					ContentLocale locale = ContentLocale.fromApiValue(localeValue);
					contentCacheService.refreshShelf(shelfType, locale, ContentRefreshTriggerSource.SCHEDULED);
					refreshed++;
				}
				catch (IllegalArgumentException | ContentProviderException exception) {
					log.warn("Content metadata refresh skipped shelf {} locale {}: {}", shelfValue, localeValue,
							exception.getMessage());
				}
			}
		}
		return refreshed;
	}
}
