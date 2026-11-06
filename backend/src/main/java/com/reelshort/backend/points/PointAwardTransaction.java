package com.reelshort.backend.points;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;

@Service
class PointAwardTransaction {
	private static final Semaphore DAILY_RULE_CREATION_GATE = new Semaphore(1);
	private static final ZoneId EARNING_ZONE = ZoneId.of("Asia/Shanghai");

	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository;
	private final DailyEarningRuleRepository dailyEarningRuleRepository;
	private final DailyEarningQuotaRepository dailyEarningQuotaRepository;
	private final SystemConfigService systemConfigService;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	PointAwardTransaction(PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository,
			WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository,
			DailyEarningRuleRepository dailyEarningRuleRepository,
			DailyEarningQuotaRepository dailyEarningQuotaRepository,
			SystemConfigService systemConfigService,
			Clock clock) {
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.watchEpisodeRewardClaimRepository = watchEpisodeRewardClaimRepository;
		this.dailyEarningRuleRepository = dailyEarningRuleRepository;
		this.dailyEarningQuotaRepository = dailyEarningQuotaRepository;
		this.systemConfigService = systemConfigService;
		this.clock = clock;
	}

	private boolean isFairMode() {
		return systemConfigService.intValue(SystemConfigRegistry.POINTS_FAIR_MODE_ENABLED) == 1;
	}

	@Transactional
	public WatchRewardResult awardWatchProgress(UUID userId, String bookId, int episodeNum, int progressPercent,
			int authoritativeDurationSeconds, int secondsPerPoint, int dailyEarnedMaximum,
			int fluctuationMaximumPercent) {
		if (progressPercent < 100) {
			return WatchRewardResult.none();
		}
		if (authoritativeDurationSeconds <= 0) {
			return WatchRewardResult.durationUnavailable();
		}
		if (watchEpisodeRewardClaimRepository.existsByUserIdAndBookIdAndEpisodeNum(userId, bookId, episodeNum)) {
			return new WatchRewardResult(List.of(), 0, true, WatchRewardStatus.ALREADY_CLAIMED);
		}

		// 统一以"十分位"单位计算奖励：公平模式按秒精确（带小数），普通模式取整后 ×10。
		// balance 永远存真实整数，小数部分由 PointAccount.fractionalPart 累积。
		boolean fairMode = isFairMode();
		OffsetDateTime now = OffsetDateTime.now(clock);
		DailyEarningRule rule = dailyRule(currentEarningDate(), dailyEarnedMaximum, fluctuationMaximumPercent);
		int requestedTenths;
		int grantedTenths;
		if (rule.baseMaximum() <= 0) {
			requestedTenths = requestedTenths(authoritativeDurationSeconds, secondsPerPoint, fairMode);
			grantedTenths = requestedTenths;
		}
		else {
			DailyEarningQuota quota = dailyQuota(userId, rule, now);
			quota.initializeWatchedSecondsIfNeeded(secondsPerPoint);
			requestedTenths = progressiveRequestedTenths(quota, authoritativeDurationSeconds, secondsPerPoint, fairMode);
			grantedTenths = quota.allocateTenths(requestedTenths, now);
			quota.recordWatchedSeconds(authoritativeDurationSeconds, now);
			dailyEarningQuotaRepository.save(quota);
		}

		PointAccount account = accountEntity(userId);
		int awardedDisplayPoints;
		if (grantedTenths > 0) {
			int prevBalance = account.balance();
			account.addTenths(grantedTenths);
			int awardedInteger = account.balance() - prevBalance; // 本次实际进入 balance 整数部分
			if (awardedInteger > 0) {
				pointTransactionRepository.save(PointTransaction.watchReward(userId, awardedInteger, account.balance(),
						bookId, episodeNum, now));
			}
			awardedDisplayPoints = awardedInteger;
		}
		else {
			awardedDisplayPoints = 0;
		}
		watchEpisodeRewardClaimRepository.save(WatchEpisodeRewardClaim.create(userId, bookId, episodeNum,
				authoritativeDurationSeconds, requestedTenths, awardedDisplayPoints, now));
		pointAccountRepository.save(account);
		WatchRewardStatus status = grantedTenths == 0
				? WatchRewardStatus.DAILY_LIMIT_REACHED
				: grantedTenths < requestedTenths ? WatchRewardStatus.AWARDED_PARTIAL : WatchRewardStatus.AWARDED;
		return new WatchRewardResult(List.of(), awardedDisplayPoints, true, status);
	}

	@Transactional
	public DailyEarningQuotaResponse dailyEarningQuota(UUID userId, int dailyEarnedMaximum,
			int fluctuationMaximumPercent) {
		LocalDate today = currentEarningDate();
		DailyEarningRule rule = dailyRule(today, dailyEarnedMaximum, fluctuationMaximumPercent);
		if (rule.baseMaximum() <= 0) {
			return DailyEarningQuotaResponse.unlimited(today);
		}
		DailyEarningQuota quota = dailyQuota(userId, rule, OffsetDateTime.now(clock));
		return DailyEarningQuotaResponse.from(quota);
	}

