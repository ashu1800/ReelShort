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
