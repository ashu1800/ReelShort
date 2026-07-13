package com.reelshort.backend.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.content.ContentEpisodeRuntimeCache;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.watch.WatchProgressRequest;
import com.reelshort.backend.watch.WatchRecordResponse;
import com.reelshort.backend.watch.WatchService;

@SpringBootTest
class PointsServiceTests {

	@Autowired
	private PointsService pointsService;

	@Autowired
	private WatchService watchService;

	@Autowired
	private PointAccountRepository pointAccountRepository;

	@Autowired
	private PointTransferRepository pointTransferRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private DailyEarningQuotaRepository dailyEarningQuotaRepository;

	@Autowired
	private DailyEarningRuleRepository dailyEarningRuleRepository;

	@Autowired
	private ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;

	@Autowired
	private MutableClock mutableClock;

	@AfterEach
	void resetConfigs() {
		mutableClock.setInstant(Instant.parse("2026-07-13T10:00:00Z"));
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "60");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");
		dailyEarningQuotaRepository.deleteAll();
		dailyEarningRuleRepository.deleteAll();
	}

	@Test
	void completedEpisodeAwardsPointsFromAuthoritativeDurationOnlyOnce() {
		UUID userId = UUID.randomUUID();

		WatchRewardResult incomplete = pointsService.awardWatchProgress(userId, "duration-book", 1, 99, 120);
		WatchRewardResult completed = pointsService.awardWatchProgress(userId, "duration-book", 1, 100, 120);
		WatchRewardResult duplicate = pointsService.awardWatchProgress(userId, "duration-book", 1, 100, 120);

		assertThat(incomplete.awardedPoints()).isZero();
		assertThat(incomplete.rewardStatus()).isEqualTo(WatchRewardStatus.NOT_COMPLETE);
		assertThat(completed.awardedPoints()).isEqualTo(2);
		assertThat(completed.rewardClaimed()).isTrue();
		assertThat(completed.rewardStatus()).isEqualTo(WatchRewardStatus.AWARDED);
		assertThat(duplicate.awardedPoints()).isZero();
		assertThat(duplicate.rewardStatus()).isEqualTo(WatchRewardStatus.ALREADY_CLAIMED);
		assertThat(pointsService.account(userId).balance()).isEqualTo(2);
		assertThat(pointsService.records(userId)).singleElement()
				.satisfies(record -> assertThat(record.stage()).isNull());
	}

	@Test
	void dailyFluctuationCreatesStableEffectiveLimitForUserAndDay() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "1");
		UUID userId = UUID.randomUUID();

		WatchRewardResult reward = pointsService.awardWatchProgress(userId, "daily-random-book", 1, 100, 1000);
		DailyEarningQuotaResponse quota = pointsService.dailyEarningQuota(userId);

		assertThat(quota.fluctuationPercent()).isBetween(0, 35);
		assertThat(quota.effectiveMaximum()).isEqualTo(
				WatchRewardCalculation.effectiveDailyLimit(1000, quota.fluctuationPercent()));
		assertThat(reward.awardedPoints()).isEqualTo(quota.effectiveMaximum());
		assertThat(quota.earnedPoints()).isEqualTo(quota.effectiveMaximum());
		assertThat(pointsService.dailyEarningQuota(userId)).isEqualTo(quota);
	}

	@Test
	void dailyRuleConfigurationChangesApplyOnNextServerDay() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");
		UUID firstUser = UUID.randomUUID();
		UUID secondUser = UUID.randomUUID();

		assertThat(pointsService.awardWatchProgress(firstUser, "rule-book-1", 1, 100, 1000).awardedPoints())
				.isEqualTo(1000);
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "500");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "35");

		assertThat(pointsService.awardWatchProgress(secondUser, "rule-book-2", 1, 100, 1000).awardedPoints())
				.isEqualTo(1000);
		mutableClock.setInstant(Instant.parse("2026-07-14T00:01:00Z"));
		assertThat(pointsService.awardWatchProgress(secondUser, "rule-book-3", 1, 100, 1000).awardedPoints())
				.isBetween(325, 500);
	}

	@Test
	void accountIsCreatedWithZeroBalance() {
		PointAccountResponse account = pointsService.account(UUID.randomUUID());

		assertThat(account.balance()).isZero();
	}

	@Test
	void watchRewardCreatesTransactionsAndBalance() {
		UUID userId = UUID.randomUUID();

		WatchRewardResult reward = pointsService.awardWatchProgress(userId, "book-1", 1, 100, 120);

		assertThat(reward.awardedStages()).isEmpty();
		assertThat(reward.awardedPoints()).isEqualTo(2);
		assertThat(pointsService.account(userId).balance()).isEqualTo(2);
		assertThat(pointsService.records(userId))
				.hasSize(1)
				.allSatisfy(record -> assertThat(record.source()).isEqualTo("WATCH_REWARD"));
	}

	@Test
	void watchRewardsAreCappedByDailyEarnedMaximumAndOnlyCreditRemainingPoints() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");
		UUID userId = UUID.randomUUID();

		WatchRewardResult reward = pointsService.awardWatchProgress(userId, "book-daily-cap", 1, 100, 600);
		WatchRewardResult laterReward = pointsService.awardWatchProgress(userId, "book-daily-cap-2", 1, 100, 600);

		assertThat(reward.awardedStages()).isEmpty();
		assertThat(reward.awardedPoints()).isEqualTo(600);
		assertThat(laterReward.awardedStages()).isEmpty();
		assertThat(laterReward.awardedPoints()).isEqualTo(400);
		assertThat(laterReward.rewardStatus()).isEqualTo(WatchRewardStatus.AWARDED_PARTIAL);
		assertThat(pointsService.account(userId).balance()).isEqualTo(1000);
		assertThat(pointsService.records(userId))
				.extracting(PointTransactionResponse::amount)
				.containsExactlyInAnyOrder(600, 400);
	}

	@Test
	void rechargeAndAdminAdjustmentAreNotCappedByDailyEarnedMaximum() {
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1");
		UUID userId = UUID.randomUUID();

		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");
		pointsService.awardWatchProgress(userId, "book-auto-capped", 1, 100, 10);
		pointsService.creditRechargeOrder(userId, "RO-uncapped", 99);
		PointAccountResponse adjusted = pointsService.adjustByAdmin(userId, 50, "manual adjustment");

		assertThat(adjusted.balance()).isEqualTo(150);
		assertThat(pointsService.records(userId))
				.extracting(PointTransactionResponse::source)
				.contains("WATCH_REWARD", "RECHARGE_ORDER", "ADMIN_ADJUSTMENT");
	}

	@Test
	void dailyEarnedMaximumResetsOnNextServerDay() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");
		UUID userId = UUID.randomUUID();

		WatchRewardResult today = pointsService.awardWatchProgress(userId, "book-reset-today", 1, 100, 200);
		mutableClock.setInstant(Instant.parse("2026-07-14T00:01:00Z"));
		WatchRewardResult tomorrow = pointsService.awardWatchProgress(userId, "book-reset-tomorrow", 1, 100, 200);

		assertThat(today.awardedPoints()).isEqualTo(200);
		assertThat(tomorrow.awardedPoints()).isEqualTo(200);
		assertThat(pointsService.account(userId).balance()).isEqualTo(400);
	}

	@Test
	void duplicateWatchRewardDoesNotChangeBalance() {
		UUID userId = UUID.randomUUID();
		pointsService.awardWatchProgress(userId, "book-1", 1, 100, 60);

		WatchRewardResult duplicate = pointsService.awardWatchProgress(userId, "book-1", 1, 100, 60);

		assertThat(duplicate.awardedPoints()).isZero();
		assertThat(duplicate.awardedStages()).isEmpty();
		assertThat(pointsService.account(userId).balance()).isEqualTo(1);
		assertThat(pointsService.records(userId)).hasSize(1);
	}

	@Test
	void rechargeOrderCreditCreatesTransactionAndBalance() {
		UUID userId = UUID.randomUUID();

		PointAccountResponse account = pointsService.creditRechargeOrder(userId, "RO-test-1", 99);

		assertThat(account.balance()).isEqualTo(99);
		assertThat(pointsService.records(userId))
				.singleElement()
				.satisfies(record -> {
					assertThat(record.amount()).isEqualTo(99);
					assertThat(record.balanceAfter()).isEqualTo(99);
					assertThat(record.source()).isEqualTo("RECHARGE_ORDER");
					assertThat(record.reason()).isEqualTo("RO-test-1");
				});
	}

	@Test
	void concurrentSameStageRewardIsIdempotent() throws Exception {
		UUID userId = UUID.randomUUID();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<WatchRewardResult> task = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return pointsService.awardWatchProgress(userId, "book-race", 1, 100, 60);
		};

		Future<WatchRewardResult> first = executor.submit(task);
		Future<WatchRewardResult> second = executor.submit(task);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<WatchRewardResult> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
		executor.shutdownNow();

		assertThat(results).extracting(WatchRewardResult::awardedPoints).containsExactlyInAnyOrder(0, 1);
		assertThat(pointsService.account(userId).balance()).isEqualTo(1);
		assertThat(pointsService.records(userId)).hasSize(1);
	}

	@Test
	void concurrentDifferentUsersShareOneDailyRuleSnapshot() throws Exception {
		dailyEarningQuotaRepository.deleteAll();
		dailyEarningRuleRepository.deleteAll();
		UUID firstUserId = UUID.randomUUID();
		UUID secondUserId = UUID.randomUUID();
		ExecutorService executor = Executors.newFixedThreadPool(2);

		Future<WatchRewardResult> first = executor.submit(
				() -> pointsService.awardWatchProgress(firstUserId, "rule-race-1", 1, 100, 60));
		Future<WatchRewardResult> second = executor.submit(
				() -> pointsService.awardWatchProgress(secondUserId, "rule-race-2", 1, 100, 60));

		assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
				.extracting(WatchRewardResult::awardedPoints)
				.containsExactlyInAnyOrder(1, 1);
		assertThat(dailyEarningRuleRepository.count()).isEqualTo(1);
		executor.shutdownNow();
	}

	@Test
	void concurrentWatchReportsOnlyAwardSameStageOnce() throws Exception {
		UUID userId = UUID.randomUUID();
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-watch-race", 1, "chapter-1", 100));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<WatchRecordResponse> task = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return watchService.reportProgress(userId, request("book-watch-race", 1, 100, 100));
		};

		Future<WatchRecordResponse> first = executor.submit(task);
		Future<WatchRecordResponse> second = executor.submit(task);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<WatchRecordResponse> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
		executor.shutdownNow();

		assertThat(results).extracting(WatchRecordResponse::awardedPoints).containsExactlyInAnyOrder(0, 1);
		assertThat(pointsService.account(userId).balance()).isEqualTo(1);
		assertThat(pointsService.records(userId)).hasSize(1);
	}

	@Test
	void laterProgressOnlyCreatesMissingRewardClaims() {
		UUID userId = UUID.randomUUID();
		pointsService.awardWatchProgress(userId, "book-claimed", 1, 100, 60);

		WatchRewardResult duplicate = pointsService.awardWatchProgress(userId, "book-claimed", 1, 100, 60);

		assertThat(duplicate.awardedStages()).isEmpty();
		assertThat(duplicate.awardedPoints()).isEqualTo(0);
		assertThat(pointsService.account(userId).balance()).isEqualTo(1);
		assertThat(pointsService.records(userId)).hasSize(1);
	}

	@Test
	void pointAccountRejectsOverflowAndFrozenUnderflowAdjustments() {
		PointAccount account = PointAccount.create(UUID.randomUUID());
		account.add(Integer.MAX_VALUE);

		assertThatThrownBy(() -> account.add(1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("point balance overflow");

		PointAccount frozenAccount = PointAccount.create(UUID.randomUUID());
		frozenAccount.add(10);
		frozenAccount.freeze(8);
		assertThatThrownBy(() -> frozenAccount.add(-3))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("point balance below frozen points");
	}

	@Test
	void rechargeCreditRejectsOverflowBeforeWritingTransaction() {
		UUID userId = UUID.randomUUID();
		PointAccount account = PointAccount.create(userId);
		account.add(Integer.MAX_VALUE);
		pointAccountRepository.saveAndFlush(account);

		assertThatThrownBy(() -> pointsService.creditRechargeOrder(userId, "RO-overflow", 1))
				.isInstanceOf(AdminException.class)
				.hasMessage("point balance overflow");

		assertThat(pointsService.account(userId).balance()).isEqualTo(Integer.MAX_VALUE);
		assertThat(pointsService.records(userId)).isEmpty();
	}

	@Test
	void transferRollsBackWhenRecipientWouldOverflow() {
		UserAccount sender = userAccountRepository.save(
				UserAccount.createPhoneAccount("+1", "4155550601", "+14155550601", "hash"));
		UserAccount recipient = userAccountRepository.save(
				UserAccount.createPhoneAccount("+1", "4155550602", "+14155550602", "hash"));
		PointAccount senderAccount = PointAccount.create(sender.id());
		senderAccount.add(10);
		pointAccountRepository.save(senderAccount);
		PointAccount recipientAccount = PointAccount.create(recipient.id());
		recipientAccount.add(Integer.MAX_VALUE);
		pointAccountRepository.saveAndFlush(recipientAccount);

		assertThatThrownBy(() -> pointsService.transfer(sender.id(), "+14155550602", 5))
				.isInstanceOf(AdminException.class)
				.hasMessage("point balance overflow");

		assertThat(pointsService.account(sender.id()).balance()).isEqualTo(10);
		assertThat(pointsService.account(recipient.id()).balance()).isEqualTo(Integer.MAX_VALUE);
		assertThat(pointTransferRepository.findBySenderUserIdOrRecipientUserIdOrderByCreatedAtDesc(
				sender.id(), sender.id())).isEmpty();
	}

	@Test
	void watchRewardsAreScopedByUser() {
		UUID firstUserId = UUID.randomUUID();
		UUID secondUserId = UUID.randomUUID();

		pointsService.awardWatchProgress(firstUserId, "book-1", 1, 100, 60);
		pointsService.awardWatchProgress(secondUserId, "book-1", 1, 100, 60);

		assertThat(pointsService.account(firstUserId).balance()).isEqualTo(1);
		assertThat(pointsService.account(secondUserId).balance()).isEqualTo(1);
	}

	private WatchProgressRequest request(String bookId, int episodeNum, int positionSeconds, int durationSeconds) {
		return new WatchProgressRequest(bookId, "Title " + bookId, "filtered-" + bookId, episodeNum,
				"chapter-" + episodeNum, positionSeconds, durationSeconds);
	}

	@TestConfiguration
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-07-13T10:00:00Z"), ZoneId.of("UTC"));
		}
	}

	static class MutableClock extends Clock {

		private Instant instant;
		private final ZoneId zone;

		MutableClock(Instant instant, ZoneId zone) {
			this.instant = instant;
			this.zone = zone;
		}

		void setInstant(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
