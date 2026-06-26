package com.reelshort.backend.watch;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.points.WatchRewardResult;

@Service
class WatchProgressTransaction {

	private final WatchRecordRepository watchRecordRepository;
	private final PointsService pointsService;

	WatchProgressTransaction(WatchRecordRepository watchRecordRepository, PointsService pointsService) {
		this.watchRecordRepository = watchRecordRepository;
		this.pointsService = pointsService;
	}

	@Transactional
	public WatchRecordResponse reportProgress(UUID userId, WatchProgressRequest request) {
		WatchRecord record = watchRecordRepository
				.findByUserIdAndBookIdAndEpisodeNum(userId, request.bookId(), request.episodeNum())
				.orElseGet(() -> WatchRecord.create(userId, request.bookId(), request.bookTitle(),
						request.filteredTitle(), request.episodeNum(), request.chapterId()));
		int clampedPosition = Math.min(request.positionSeconds(), request.durationSeconds());
		record.update(request.bookTitle(), request.filteredTitle(), request.chapterId(), clampedPosition,
				request.durationSeconds(), progressPercent(clampedPosition, request.durationSeconds()));
		WatchRecord savedRecord = watchRecordRepository.save(record);
		WatchRewardResult reward = pointsService.awardWatchProgress(userId, savedRecord.bookId(), savedRecord.episodeNum(),
				savedRecord.progressPercent());
		return WatchRecordResponse.from(savedRecord, reward.awardedStages(), reward.awardedPoints());
	}

	private int progressPercent(int positionSeconds, int durationSeconds) {
		return Math.min(100, (int) Math.floor(positionSeconds * 100.0 / durationSeconds));
	}
}
