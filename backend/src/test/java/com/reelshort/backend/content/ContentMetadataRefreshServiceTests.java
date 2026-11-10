package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reelshort.backend.system.config.SystemConfigChangedEvent;
import com.reelshort.backend.system.config.SystemConfigRegistry;

@ExtendWith(MockitoExtension.class)
class ContentMetadataRefreshServiceTests {

	@Mock
	private ContentCacheService contentCacheService;

	@Test
	void refreshShelvesRefreshesValidShelvesAndSkipsInvalidValues() {
		ContentRefreshProperties properties = new ContentRefreshProperties();
		ContentMetadataRefreshService service = new ContentMetadataRefreshService(contentCacheService, properties);
		when(contentCacheService.refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.SCHEDULED)).thenReturn(List.of());

		int refreshed = service.refreshShelves(List.of("recommend", "unknown"));

		assertThat(refreshed).isEqualTo(1);
		verify(contentCacheService).refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.SCHEDULED);
	}

	@Test
	void upstreamSwitchClearsShelvesAndRefreshesConfiguredOnes() {
		ContentRefreshProperties properties = new ContentRefreshProperties();
		ContentMetadataRefreshService service = new ContentMetadataRefreshService(contentCacheService, properties);
		when(contentCacheService.refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.SCHEDULED)).thenReturn(List.of());

		service.onConfigChanged(new SystemConfigChangedEvent(SystemConfigRegistry.CONTENT_UPSTREAM, "TWISTYTV"));

		verify(contentCacheService).clearShelves();
		// 刷新在后台线程执行：等它落地再断言，否则这是个 flaky 用例。
		verify(contentCacheService, timeout(2000)).refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.SCHEDULED);
	}

	@Test
	void unrelatedConfigChangesDoNotTouchTheContentCache() {
		ContentRefreshProperties properties = new ContentRefreshProperties();
		ContentMetadataRefreshService service = new ContentMetadataRefreshService(contentCacheService, properties);

		service.onConfigChanged(new SystemConfigChangedEvent(SystemConfigRegistry.VIP_FREE_EPISODES, "7"));

		verifyNoInteractions(contentCacheService);
	}

	@Test
	void refreshConfiguredShelvesDoesNothingWhenDisabled() {
		ContentRefreshProperties properties = new ContentRefreshProperties();
		properties.setEnabled(false);
		ContentMetadataRefreshService service = new ContentMetadataRefreshService(contentCacheService, properties);

		service.refreshConfiguredShelves();

		verifyNoInteractions(contentCacheService);
	}
}
