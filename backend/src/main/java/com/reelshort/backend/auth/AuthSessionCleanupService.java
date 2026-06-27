package com.reelshort.backend.auth;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionCleanupService {

	private final AccessTokenRepository accessTokenRepository;
	private final AuthSessionProperties authSessionProperties;

	public AuthSessionCleanupService(AccessTokenRepository accessTokenRepository,
			AuthSessionProperties authSessionProperties) {
		this.accessTokenRepository = accessTokenRepository;
		this.authSessionProperties = authSessionProperties;
	}

	@Scheduled(
			initialDelayString = "${reelshort.auth.session.cleanup-initial-delay:1h}",
			fixedDelayString = "${reelshort.auth.session.cleanup-interval:1h}")
	@Transactional
	public void cleanupExpiredAndRevokedTokens() {
		cleanup(OffsetDateTime.now());
	}

	@Transactional
	int cleanup(OffsetDateTime now) {
		OffsetDateTime cutoff = now.minus(authSessionProperties.getCleanupRetention());
		return accessTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
	}
}
