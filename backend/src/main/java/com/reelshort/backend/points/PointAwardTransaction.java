package com.reelshort.backend.points;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PointAwardTransaction {

	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final WatchRewardClaimRepository watchRewardClaimRepository;

	PointAwardTransaction(PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository,
			WatchRewardClaimRepository watchRewardClaimRepository) {
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.watchRewardClaimRepository = watchRewardClaimRepository;
	}

	@Transactional
	public WatchRewardResult awardWatchProgress(UUID userId, String bookId, int episodeNum, int progressPercent,
			List<Integer> rewardStages, int stagePoints) {
		PointAccount account = accountEntity(userId);
		List<Integer> awardedStages = new ArrayList<>();
		for (int stage : rewardStages) {
			if (progressPercent >= stage && claimReward(userId, bookId, episodeNum, stage)) {
				account.add(stagePoints);
				pointTransactionRepository.save(PointTransaction.watchReward(userId, stagePoints,
						account.balance(), bookId, episodeNum, stage));
				awardedStages.add(stage);
			}
		}
		pointAccountRepository.save(account);
		return new WatchRewardResult(List.copyOf(awardedStages), awardedStages.size() * stagePoints);
	}

	@Transactional
	public PointAccount accountEntity(UUID userId) {
		return pointAccountRepository.findByUserId(userId)
				.orElseGet(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)));
	}

	private boolean claimReward(UUID userId, String bookId, int episodeNum, int stage) {
		if (watchRewardClaimRepository.existsByUserIdAndBookIdAndEpisodeNumAndStage(userId, bookId, episodeNum, stage)) {
			return false;
		}
		watchRewardClaimRepository.save(WatchRewardClaim.create(userId, bookId, episodeNum, stage));
		return true;
	}
}
