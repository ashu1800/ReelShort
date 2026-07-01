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

	@Scheduled(
			initialDelayString = "${reelshort.content.refresh.initial-delay:5m}",
			fixedDelayString = "${reelshort.content.refresh.interval:6h}")
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
