package com.reelshort.backend.watch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WatchServiceTests {

	@Autowired
	private WatchService watchService;

	@Autowired
	private WatchRecordRepository watchRecordRepository;

	@Test
	void reportProgressClampsPositionAndCalculatesPercent() {
		UUID userId = UUID.randomUUID();

		WatchRecordResponse response = watchService.reportProgress(userId,
				request("book-clamp", 1, 150, 120));

		assertThat(response.positionSeconds()).isEqualTo(120);
		assertThat(response.progressPercent()).isEqualTo(100);
	}

	@Test
	void duplicateReportUpdatesExistingRecord() {
		UUID userId = UUID.randomUUID();
		watchService.reportProgress(userId, request("book-upsert", 1, 10, 100));

		WatchRecordResponse response = watchService.reportProgress(userId, request("book-upsert", 1, 40, 100));

		assertThat(response.positionSeconds()).isEqualTo(40);
		assertThat(watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId)).hasSize(1);
	}

	@Test
	void historyIsScopedByUser() {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		watchService.reportProgress(userId, request("book-user", 1, 20, 100));
		watchService.reportProgress(otherUserId, request("book-user", 1, 90, 100));

		List<WatchRecordResponse> history = watchService.history(userId);

		assertThat(history).hasSize(1);
		assertThat(history.get(0).positionSeconds()).isEqualTo(20);
	}

	private WatchProgressRequest request(String bookId, int episodeNum, int positionSeconds, int durationSeconds) {
		return new WatchProgressRequest(bookId, "Title " + bookId, "filtered-" + bookId, episodeNum,
				"chapter-" + episodeNum, positionSeconds, durationSeconds);
	}
}
