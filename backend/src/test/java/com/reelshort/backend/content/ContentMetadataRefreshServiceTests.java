package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
		when(contentCacheService.refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE,
				ContentRefreshTriggerSource.SCHEDULED))
				.thenReturn(List.of());

		int refreshed = service.refreshShelves(List.of("recommend", "unknown"));

		assertThat(refreshed).isEqualTo(2);
		verify(contentCacheService).refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH,
				ContentRefreshTriggerSource.SCHEDULED);
		verify(contentCacheService).refreshShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE,
				ContentRefreshTriggerSource.SCHEDULED);
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
