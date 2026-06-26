package com.reelshort.backend.points;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;

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

	@Transactional
	public PointAccount adjustByAdmin(UUID userId, int amount, String reason) {
		PointAccount account = accountEntity(userId);
		if (!account.canAdjust(amount)) {
			throw new AdminException(400, "insufficient point balance");
		}
		account.add(amount);
		pointTransactionRepository.save(PointTransaction.adminAdjustment(userId, amount, account.balance(), reason));
		return pointAccountRepository.save(account);
	}

	private boolean claimReward(UUID userId, String bookId, int episodeNum, int stage) {
		if (watchRewardClaimRepository.existsByUserIdAndBookIdAndEpisodeNumAndStage(userId, bookId, episodeNum, stage)) {
			return false;
		}
		watchRewardClaimRepository.save(WatchRewardClaim.create(userId, bookId, episodeNum, stage));
		return true;
	}
}
