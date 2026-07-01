package com.reelshort.backend.content;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentRefreshRunRecorder {

	private final ContentRefreshRunRepository contentRefreshRunRepository;

	public ContentRefreshRunRecorder(ContentRefreshRunRepository contentRefreshRunRepository) {
		this.contentRefreshRunRepository = contentRefreshRunRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSuccess(ContentRefreshTriggerSource triggerSource, ContentShelfType shelfType,
			ContentLocale locale, OffsetDateTime startedAt, OffsetDateTime finishedAt, int itemCount) {
		contentRefreshRunRepository.save(ContentRefreshRun.success(
				triggerSource,
				shelfType,
				locale,
				startedAt,
				finishedAt,
				itemCount));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(ContentRefreshTriggerSource triggerSource, ContentShelfType shelfType,
			ContentLocale locale, OffsetDateTime startedAt, OffsetDateTime finishedAt, String errorMessage) {
		contentRefreshRunRepository.save(ContentRefreshRun.failure(
				triggerSource,
				shelfType,
				locale,
				startedAt,
				finishedAt,
				errorMessage));
	}
}
