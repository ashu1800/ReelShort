package com.reelshort.backend.watch;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.points.WatchRewardResult;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;

@Service
class WatchProgressTransaction {

	private final WatchRecordRepository watchRecordRepository;
	private final PointsService pointsService;
	private final ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;

	WatchProgressTransaction(WatchRecordRepository watchRecordRepository, PointsService pointsService,
			ContentEpisodeRuntimeCacheRepository runtimeCacheRepository) {
		this.watchRecordRepository = watchRecordRepository;
		this.pointsService = pointsService;
		this.runtimeCacheRepository = runtimeCacheRepository;
	}

	@Transactional
	public WatchRecordResponse reportProgress(UUID userId, WatchProgressRequest request) {
		WatchRecord record = watchRecordRepository
				.findByUserIdAndBookIdAndEpisodeNum(userId, request.bookId(), request.episodeNum())
				.orElseGet(() -> WatchRecord.create(userId, request.bookId(), request.bookTitle(),
						request.filteredTitle(), request.episodeNum(), request.chapterId()));
		int authoritativeDuration = runtimeCacheRepository
				.findByBookIdAndEpisodeNumAndChapterId(request.bookId(), request.episodeNum(), request.chapterId())
				.map(cache -> cache.durationSeconds())
				.orElse(0);
		int historyDuration = authoritativeDuration > 0 ? authoritativeDuration : request.durationSeconds();
		int effectiveProgressPercent = progressPercent(request.positionSeconds(), historyDuration);
		int clampedPosition = effectiveProgressPercent >= 100 && authoritativeDuration > 0
				? authoritativeDuration
				: Math.min(request.positionSeconds(), historyDuration);
		record.update(request.bookTitle(), request.filteredTitle(), request.chapterId(), clampedPosition,
				historyDuration, effectiveProgressPercent);
		WatchRecord savedRecord = watchRecordRepository.save(record);
		WatchRewardResult reward = pointsService.awardWatchProgress(userId, savedRecord.bookId(), savedRecord.episodeNum(),
				effectiveProgressPercent, authoritativeDuration);
		return WatchRecordResponse.from(savedRecord, reward);
	}

	private int progressPercent(int positionSeconds, int durationSeconds) {
		if (durationSeconds <= 0) {
			return 0;
		}
		return Math.min(100, (int) Math.floor(positionSeconds * 100.0 / durationSeconds));
	}
}
