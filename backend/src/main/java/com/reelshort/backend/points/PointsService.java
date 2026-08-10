package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.wallet.UserWallet;
import com.reelshort.backend.wallet.UserWalletRepository;

@Service
public class PointsService {

	private final PointTransactionRepository pointTransactionRepository;
	private final PointAccountRepository pointAccountRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserWalletRepository userWalletRepository;
	private final UserActionLocks userActionLocks;
	private final PointAwardTransaction pointAwardTransaction;
	private final SystemConfigService systemConfigService;
	private final Clock clock;

	public PointsService(PointTransactionRepository pointTransactionRepository,
			PointAccountRepository pointAccountRepository, UserAccountRepository userAccountRepository,
			UserWalletRepository userWalletRepository, UserActionLocks userActionLocks,
			PointAwardTransaction pointAwardTransaction, SystemConfigService systemConfigService, Clock clock) {
		this.pointTransactionRepository = pointTransactionRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.userAccountRepository = userAccountRepository;
		this.userWalletRepository = userWalletRepository;
		this.userActionLocks = userActionLocks;
		this.pointAwardTransaction = pointAwardTransaction;
		this.systemConfigService = systemConfigService;
		this.clock = clock;
	}

	public PointAccountResponse account(UUID userId) {
		return userActionLocks.withUserLock(userId, () -> {
			PointAccount account = pointAwardTransaction.accountEntity(userId);
			UserAccount user = userAccountRepository.findById(userId).orElse(null);
			UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
			boolean vip = user != null && user.isVip();
			String vipUntil = (user != null && user.vipUntil() != null)
					? java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
							.withZone(java.time.ZoneId.systemDefault()).format(user.vipUntil())
					: null;
			String walletAddress = wallet != null ? wallet.walletAddress() : null;
			ZoneId zone = ZoneId.of("Asia/Shanghai");
			LocalDate today = LocalDate.now(clock.withZone(zone));
			ZonedDateTime start = today.atStartOfDay(zone);
			ZonedDateTime end = today.plusDays(1).atStartOfDay(zone);
			long todayCreditedPoints = pointTransactionRepository.sumPositiveAmountByUserIdAndCreatedAtBetween(
					userId, start.toOffsetDateTime(), end.toOffsetDateTime());
			// balance 永远是真实整数积分（小数部分由 PointAccount.fractionalPart 承载），直接透传。
			return new PointAccountResponse(account.balance(), account.frozenPoints(), account.availablePoints(),
					vip, vipUntil, walletAddress, Math.toIntExact(todayCreditedPoints));
		});
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
		return rewardTenths(authoritativeDurationSeconds) / WatchRewardCalculation.FAIR_MODE_SCALE;
	}

	public int estimatedWatchRewardPoints(UUID userId, int authoritativeDurationSeconds) {
		int requestedTenths = rewardTenths(authoritativeDurationSeconds);
		int dailyEarnedMaximum = systemConfigService.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM);
		int fluctuationMaximum = systemConfigService
				.intValue(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT);
		return userActionLocks.withUserLock(userId,
				() -> pointAwardTransaction.estimatedWatchRewardPoints(userId, requestedTenths, dailyEarnedMaximum,
						fluctuationMaximum));
	}

	private int rewardTenths(int authoritativeDurationSeconds) {
		int secondsPerPoint = systemConfigService.intValue(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT);
		boolean fairMode = systemConfigService.intValue(SystemConfigRegistry.POINTS_FAIR_MODE_ENABLED) == 1;
		return fairMode
				? WatchRewardCalculation.pointsForDurationTenths(authoritativeDurationSeconds, secondsPerPoint)
				: WatchRewardCalculation.pointsForDuration(authoritativeDurationSeconds, secondsPerPoint)
						* WatchRewardCalculation.FAIR_MODE_SCALE;
	}

	public PointAccountResponse adjustByAdmin(UUID userId, int amount, String reason, String idempotencyKey) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.adjustByAdmin(userId, amount, reason, idempotencyKey)));
	}

	/** Internal maintenance callers that do not expose a retryable HTTP request key. */
	public PointAccountResponse adjustByAdmin(UUID userId, int amount, String reason) {
		return adjustByAdmin(userId, amount, reason, "internal-adjust:" + UUID.randomUUID());
	}

	public PointAccountResponse creditRechargeOrder(UUID userId, String orderNo, int amount) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.creditRechargeOrder(userId, orderNo, amount)));
	}
}
