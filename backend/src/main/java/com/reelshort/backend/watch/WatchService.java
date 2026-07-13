package com.reelshort.backend.watch;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.WatchEpisodeRewardClaimRepository;
import com.reelshort.backend.points.WatchRewardStatus;
import com.reelshort.backend.system.concurrency.UserActionLocks;

@Service
public class WatchService {

	private final WatchRecordRepository watchRecordRepository;
	private final WatchProgressTransaction watchProgressTransaction;
	private final UserActionLocks userActionLocks;
	private final WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository;

	public WatchService(WatchRecordRepository watchRecordRepository, WatchProgressTransaction watchProgressTransaction,
			UserActionLocks userActionLocks, WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository) {
		this.watchRecordRepository = watchRecordRepository;
		this.watchProgressTransaction = watchProgressTransaction;
		this.userActionLocks = userActionLocks;
		this.watchEpisodeRewardClaimRepository = watchEpisodeRewardClaimRepository;
	}

	public WatchRecordResponse reportProgress(UUID userId, WatchProgressRequest request) {
		return userActionLocks.withUserLock(userId, () -> watchProgressTransaction.reportProgress(userId, request));
	}

	@Transactional(readOnly = true)
	public List<WatchRecordResponse> history(UUID userId) {
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(record -> watchEpisodeRewardClaimRepository.findAwardedPoints(userId, record.bookId(), record.episodeNum())
						.map(points -> WatchRecordResponse.fromClaim(record, points))
						.orElseGet(() -> WatchRecordResponse.from(record)))
				.toList();
	}

	@Transactional(readOnly = true)
	public WatchEpisodeSnapshotResponse snapshot(UUID userId, String bookId, int episodeNum) {
		List<Integer> awardedStages = List.of();
		var awardedPointsResult = watchEpisodeRewardClaimRepository.findAwardedPoints(userId, bookId,
				episodeNum);
		boolean rewardClaimed = awardedPointsResult.isPresent();
		int awardedPoints = awardedPointsResult.orElse(0);
		WatchRewardStatus rewardStatus = rewardClaimed ? WatchRewardStatus.ALREADY_CLAIMED
				: WatchRewardStatus.NOT_COMPLETE;
		return watchRecordRepository.findByUserIdAndBookIdAndEpisodeNum(userId, bookId, episodeNum)
				.map(record -> new WatchEpisodeSnapshotResponse(
						record.bookId(),
						record.episodeNum(),
						record.positionSeconds(),
						record.durationSeconds(),
						record.progressPercent(), awardedStages, awardedPoints, rewardClaimed, rewardStatus))
				.orElseGet(() -> new WatchEpisodeSnapshotResponse(bookId, episodeNum, 0, 0, 0, awardedStages,
						awardedPoints, rewardClaimed, rewardStatus));
	}
}
