package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * P2-16: 验证码清理任务只删除早于保留期的过期行。
 */
@SpringBootTest
class CaptchaCleanupServiceTests {

	@Autowired
	private CaptchaCleanupService captchaCleanupService;

	@Autowired
	private CaptchaChallengeRepository captchaChallengeRepository;

	@Autowired
	private TokenHasher tokenHasher;

	@Test
	void deletesOnlyChallengesExpiredBeyondRetention() {
		OffsetDateTime now = OffsetDateTime.parse("2026-10-12T12:00:00Z");
		CaptchaChallenge longExpired = save("ABCD", now.minusHours(3));
		CaptchaChallenge recentlyExpired = save("EFGH", now.minusMinutes(30));
		CaptchaChallenge active = save("JKLM", now.plusMinutes(4));

		int deleted = captchaCleanupService.cleanup(now);

		assertThat(deleted).isEqualTo(1);
		assertThat(captchaChallengeRepository.findById(longExpired.id())).isEmpty();
		assertThat(captchaChallengeRepository.findById(recentlyExpired.id())).isPresent();
		assertThat(captchaChallengeRepository.findById(active.id())).isPresent();
	}

	private CaptchaChallenge save(String answer, OffsetDateTime expiresAt) {
		return captchaChallengeRepository.save(CaptchaChallenge.create(tokenHasher.hash(answer),
				"data:image/png;base64,AA==", expiresAt));
	}
}
