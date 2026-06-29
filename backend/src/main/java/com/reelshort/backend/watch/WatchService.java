package com.reelshort.backend.watch;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointTransaction;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.system.concurrency.UserActionLocks;

@Service
public class WatchService {

	private final WatchRecordRepository watchRecordRepository;
	private final WatchProgressTransaction watchProgressTransaction;
	private final PointTransactionRepository pointTransactionRepository;
	private final UserActionLocks userActionLocks;

	public WatchService(WatchRecordRepository watchRecordRepository, WatchProgressTransaction watchProgressTransaction,
			PointTransactionRepository pointTransactionRepository, UserActionLocks userActionLocks) {
		this.watchRecordRepository = watchRecordRepository;
		this.watchProgressTransaction = watchProgressTransaction;
		this.pointTransactionRepository = pointTransactionRepository;
		this.userActionLocks = userActionLocks;
	}

	public WatchRecordResponse reportProgress(UUID userId, WatchProgressRequest request) {
		return userActionLocks.withUserLock(userId, () -> watchProgressTransaction.reportProgress(userId, request));
	}

	@Transactional(readOnly = true)
	public List<WatchRecordResponse> history(UUID userId) {
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(WatchRecordResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public WatchEpisodeSnapshotResponse snapshot(UUID userId, String bookId, int episodeNum) {
		List<Integer> awardedStages = pointTransactionRepository
				.findByUserIdAndBookIdAndEpisodeNumAndSourceOrderByStageAsc(userId, bookId, episodeNum,
						"WATCH_REWARD")
				.stream()
				.map(PointTransaction::stage)
				.filter(stage -> stage != null)
				.distinct()
				.toList();
		return watchRecordRepository.findByUserIdAndBookIdAndEpisodeNum(userId, bookId, episodeNum)
				.map(record -> new WatchEpisodeSnapshotResponse(
						record.bookId(),
						record.episodeNum(),
						record.positionSeconds(),
						record.durationSeconds(),
						record.progressPercent(),
						awardedStages))
				.orElseGet(() -> new WatchEpisodeSnapshotResponse(bookId, episodeNum, 0, 0, 0, awardedStages));
	}
}
