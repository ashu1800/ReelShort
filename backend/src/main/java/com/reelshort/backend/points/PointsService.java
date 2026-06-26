package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.system.concurrency.UserActionLocks;

@Service
public class PointsService {

	private static final List<Integer> REWARD_STAGES = List.of(25, 50, 75, 100);
	private static final int WATCH_STAGE_POINTS = 1;

	private final PointTransactionRepository pointTransactionRepository;
	private final UserActionLocks userActionLocks;
	private final PointAwardTransaction pointAwardTransaction;

	public PointsService(PointTransactionRepository pointTransactionRepository, UserActionLocks userActionLocks,
			PointAwardTransaction pointAwardTransaction) {
		this.pointTransactionRepository = pointTransactionRepository;
		this.userActionLocks = userActionLocks;
		this.pointAwardTransaction = pointAwardTransaction;
	}

	public PointAccountResponse account(UUID userId) {
		return userActionLocks.withUserLock(userId, () -> PointAccountResponse.from(pointAwardTransaction.accountEntity(userId)));
	}

	@Transactional(readOnly = true)
	public List<PointTransactionResponse> records(UUID userId) {
		return pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(PointTransactionResponse::from)
				.toList();
	}

	public WatchRewardResult awardWatchProgress(UUID userId, String bookId, int episodeNum, int progressPercent) {
		return userActionLocks.withUserLock(userId, () -> pointAwardTransaction.awardWatchProgress(userId, bookId,
				episodeNum, progressPercent, REWARD_STAGES, WATCH_STAGE_POINTS));
	}

	public PointAccountResponse adjustByAdmin(UUID userId, int amount, String reason) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.adjustByAdmin(userId, amount, reason)));
	}
}
