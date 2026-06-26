package com.reelshort.backend.points;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.watch.WatchProgressRequest;
import com.reelshort.backend.watch.WatchRecordResponse;
import com.reelshort.backend.watch.WatchService;

@SpringBootTest
class PointsServiceTests {

	@Autowired
	private PointsService pointsService;

	@Autowired
	private WatchService watchService;

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
}
