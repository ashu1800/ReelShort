package com.reelshort.backend.content;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.reelshort.backend.system.config.SystemConfigChangedEvent;
import com.reelshort.backend.system.config.SystemConfigRegistry;

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
	 * 简介补抓：TwistyTV 的 `/browse` 卡片没有简介，货架里那些书只能按本翻详情页补。
	 * 每次跑一小批并留间隔，跑完一轮就停（队列为空时是空查询，成本可忽略）。
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

	@Scheduled(
			initialDelayString = "${reelshort.content.refresh.initial-delay:5m}",
			fixedDelayString = "${reelshort.content.refresh.interval:6h}")
	public void refreshConfiguredShelves() {
		if (!properties.isEnabled()) {
			return;
		}
		refreshShelves(properties.getShelves());
	}

	/**
	 * 上游切换后立刻作废旧货架并重抓：不清缓存的话，用户首页会继续显示旧上游的片单，
	 * 直到 6 小时后的定时刷新——"后台切了但 App 没变"是最容易误判成故障的那种状态。
	 *
	 * 刷新在本线程之外跑：抓一轮上游要几秒到几十秒，不能让管理员的保存请求等着。
	 */
	@EventListener
	public void onConfigChanged(SystemConfigChangedEvent event) {
		if (!SystemConfigRegistry.CONTENT_UPSTREAM.equals(event.key())) {
			return;
		}
		contentCacheService.clearShelves();
		List<String> shelves = List.copyOf(properties.getShelves());
		CompletableFuture.runAsync(() -> refreshShelves(shelves))
				.exceptionally(exception -> {
					log.warn("Content shelves were not refreshed after upstream switch: {}", exception.getMessage());
					return null;
				});
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
