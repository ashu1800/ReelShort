package com.reelshort.backend.watch;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.points.WatchRewardResult;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;
import com.reelshort.backend.content.VipGateService;

@Service
class WatchProgressTransaction {

	private final WatchRecordRepository watchRecordRepository;
	private final PointsService pointsService;
	private final ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;
	private final VipGateService vipGateService;

	WatchProgressTransaction(WatchRecordRepository watchRecordRepository, PointsService pointsService,
			ContentEpisodeRuntimeCacheRepository runtimeCacheRepository, VipGateService vipGateService) {
		this.watchRecordRepository = watchRecordRepository;
		this.pointsService = pointsService;
		this.runtimeCacheRepository = runtimeCacheRepository;
		this.vipGateService = vipGateService;
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
		int reportedProgressPercent = progressPercent(request.positionSeconds(), historyDuration);
		int reportedPositionSeconds = reportedProgressPercent >= 100 && authoritativeDuration > 0
				? authoritativeDuration
				: Math.min(request.positionSeconds(), historyDuration);
		// P2-5：进度只允许前进。离线补报、多设备切换或乱序上报不得把已看完的集数回退，
		// 否则会出现“进度 8% + rewardClaimed=true”的永久不一致（奖励不可重领但显示未完成）。
		int effectiveProgressPercent = Math.max(record.progressPercent(), reportedProgressPercent);
		int effectivePositionSeconds = effectiveProgressPercent > reportedProgressPercent
				? record.positionSeconds()
				: reportedPositionSeconds;
		// 标题、章节等元数据仍按最新上报更新，只有进度相关字段受单调保护约束。
		record.update(request.bookTitle(), request.filteredTitle(), request.chapterId(), effectivePositionSeconds,
				historyDuration, effectiveProgressPercent);
		WatchRecord savedRecord = watchRecordRepository.save(record);
		WatchRewardResult reward;
		if (vipGateService.isVip(userId)) {
			reward = pointsService.awardWatchProgress(userId, savedRecord.bookId(), savedRecord.episodeNum(),
					effectiveProgressPercent, authoritativeDuration);
		}
		else {
			reward = WatchRewardResult.noVip();
		}
		return WatchRecordResponse.from(savedRecord, reward);
	}

	private int progressPercent(int positionSeconds, int durationSeconds) {
		if (durationSeconds <= 0) {
			return 0;
		}
		return Math.min(100, (int) Math.floor(positionSeconds * 100.0 / durationSeconds));
	}
}
