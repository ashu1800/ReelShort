package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccountRepository;

@Service
public class PointsService {

	private final PointTransactionRepository pointTransactionRepository;
	private final PointAccountRepository pointAccountRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserActionLocks userActionLocks;
	private final PointAwardTransaction pointAwardTransaction;
	private final SystemConfigService systemConfigService;

	public PointsService(PointTransactionRepository pointTransactionRepository,
			PointAccountRepository pointAccountRepository, UserAccountRepository userAccountRepository,
			UserActionLocks userActionLocks, PointAwardTransaction pointAwardTransaction,
			SystemConfigService systemConfigService) {
		this.pointTransactionRepository = pointTransactionRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.userAccountRepository = userAccountRepository;
		this.userActionLocks = userActionLocks;
		this.pointAwardTransaction = pointAwardTransaction;
		this.systemConfigService = systemConfigService;
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
		return awardWatchProgress(userId, bookId, episodeNum, progressPercent, 0);
	}

	public WatchRewardResult awardWatchProgress(UUID userId, String bookId, int episodeNum, int progressPercent,
			int authoritativeDurationSeconds) {
		int secondsPerPoint = systemConfigService.intValue(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT);
		int dailyEarnedMaximum = systemConfigService.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM);
		int fluctuationMaximum = systemConfigService
				.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT);
		return userActionLocks.withUserLock(userId, () -> pointAwardTransaction.awardWatchProgress(userId, bookId,
				episodeNum, progressPercent, authoritativeDurationSeconds, secondsPerPoint, dailyEarnedMaximum,
				fluctuationMaximum));
	}

	public DailyEarningQuotaResponse dailyEarningQuota(UUID userId) {
		int dailyEarnedMaximum = systemConfigService.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM);
		int fluctuationMaximum = systemConfigService
				.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT);
		return userActionLocks.withUserLock(userId,
				() -> pointAwardTransaction.dailyEarningQuota(userId, dailyEarnedMaximum, fluctuationMaximum));
	}

	public void snapshotDailyEarningRule() {
		pointAwardTransaction.snapshotDailyEarningRule(
				systemConfigService.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM),
				systemConfigService.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT));
	}

	public int estimatedWatchRewardPoints(int authoritativeDurationSeconds) {
		return WatchRewardCalculation.pointsForDuration(authoritativeDurationSeconds,
				systemConfigService.intValue(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT));
	}

	public PointAccountResponse adjustByAdmin(UUID userId, int amount, String reason) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.adjustByAdmin(userId, amount, reason)));
	}

	public PointAccountResponse creditRechargeOrder(UUID userId, String orderNo, int amount) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.creditRechargeOrder(userId, orderNo, amount)));
	}
}