	@Transactional
	public int estimatedWatchRewardPoints(UUID userId, int authoritativeDurationSeconds, int secondsPerPoint,
			int dailyEarnedMaximum, int fluctuationMaximumPercent) {
		boolean fairMode = isFairMode();
		DailyEarningRule rule = dailyRule(currentEarningDate(), dailyEarnedMaximum, fluctuationMaximumPercent);
		int grantedTenths;
		if (rule.baseMaximum() <= 0) {
			grantedTenths = requestedTenths(authoritativeDurationSeconds, secondsPerPoint, fairMode);
		}
		else {
			DailyEarningQuota quota = dailyQuota(userId, rule, OffsetDateTime.now(clock));
			quota.initializeWatchedSecondsIfNeeded(secondsPerPoint);
			grantedTenths = quota.previewAllocationTenths(
					progressiveRequestedTenths(quota, authoritativeDurationSeconds, secondsPerPoint, fairMode));
		}
		PointAccount account = accountEntity(userId);
		return (account.fractionalPart() + grantedTenths) / WatchRewardCalculation.FAIR_MODE_SCALE;
	}

	@Transactional
	public void snapshotDailyEarningRule(int dailyEarnedMaximum, int fluctuationMaximumPercent) {
		dailyRule(currentEarningDate(), dailyEarnedMaximum, fluctuationMaximumPercent);
	}

	private int requestedTenths(int durationSeconds, int secondsPerPoint, boolean fairMode) {
		return fairMode
				? WatchRewardCalculation.pointsForDurationTenths(durationSeconds, secondsPerPoint)
				: WatchRewardCalculation.pointsForDuration(durationSeconds, secondsPerPoint)
						* WatchRewardCalculation.FAIR_MODE_SCALE;
	}

	private int progressiveRequestedTenths(DailyEarningQuota quota, int durationSeconds, int secondsPerPoint,
			boolean fairMode) {
		int requested = WatchRewardCalculation.progressivePointsTenths(quota.effectiveMaximum(),
				quota.watchedSeconds(), durationSeconds, secondsPerPoint);
		if (!fairMode) {
			requested = Math.max(10, (requested / WatchRewardCalculation.FAIR_MODE_SCALE)
					* WatchRewardCalculation.FAIR_MODE_SCALE);
		}
		return requested;
	}

	private LocalDate currentEarningDate() {
		return LocalDate.now(clock.withZone(EARNING_ZONE));
	}

	private DailyEarningRule dailyRule(LocalDate date, int baseMaximum, int fluctuationMaximumPercent) {
		return dailyEarningRuleRepository.findById(date).orElseGet(() -> {
			DAILY_RULE_CREATION_GATE.acquireUninterruptibly();
			boolean releaseAfterMethod = true;
			try {
				DailyEarningRule existing = dailyEarningRuleRepository.findById(date).orElse(null);
				if (existing != null) {
					return existing;
				}
				DailyEarningRule created = dailyEarningRuleRepository.saveAndFlush(DailyEarningRule.create(date,
						baseMaximum, fluctuationMaximumPercent, OffsetDateTime.now(clock)));
				if (TransactionSynchronizationManager.isSynchronizationActive()) {
					releaseAfterMethod = false;
					TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
						@Override
						public void afterCompletion(int status) {
							DAILY_RULE_CREATION_GATE.release();
						}
					});
				}
				return created;
			}
			finally {
				if (releaseAfterMethod) {
					DAILY_RULE_CREATION_GATE.release();
				}
			}
		});
	}

	private DailyEarningQuota dailyQuota(UUID userId, DailyEarningRule rule, OffsetDateTime now) {
		return dailyEarningQuotaRepository.findForUpdate(userId, rule.earningDate())
				.orElseGet(() -> dailyEarningQuotaRepository.save(DailyEarningQuota.create(userId, rule,
						secureRandom.nextInt(rule.fluctuationMaximumPercent() + 1), now)));
	}

	@Transactional
	public PointAccount accountEntity(UUID userId) {
		return pointAccountRepository.findByUserId(userId)
				.orElseGet(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)));
	}

	@Transactional
	public PointAccount adjustByAdmin(UUID userId, int amount, String reason, String idempotencyKey) {
		if (pointTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
			return accountEntity(userId);
		}
		PointAccount account = pointAccountRepository.findByUserIdForUpdate(userId)
				.orElseGet(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)));
		if (pointTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
			return account;
		}
		if (!account.canAdjust(amount)) {
			throw new AdminException(400, amount > 0 ? "point balance overflow" : "insufficient point balance");
		}
		addPoints(account, amount);
		pointTransactionRepository.saveAndFlush(PointTransaction.adminAdjustment(userId, amount, account.balance(), reason,
				idempotencyKey, account.frozenPoints()));
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

}
