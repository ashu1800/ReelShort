package com.reelshort.backend.auth;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P2-16: 验证码清理任务。匿名 GET /api/app/auth/captcha 会持续写库，若不清理则无界增长。
 * 只删除早于保留期（默认 1 小时，远大于 5 分钟有效期）的过期行，避免误删刚生成/刚过期的行。
 */
@Service
public class CaptchaCleanupService {

	private static final Duration CLEANUP_RETENTION = Duration.ofHours(1);

	private final CaptchaChallengeRepository captchaChallengeRepository;

	public CaptchaCleanupService(CaptchaChallengeRepository captchaChallengeRepository) {
		this.captchaChallengeRepository = captchaChallengeRepository;
	}

	@Scheduled(fixedDelayString = "${reelshort.auth.captcha.cleanup-interval:1h}")
	@Transactional
	public void cleanupExpiredChallenges() {
		cleanup(OffsetDateTime.now());
	}

	@Transactional
	int cleanup(OffsetDateTime now) {
		return captchaChallengeRepository.deleteExpiredBefore(now.minus(CLEANUP_RETENTION));
	}
}
