package com.reelshort.backend.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class PointsRepositoryTests {

	@Autowired
	private PointAccountRepository pointAccountRepository;

	@Autowired
	private WatchRewardClaimRepository watchRewardClaimRepository;

	@Autowired
	private PointTransactionRepository pointTransactionRepository;

	@Test
	void pointAccountIsUniquePerUser() {
		UUID userId = UUID.randomUUID();
		pointAccountRepository.saveAndFlush(PointAccount.create(userId));

		assertThatThrownBy(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void watchRewardClaimIsUniquePerUserEpisodeStage() {
		UUID userId = UUID.randomUUID();
		watchRewardClaimRepository.saveAndFlush(WatchRewardClaim.create(userId, "book-1", 1, 25));

		assertThatThrownBy(() -> watchRewardClaimRepository.saveAndFlush(
				WatchRewardClaim.create(userId, "book-1", 1, 25)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void watchRewardSumIgnoresAdminAdjustmentsAndWithdrawals() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		pointTransactionRepository.save(PointTransaction.watchReward(userId, 100, 100, "book-1", 1, now.minusHours(2)));
		pointTransactionRepository.save(PointTransaction.watchReward(userId, 50, 150, "book-2", 1, now.minusHours(1)));
		pointTransactionRepository.save(PointTransaction.adminAdjustment(userId, 999_999, 1_000_149, "test adjustment"));
		pointTransactionRepository.save(PointTransaction.withdrawal(userId, 30, 1_000_119, "withdrawal-1"));

		long total = pointTransactionRepository.sumWatchRewardAmountByCreatedAtBetween(now.minusDays(1),
				now.plusDays(1));

		assertThat(total).isEqualTo(150L);
	}
}
