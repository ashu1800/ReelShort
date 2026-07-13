package com.reelshort.backend.points;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.reelshort.backend.admin.AdminException;

@Service
class PointAwardTransaction {
	private static final Semaphore DAILY_RULE_CREATION_GATE = new Semaphore(1);

	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository;
	private final DailyEarningRuleRepository dailyEarningRuleRepository;
	private final DailyEarningQuotaRepository dailyEarningQuotaRepository;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	PointAwardTransaction(PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository,
			WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository,
			DailyEarningRuleRepository dailyEarningRuleRepository,
			DailyEarningQuotaRepository dailyEarningQuotaRepository,
			Clock clock) {
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.watchEpisodeRewardClaimRepository = watchEpisodeRewardClaimRepository;
		this.dailyEarningRuleRepository = dailyEarningRuleRepository;
		this.dailyEarningQuotaRepository = dailyEarningQuotaRepository;
		this.clock = clock;
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

		int calculatedPoints = WatchRewardCalculation.pointsForDuration(authoritativeDurationSeconds, secondsPerPoint);
		OffsetDateTime now = OffsetDateTime.now(clock);
		int awardedPoints = allocateDailyPoints(userId, calculatedPoints, dailyEarnedMaximum,
				fluctuationMaximumPercent, now);
		PointAccount account = accountEntity(userId);
		if (awardedPoints > 0) {
			addPoints(account, awardedPoints);
			pointTransactionRepository.save(PointTransaction.watchReward(userId, awardedPoints, account.balance(), bookId,
					episodeNum, now));
		}
		watchEpisodeRewardClaimRepository.save(WatchEpisodeRewardClaim.create(userId, bookId, episodeNum,
				authoritativeDurationSeconds, calculatedPoints, awardedPoints, now));
		pointAccountRepository.save(account);
		WatchRewardStatus status = awardedPoints == 0
				? WatchRewardStatus.DAILY_LIMIT_REACHED
				: awardedPoints < calculatedPoints ? WatchRewardStatus.AWARDED_PARTIAL : WatchRewardStatus.AWARDED;
		return new WatchRewardResult(List.of(), awardedPoints, true, status);
	}

	@Transactional
	public DailyEarningQuotaResponse dailyEarningQuota(UUID userId, int dailyEarnedMaximum,
			int fluctuationMaximumPercent) {
		LocalDate today = LocalDate.now(clock);
		DailyEarningRule rule = dailyRule(today, dailyEarnedMaximum, fluctuationMaximumPercent);
		if (rule.baseMaximum() <= 0) {
			return DailyEarningQuotaResponse.unlimited(today);
		}
		DailyEarningQuota quota = dailyQuota(userId, rule, OffsetDateTime.now(clock));
		return DailyEarningQuotaResponse.from(quota);
	}

	@Transactional
	public void snapshotDailyEarningRule(int dailyEarnedMaximum, int fluctuationMaximumPercent) {
		dailyRule(LocalDate.now(clock), dailyEarnedMaximum, fluctuationMaximumPercent);
	}

	private int allocateDailyPoints(UUID userId, int requestedPoints, int dailyEarnedMaximum,
			int fluctuationMaximumPercent, OffsetDateTime now) {
		DailyEarningRule rule = dailyRule(LocalDate.now(clock), dailyEarnedMaximum, fluctuationMaximumPercent);
		if (rule.baseMaximum() <= 0) {
			return requestedPoints;
		}
		DailyEarningQuota quota = dailyQuota(userId, rule, now);
		int granted = quota.allocate(requestedPoints, now);
		dailyEarningQuotaRepository.save(quota);
		return granted;
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

}
