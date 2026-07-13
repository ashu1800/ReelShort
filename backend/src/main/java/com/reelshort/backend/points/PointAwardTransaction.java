package com.reelshort.backend.points;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
	private final Clock clock;

	PointAwardTransaction(PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository,
			WatchRewardClaimRepository watchRewardClaimRepository,
			Clock clock) {
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.watchRewardClaimRepository = watchRewardClaimRepository;
		this.clock = clock;
	}

	@Transactional
	public WatchRewardResult awardWatchProgress(UUID userId, String bookId, int episodeNum, int progressPercent,
			List<Integer> rewardStages, int stagePoints, int dailyEarnedMaximum) {
		PointAccount account = accountEntity(userId);
		List<Integer> awardedStages = new ArrayList<>();
		int awardedPoints = 0;
		int remainingDailyPoints = remainingDailyPoints(userId, dailyEarnedMaximum);
		for (int stage : rewardStages) {
			if (progressPercent >= stage && claimReward(userId, bookId, episodeNum, stage)) {
				int pointsToAward = pointsToAward(stagePoints, remainingDailyPoints);
				if (pointsToAward > 0) {
					addPoints(account, pointsToAward);
					remainingDailyPoints -= pointsToAward;
					awardedPoints += pointsToAward;
					pointTransactionRepository.save(PointTransaction.watchReward(userId, pointsToAward,
							account.balance(), bookId, episodeNum, stage, OffsetDateTime.now(clock)));
				}
				awardedStages.add(stage);
			}
		}
		pointAccountRepository.save(account);
		return new WatchRewardResult(List.copyOf(awardedStages), awardedPoints);
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
			throw new AdminException(400, amount > 0 ? "point balance overflow" : "insufficient point balance");
		}
		addPoints(account, amount);
		pointTransactionRepository.save(PointTransaction.adminAdjustment(userId, amount, account.balance(), reason));
		return pointAccountRepository.save(account);
	}

	@Transactional
	public PointAccount creditRechargeOrder(UUID userId, String orderNo, int amount) {
		PointAccount account = accountEntity(userId);
		addPoints(account, amount);
		pointTransactionRepository.save(PointTransaction.rechargeOrder(userId, amount, account.balance(), orderNo));
		return pointAccountRepository.save(account);
	}

	private void addPoints(PointAccount account, int amount) {
		try {
			account.add(amount);
		}
		catch (IllegalStateException exception) {
			throw new AdminException(400, exception.getMessage());
		}
	}

	private int pointsToAward(int stagePoints, int remainingDailyPoints) {
		if (stagePoints <= 0) {
			return 0;
		}
		if (remainingDailyPoints < 0) {
			return stagePoints;
		}
		return Math.min(stagePoints, remainingDailyPoints);
	}

	private int remainingDailyPoints(UUID userId, int dailyEarnedMaximum) {
		if (dailyEarnedMaximum <= 0) {
			return -1;
		}
		LocalDate today = LocalDate.now(clock);
		OffsetDateTime startInclusive = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
		OffsetDateTime endExclusive = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
		long earnedToday = pointTransactionRepository.sumWatchRewardAmountByUserIdAndCreatedAtBetween(userId,
				startInclusive, endExclusive);
		return (int) Math.max(0, dailyEarnedMaximum - earnedToday);
	}

	private boolean claimReward(UUID userId, String bookId, int episodeNum, int stage) {
		if (watchRewardClaimRepository.existsByUserIdAndBookIdAndEpisodeNumAndStage(userId, bookId, episodeNum, stage)) {
			return false;
		}
		watchRewardClaimRepository.save(WatchRewardClaim.create(userId, bookId, episodeNum, stage));
		return true;
	}
}
