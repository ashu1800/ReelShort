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
	private MutableClock mutableClock;

	@AfterEach
	void resetConfigs() {
		mutableClock.setInstant(Instant.parse("2026-07-13T10:00:00Z"));
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_STAGE_POINTS, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
	}

	@Test
	void accountIsCreatedWithZeroBalance() {
		PointAccountResponse account = pointsService.account(UUID.randomUUID());

		assertThat(account.balance()).isZero();
	}

	@Test
	void watchRewardCreatesTransactionsAndBalance() {
		UUID userId = UUID.randomUUID();

		WatchRewardResult reward = pointsService.awardWatchProgress(userId, "book-1", 1, 76);

		assertThat(reward.awardedStages()).containsExactly(25, 50, 75);
		assertThat(reward.awardedPoints()).isEqualTo(3);
		assertThat(pointsService.account(userId).balance()).isEqualTo(3);
		assertThat(pointsService.records(userId))
				.hasSize(3)
				.allSatisfy(record -> assertThat(record.source()).isEqualTo("WATCH_REWARD"));
	}

	@Test
	void watchRewardsAreCappedByDailyEarnedMaximumAndOnlyCreditRemainingPoints() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_STAGE_POINTS, "600");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		UUID userId = UUID.randomUUID();

		WatchRewardResult reward = pointsService.awardWatchProgress(userId, "book-daily-cap", 1, 100);
		WatchRewardResult laterReward = pointsService.awardWatchProgress(userId, "book-daily-cap-2", 1, 25);

		assertThat(reward.awardedStages()).containsExactly(25, 50, 75, 100);
		assertThat(reward.awardedPoints()).isEqualTo(1000);
		assertThat(laterReward.awardedStages()).containsExactly(25);
		assertThat(laterReward.awardedPoints()).isZero();
		assertThat(pointsService.account(userId).balance()).isEqualTo(1000);
		assertThat(pointsService.records(userId))
				.extracting(PointTransactionResponse::amount)
				.containsExactlyInAnyOrder(600, 400);
	}

	@Test
	void rechargeAndAdminAdjustmentAreNotCappedByDailyEarnedMaximum() {
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1");
		UUID userId = UUID.randomUUID();

		pointsService.awardWatchProgress(userId, "book-auto-capped", 1, 100);
		pointsService.creditRechargeOrder(userId, "RO-uncapped", 99);
		PointAccountResponse adjusted = pointsService.adjustByAdmin(userId, 50, "manual adjustment");

		assertThat(adjusted.balance()).isEqualTo(150);
		assertThat(pointsService.records(userId))
				.extracting(PointTransactionResponse::source)
				.contains("WATCH_REWARD", "RECHARGE_ORDER", "ADMIN_ADJUSTMENT");
	}

	@Test
	void dailyEarnedMaximumResetsOnNextServerDay() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_STAGE_POINTS, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		UUID userId = UUID.randomUUID();

		WatchRewardResult today = pointsService.awardWatchProgress(userId, "book-reset-today", 1, 25);
		mutableClock.setInstant(Instant.parse("2026-07-14T00:01:00Z"));
		WatchRewardResult tomorrow = pointsService.awardWatchProgress(userId, "book-reset-tomorrow", 1, 25);

		assertThat(today.awardedPoints()).isEqualTo(1000);
		assertThat(tomorrow.awardedPoints()).isEqualTo(1000);
		assertThat(pointsService.account(userId).balance()).isEqualTo(2000);
	}

	@Test
	void duplicateWatchRewardDoesNotChangeBalance() {
		UUID userId = UUID.randomUUID();
		pointsService.awardWatchProgress(userId, "book-1", 1, 25);

		WatchRewardResult duplicate = pointsService.awardWatchProgress(userId, "book-1", 1, 25);

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
			return pointsService.awardWatchProgress(userId, "book-race", 1, 25);
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
	void concurrentWatchReportsOnlyAwardSameStageOnce() throws Exception {
		UUID userId = UUID.randomUUID();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<WatchRecordResponse> task = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return watchService.reportProgress(userId, request("book-watch-race", 1, 25, 100));
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
		pointsService.awardWatchProgress(userId, "book-claimed", 1, 25);

		WatchRewardResult duplicate = pointsService.awardWatchProgress(userId, "book-claimed", 1, 50);

		assertThat(duplicate.awardedStages()).containsExactly(50);
		assertThat(duplicate.awardedPoints()).isEqualTo(1);
		assertThat(pointsService.account(userId).balance()).isEqualTo(2);
		assertThat(pointsService.records(userId)).hasSize(2);
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

		pointsService.awardWatchProgress(firstUserId, "book-1", 1, 25);
		pointsService.awardWatchProgress(secondUserId, "book-1", 1, 25);

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
