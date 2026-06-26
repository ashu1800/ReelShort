package com.reelshort.backend.points;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
